package workflow.core.engine.domain.workflow;

/** Workflow instance state Represents the lifecycle of a workflow execution */
public enum WorkflowState {
  PENDING, // Created but not yet started
  RUNNING, // Currently executing
  PAUSED, // Paused (e.g., waiting for user task or external event)
  COMPLETED, // Successfully completed
  FAILED, // Failed with error
  CANCELLED // Cancelled by user/system
}
