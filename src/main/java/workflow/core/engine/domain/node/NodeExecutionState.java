package workflow.core.engine.domain.node;

/**
 * Node execution state
 */
public enum NodeExecutionState {
    PENDING,        // Not yet started
    RUNNING,        // Currently executing
    COMPLETED,      // Successfully completed
    FAILED,         // Failed with error
    SKIPPED         // Skipped (conditional path not taken)
}

