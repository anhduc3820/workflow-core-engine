package workflow.core.engine.application.transaction;

import lombok.Builder;
import lombok.Data;
import org.springframework.transaction.TransactionDefinition;

import java.util.HashMap;
import java.util.Map;

/**
 * Transaction Context
 * Encapsulates all context needed for financial-grade transaction execution
 */
@Data
@Builder
public class TransactionContext {

    private String executionId;
    private String nodeId;
    private String nodeType;
    private String tenantId;

    // Transaction configuration
    @Builder.Default
    private Integer isolationLevel = TransactionDefinition.ISOLATION_SERIALIZABLE;

    @Builder.Default
    private int timeoutSeconds = 30;

    @Builder.Default
    private boolean nullResultForbidden = true;

    @Builder.Default
    private boolean idempotencyRequired = true;

    // Idempotency key for this transaction
    private String idempotencyKey;

    // Pre-commit validation hook
    private FinancialTransactionManager.PreCommitValidator preCommitValidator;

    // Input data for the transaction
    @Builder.Default
    private Map<String, Object> inputData = new HashMap<>();

    // Additional metadata
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    /**
     * Generate idempotency key for this context
     */
    public String generateIdempotencyKey() {
        if (idempotencyKey != null) {
            return idempotencyKey;
        }
        idempotencyKey = String.format("idem-%s-%s-%d", executionId, nodeId, System.nanoTime());
        return idempotencyKey;
    }

    /**
     * Add input data
     */
    public TransactionContext withInput(String key, Object value) {
        inputData.put(key, value);
        return this;
    }

    /**
     * Get input data
     */
    @SuppressWarnings("unchecked")
    public <T> T getInput(String key) {
        return (T) inputData.get(key);
    }

    /**
     * Add metadata
     */
    public TransactionContext withMetadata(String key, Object value) {
        metadata.put(key, value);
        return this;
    }
}

