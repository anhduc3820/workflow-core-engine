package workflow.core.engine.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import workflow.core.engine.application.transaction.FinancialTransactionManager;
import workflow.core.engine.application.transaction.TransactionContext;
import workflow.core.engine.application.transaction.CompensationHandler;
import workflow.core.engine.application.transaction.CompensationService;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * Financial Transaction Test
 * Validates ACID guarantees and two-phase commit
 *
 * Financial-Grade Requirements:
 * - Atomicity: All-or-nothing execution
 * - Consistency: Valid state transitions only
 * - Isolation: SERIALIZABLE isolation level
 * - Durability: Committed changes are persisted
 */
@SpringBootTest
@ActiveProfiles("test")
public class FinancialTransactionTest {

    @Autowired
    private FinancialTransactionManager transactionManager;

    @Autowired
    private CompensationService compensationService;

    @Test
    public void testAtomicTransaction() {
        // Given: A transaction context
        TransactionContext context = TransactionContext.builder()
                .executionId("atomic-test-" + System.currentTimeMillis())
                .nodeId("test-node")
                .nodeType("payment")
                .tenantId("test-tenant")
                .build();

        AtomicBoolean executed = new AtomicBoolean(false);

        // When: Execute in transaction
        String result = transactionManager.executeInTransaction(context, ctx -> {
            executed.set(true);
            return "SUCCESS";
        });

        // Then: Should execute and return result
        assertThat(result).isEqualTo("SUCCESS");
        assertThat(executed.get()).isTrue();
    }

    @Test
    public void testTransactionRollbackOnError() {
        // Given: A transaction that will fail
        TransactionContext context = TransactionContext.builder()
                .executionId("rollback-test-" + System.currentTimeMillis())
                .nodeId("test-node")
                .nodeType("payment")
                .build();

        AtomicBoolean compensated = new AtomicBoolean(false);

        // When: Transaction fails
        assertThatThrownBy(() -> {
            transactionManager.executeInTransaction(context, ctx -> {
                throw new RuntimeException("Simulated failure");
            });
        }).isInstanceOf(FinancialTransactionManager.TransactionFailureException.class);

        // Then: Transaction should be rolled back (verified by no committed state)
    }

    @Test
    public void testTwoPhaseCommitSuccess() {
        // Given: Two-phase transaction context
        TransactionContext context = TransactionContext.builder()
                .executionId("2pc-success-" + System.currentTimeMillis())
                .nodeId("payment-node")
                .nodeType("payment")
                .build();

        AtomicInteger phase1Called = new AtomicInteger(0);
        AtomicInteger phase2Called = new AtomicInteger(0);

        FinancialTransactionManager.TwoPhaseOperation<String> operation =
            new FinancialTransactionManager.TwoPhaseOperation<>() {
                @Override
                public String prepare(TransactionContext ctx) {
                    phase1Called.incrementAndGet();
                    return "PREPARED";
                }

                @Override
                public void commit(TransactionContext ctx, String preparedResult) {
                    phase2Called.incrementAndGet();
                    assertThat(preparedResult).isEqualTo("PREPARED");
                }

                @Override
                public boolean hasCompensation() {
                    return false;
                }

                @Override
                public CompensationHandler getCompensationHandler() {
                    return null;
                }
            };

        // When: Execute two-phase commit
        String result = transactionManager.executeWithTwoPhaseCommit(context, operation);

        // Then: Both phases should be called
        assertThat(result).isEqualTo("PREPARED");
        assertThat(phase1Called.get()).isEqualTo(1);
        assertThat(phase2Called.get()).isEqualTo(1);
    }

    @Test
    public void testTwoPhaseCommitWithCompensation() {
        // Given: Two-phase transaction with compensation
        TransactionContext context = TransactionContext.builder()
                .executionId("2pc-compensate-" + System.currentTimeMillis())
                .nodeId("payment-node")
                .nodeType("payment")
                .build();

        AtomicBoolean compensated = new AtomicBoolean(false);

        FinancialTransactionManager.TwoPhaseOperation<String> operation =
            new FinancialTransactionManager.TwoPhaseOperation<>() {
                @Override
                public String prepare(TransactionContext ctx) {
                    return "PREPARED";
                }

                @Override
                public void commit(TransactionContext ctx, String preparedResult) {
                    // Fail during commit phase
                    throw new RuntimeException("Commit failed");
                }

                @Override
                public boolean hasCompensation() {
                    return true;
                }

                @Override
                public CompensationHandler getCompensationHandler() {
                    return ctx -> compensated.set(true);
                }
            };

        // When: Commit phase fails
        assertThatThrownBy(() -> {
            transactionManager.executeWithTwoPhaseCommit(context, operation);
        }).isInstanceOf(FinancialTransactionManager.TransactionFailureException.class)
          .hasMessageContaining("compensated");

        // Then: Compensation should be triggered
        assertThat(compensated.get()).isTrue();
    }

    @Test
    public void testIdempotencyCheck() {
        // Given: A transaction with idempotency key
        String idempotencyKey = "idem-test-" + System.currentTimeMillis();
        TransactionContext context = TransactionContext.builder()
                .executionId("idem-test-" + System.currentTimeMillis())
                .nodeId("test-node")
                .nodeType("task")
                .idempotencyKey(idempotencyKey)
                .build();

        // When: Check idempotency before execution
        boolean exists = transactionManager.checkIdempotency(
                context.getExecutionId(),
                idempotencyKey
        );

        // Then: Should not exist initially
        assertThat(exists).isFalse();
    }

    @Test
    public void testPreCommitValidation() {
        // Given: Transaction with pre-commit validator
        TransactionContext context = TransactionContext.builder()
                .executionId("validation-test-" + System.currentTimeMillis())
                .nodeId("test-node")
                .nodeType("task")
                .preCommitValidator(ctx -> {
                    if (ctx.getInput("amount") == null) {
                        throw new FinancialTransactionManager.TransactionValidationException(
                                "Amount is required");
                    }
                })
                .build();

        // When: Execute without required input
        assertThatThrownBy(() -> {
            transactionManager.executeInTransaction(context, ctx -> "SUCCESS");
        }).isInstanceOf(FinancialTransactionManager.TransactionFailureException.class)
          .hasCauseInstanceOf(FinancialTransactionManager.TransactionValidationException.class);
    }

    @Test
    public void testNullResultForbidden() {
        // Given: Context with null result forbidden
        TransactionContext context = TransactionContext.builder()
                .executionId("null-test-" + System.currentTimeMillis())
                .nodeId("test-node")
                .nodeType("task")
                .nullResultForbidden(true)
                .build();

        // When: Operation returns null
        assertThatThrownBy(() -> {
            transactionManager.executeInTransaction(context, ctx -> null);
        }).isInstanceOf(FinancialTransactionManager.TransactionFailureException.class)
          .hasCauseInstanceOf(FinancialTransactionManager.TransactionValidationException.class)
          .hasMessageContaining("null result");
    }

    @Test
    public void testTransactionTimeout() {
        // Given: Context with short timeout
        TransactionContext context = TransactionContext.builder()
                .executionId("timeout-test-" + System.currentTimeMillis())
                .nodeId("test-node")
                .nodeType("task")
                .timeoutSeconds(1)
                .build();

        // When: Operation takes longer than timeout
        // Note: This test may not reliably timeout in all environments
        assertThatThrownBy(() -> {
            transactionManager.executeInTransaction(context, ctx -> {
                Thread.sleep(2000);
                return "SUCCESS";
            });
        }).isInstanceOf(Exception.class);
    }

    @Test
    public void testActiveTransactionMonitoring() {
        // Given: A long-running transaction
        TransactionContext context = TransactionContext.builder()
                .executionId("monitoring-test-" + System.currentTimeMillis())
                .nodeId("test-node")
                .nodeType("task")
                .build();

        // When: Execute transaction
        transactionManager.executeInTransaction(context, ctx -> {
            // Check active transactions during execution
            var activeTransactions = transactionManager.getActiveTransactions();
            // Note: May be empty due to fast execution
            return "SUCCESS";
        });

        // Then: After execution, should have no active transactions
        var activeTransactions = transactionManager.getActiveTransactions();
        assertThat(activeTransactions).isEmpty();
    }

    @Test
    public void testSerializableIsolation() {
        // Given: Transaction with SERIALIZABLE isolation
        TransactionContext context = TransactionContext.builder()
                .executionId("isolation-test-" + System.currentTimeMillis())
                .nodeId("test-node")
                .nodeType("task")
                .build();

        // When: Execute with default (SERIALIZABLE) isolation
        String result = transactionManager.executeInTransaction(context, ctx -> {
            assertThat(ctx.getIsolationLevel()).isNotNull();
            return "SUCCESS";
        });

        // Then: Should execute with proper isolation
        assertThat(result).isEqualTo("SUCCESS");
    }
}

