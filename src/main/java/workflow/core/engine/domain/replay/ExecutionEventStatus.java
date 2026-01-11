package workflow.core.engine.domain.replay;

/**
 * Execution Event Status
 * Represents the status of an execution event
 */
public enum ExecutionEventStatus {
    PENDING,
    IN_PROGRESS,
    COMPLETED,
    FAILED,
    COMPENSATED,
    SKIPPED
}

