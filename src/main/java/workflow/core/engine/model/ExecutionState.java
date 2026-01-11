package workflow.core.engine.model;

/** Workflow execution state */
public enum ExecutionState {
  IDLE, // Not started
  RUNNING, // Currently executing
  PAUSED, // Paused (e.g., waiting for user task)
  COMPLETED, // Successfully completed
  FAILED, // Failed with error
  CANCELLED // Cancelled by user
}
