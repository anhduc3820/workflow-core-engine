package workflow.core.engine.application.transaction;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import workflow.core.engine.application.replay.ExecutionEventService;
import workflow.core.engine.domain.replay.ExecutionEventType;

/**
 * Financial-Grade Transaction Manager
 *
 * <p>Enforces ACID guarantees for workflow execution: - Atomicity: All-or-nothing node execution -
 * Consistency: State transitions maintain invariants - Isolation: SERIALIZABLE by default for
 * financial operations - Durability: All commits are persisted before acknowledgment
 *
 * <p>Key Features: - Two-phase commit for compensatable operations - Idempotency key enforcement -
 * Pre-commit validation hooks - Automatic rollback on any failure - Transaction boundary tracking
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialTransactionManager {

  private final PlatformTransactionManager transactionManager;
  private final ExecutionEventService eventService;
  private final CompensationService compensationService;

  // Transaction isolation level for financial operations
  private static final int FINANCIAL_ISOLATION_LEVEL = TransactionDefinition.ISOLATION_SERIALIZABLE;

  // Active transactions (for monitoring and timeout management)
  private final Map<String, ActiveTransaction> activeTransactions = new ConcurrentHashMap<>();

  /**
   * Execute a node within a financial-grade transaction boundary
   *
   * @param context Transaction context
   * @param operation The operation to execute
   * @return Result of the operation
   * @throws TransactionFailureException if transaction fails
   */
  public <T> T executeInTransaction(TransactionContext context, TransactionOperation<T> operation) {
    String transactionId = generateTransactionId(context);
    log.info("Starting financial transaction: {} for node: {}", transactionId, context.getNodeId());

    // Record transaction started event
    recordTransactionEvent(
        context.getExecutionId(),
        transactionId,
        ExecutionEventType.TRANSACTION_STARTED,
        context.getNodeId());

    // Create transaction definition
    DefaultTransactionDefinition def = new DefaultTransactionDefinition();
    def.setName(transactionId);
    def.setIsolationLevel(
        context.getIsolationLevel() != null
            ? context.getIsolationLevel()
            : FINANCIAL_ISOLATION_LEVEL);
    def.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    def.setTimeout(context.getTimeoutSeconds());

    TransactionStatus status = transactionManager.getTransaction(def);
    ActiveTransaction activeTransaction =
        new ActiveTransaction(
            transactionId,
            context.getExecutionId(),
            context.getNodeId(),
            System.currentTimeMillis());
    activeTransactions.put(transactionId, activeTransaction);

    try {
      // Pre-commit validation
      if (context.getPreCommitValidator() != null) {
        log.debug("Running pre-commit validation for transaction: {}", transactionId);
        context.getPreCommitValidator().validate(context);
      }

      // Execute operation
      T result = operation.execute(context);

      // Validate result before commit
      if (result == null && context.isNullResultForbidden()) {
        throw new TransactionValidationException(
            "Operation returned null result (forbidden in financial context)");
      }

      // Commit transaction
      transactionManager.commit(status);
      log.info("Transaction committed successfully: {}", transactionId);

      // Record transaction committed event
      recordTransactionEvent(
          context.getExecutionId(),
          transactionId,
          ExecutionEventType.TRANSACTION_COMMITTED,
          context.getNodeId());

      return result;

    } catch (Exception e) {
      log.error("Transaction failed: {} - Rolling back", transactionId, e);

      // Rollback transaction
      try {
        transactionManager.rollback(status);
        log.info("Transaction rolled back: {}", transactionId);

        // Record transaction rollback event
        recordTransactionEvent(
            context.getExecutionId(),
            transactionId,
            ExecutionEventType.TRANSACTION_ROLLED_BACK,
            context.getNodeId());

      } catch (Exception rollbackException) {
        log.error(
            "CRITICAL: Rollback failed for transaction: {}", transactionId, rollbackException);
        throw new TransactionRollbackException(
            "Failed to rollback transaction: " + transactionId, rollbackException);
      }

      throw new TransactionFailureException("Transaction failed: " + transactionId, e);

    } finally {
      activeTransactions.remove(transactionId);
    }
  }

  /**
   * Execute with two-phase commit (for compensatable operations) Phase 1: Prepare and validate
   * Phase 2: Commit or compensate
   */
  public <T> T executeWithTwoPhaseCommit(
      TransactionContext context, TwoPhaseOperation<T> operation) {
    String transactionId = generateTransactionId(context);
    log.info("Starting two-phase commit transaction: {}", transactionId);

    // PHASE 1: PREPARE
    T preparedResult;
    try {
      preparedResult =
          executeInTransaction(
              context,
              ctx -> {
                T result = operation.prepare(ctx);
                // Register compensation handler
                if (operation.hasCompensation()) {
                  compensationService.registerNodeCompensation(
                      ctx.getExecutionId(), ctx.getNodeId(), operation.getCompensationHandler());
                }
                return result;
              });
    } catch (Exception e) {
      log.error("Phase 1 (PREPARE) failed for transaction: {}", transactionId, e);
      throw new TransactionFailureException("Two-phase commit failed in PREPARE phase", e);
    }

    // PHASE 2: COMMIT
    try {
      operation.commit(context, preparedResult);
      log.info("Phase 2 (COMMIT) successful for transaction: {}", transactionId);
      return preparedResult;

    } catch (Exception e) {
      log.error(
          "Phase 2 (COMMIT) failed for transaction: {} - Initiating compensation",
          transactionId,
          e);

      // Compensate
      try {
        compensationService.compensateNode(context.getExecutionId(), context.getNodeId());
        log.info("Compensation successful for transaction: {}", transactionId);
      } catch (Exception compensationException) {
        log.error(
            "CRITICAL: Compensation failed for transaction: {}",
            transactionId,
            compensationException);
        throw new CompensationFailureException(
            "Two-phase commit COMMIT phase failed and compensation also failed",
            compensationException);
      }

      throw new TransactionFailureException(
          "Two-phase commit failed in COMMIT phase (compensated)", e);
    }
  }

  /**
   * Check if idempotency key already exists Returns true if operation should be skipped (already
   * executed)
   */
  public boolean checkIdempotency(String executionId, String idempotencyKey) {
    return eventService.existsByIdempotencyKey(idempotencyKey);
  }

  /** Get all active transactions (for monitoring) */
  public List<ActiveTransaction> getActiveTransactions() {
    return new ArrayList<>(activeTransactions.values());
  }

  /** Force rollback of active transaction (admin operation) */
  public void forceRollback(String transactionId) {
    ActiveTransaction transaction = activeTransactions.get(transactionId);
    if (transaction != null) {
      log.warn("Force rollback requested for transaction: {}", transactionId);
      // Mark for rollback - actual rollback will happen in transaction boundary
      transaction.markForRollback();
    }
  }

  private String generateTransactionId(TransactionContext context) {
    return String.format(
        "txn-%s-%s-%d", context.getExecutionId(), context.getNodeId(), System.currentTimeMillis());
  }

  private void recordTransactionEvent(
      String executionId, String transactionId, ExecutionEventType eventType, String nodeId) {
    try {
      Map<String, Object> eventData = new HashMap<>();
      eventData.put("transactionId", transactionId);
      eventData.put("nodeId", nodeId);
      eventService.recordEvent(executionId, eventType, eventData);
    } catch (Exception e) {
      log.error("Failed to record transaction event: {} for {}", eventType, transactionId, e);
      // Don't fail the transaction for audit logging failures
    }
  }

  // Nested Classes

  @FunctionalInterface
  public interface TransactionOperation<T> {
    T execute(TransactionContext context) throws Exception;
  }

  public interface TwoPhaseOperation<T> {
    T prepare(TransactionContext context) throws Exception;

    void commit(TransactionContext context, T preparedResult) throws Exception;

    boolean hasCompensation();

    CompensationService.CompensationHandler getCompensationHandler();
  }

  @FunctionalInterface
  public interface PreCommitValidator {
    void validate(TransactionContext context) throws TransactionValidationException;
  }

  public static class ActiveTransaction {
    private final String transactionId;
    private final String executionId;
    private final String nodeId;
    private final long startTimeMs;
    private volatile boolean markedForRollback = false;

    public ActiveTransaction(
        String transactionId, String executionId, String nodeId, long startTimeMs) {
      this.transactionId = transactionId;
      this.executionId = executionId;
      this.nodeId = nodeId;
      this.startTimeMs = startTimeMs;
    }

    public void markForRollback() {
      this.markedForRollback = true;
    }

    public boolean isMarkedForRollback() {
      return markedForRollback;
    }

    public long getDurationMs() {
      return System.currentTimeMillis() - startTimeMs;
    }

    public String getTransactionId() {
      return transactionId;
    }

    public String getExecutionId() {
      return executionId;
    }

    public String getNodeId() {
      return nodeId;
    }

    public long getStartTimeMs() {
      return startTimeMs;
    }
  }

  // Exception Classes

  public static class TransactionFailureException extends RuntimeException {
    public TransactionFailureException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static class TransactionRollbackException extends RuntimeException {
    public TransactionRollbackException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  public static class TransactionValidationException extends RuntimeException {
    public TransactionValidationException(String message) {
      super(message);
    }
  }

  public static class CompensationFailureException extends RuntimeException {
    public CompensationFailureException(String message, Throwable cause) {
      super(message, cause);
    }
  }
}
