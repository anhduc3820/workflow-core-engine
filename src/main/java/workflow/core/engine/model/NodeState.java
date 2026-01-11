package workflow.core.engine.model;

/**
 * Individual node execution state
 */
public enum NodeState {
    PENDING,        // Not yet executed
    RUNNING,        // Currently executing
    COMPLETED,      // Successfully completed
    FAILED,         // Failed with error
    SKIPPED         // Skipped (e.g., not taken branch)
}

