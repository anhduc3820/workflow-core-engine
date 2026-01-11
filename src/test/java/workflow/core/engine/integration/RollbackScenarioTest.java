package workflow.core.engine.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import workflow.core.engine.application.replay.ExecutionEventService;
import workflow.core.engine.application.transaction.CompensationService;
import workflow.core.engine.application.transaction.RollbackCoordinator;
import workflow.core.engine.domain.replay.ExecutionEventType;
import workflow.core.engine.domain.workflow.WorkflowInstanceEntity;
import workflow.core.engine.domain.workflow.WorkflowInstanceRepository;
import workflow.core.engine.domain.workflow.WorkflowState;

/**
 * Rollback Scenario Test Validates all rollback levels: node, step, and workflow
 *
 * <p>Financial-Grade Requirements: - Rollback must restore consistent state - Compensation handlers
 * must be invoked - Audit trail must be maintained
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class RollbackScenarioTest {

  @Autowired private RollbackCoordinator rollbackCoordinator;

  @Autowired private CompensationService compensationService;

  @Autowired private ExecutionEventService eventService;

  @Autowired private WorkflowInstanceRepository workflowInstanceRepository;

  @Test
  public void testNodeLevelRollback() {
    // Given: Workflow with completed node
    String executionId = "rollback-node-" + System.currentTimeMillis();

    // Setup workflow with "payment" node type
    WorkflowInstanceEntity workflow =
        new WorkflowInstanceEntity(executionId, "test-workflow", "1.0", "default");
    workflow.start();
    workflowInstanceRepository.save(workflow);

    Map<String, Object> eventData = new HashMap<>();
    eventService.recordEvent(executionId, ExecutionEventType.WORKFLOW_STARTED, eventData);

    eventData.put("nodeId", "payment-node");
    eventData.put("nodeType", "payment"); // Match with handler registration below
    eventService.recordEvent(executionId, ExecutionEventType.NODE_STARTED, eventData);
    eventData.put("outputSnapshot", "{\"paymentId\": \"12345\"}");
    eventService.recordEvent(executionId, ExecutionEventType.NODE_COMPLETED, eventData);

    // Register compensation handler
    compensationService.registerHandler(
        "payment",
        context -> {
          // Simulate payment reversal
          System.out.println("Reversing payment for: " + context.getNodeId());
        });

    // When: Rollback single node
    RollbackCoordinator.RollbackResult result =
        rollbackCoordinator.rollbackNode(
            executionId,
            "payment-node",
            RollbackCoordinator.RollbackReason.userRequested("Testing rollback"));

    // Then: Rollback should succeed
    assertThat(result.isSuccess()).isTrue();
    assertThat(result.getMessage()).contains("rolled back successfully");

    // And: Compensation event should be recorded
    var events = eventService.getExecutionTimeline(executionId);
    assertThat(events).anyMatch(e -> e.getEventType() == ExecutionEventType.COMPENSATION_INITIATED);
    assertThat(events).anyMatch(e -> e.getEventType() == ExecutionEventType.COMPENSATION_COMPLETED);
  }

  @Test
  public void testStepLevelRollback() {
    // Given: Workflow with multiple completed nodes
    String executionId = "rollback-step-" + System.currentTimeMillis();

    // Setup workflow but create checkpoint BETWEEN nodes
    WorkflowInstanceEntity workflow =
        new WorkflowInstanceEntity(executionId, "test-workflow", "1.0", "default");
    workflow.start();
    workflowInstanceRepository.save(workflow);

    Map<String, Object> eventData = new HashMap<>();
    eventService.recordEvent(executionId, ExecutionEventType.WORKFLOW_STARTED, eventData);

    // Node 1 - complete
    eventData.put("nodeId", "node1");
    eventData.put("nodeType", "task");
    eventService.recordEvent(executionId, ExecutionEventType.NODE_STARTED, eventData);
    eventService.recordEvent(executionId, ExecutionEventType.NODE_COMPLETED, eventData);

    // Create checkpoint AFTER node1
    Long checkpoint = rollbackCoordinator.createCheckpoint(executionId, "after-node1");

    // Node 2 - complete AFTER checkpoint
    eventData.put("nodeId", "node2");
    eventService.recordEvent(executionId, ExecutionEventType.NODE_STARTED, eventData);
    eventService.recordEvent(executionId, ExecutionEventType.NODE_COMPLETED, eventData);

    // Node 3 - complete AFTER checkpoint
    eventData.put("nodeId", "node3");
    eventService.recordEvent(executionId, ExecutionEventType.NODE_STARTED, eventData);
    eventService.recordEvent(executionId, ExecutionEventType.NODE_COMPLETED, eventData);

    // Register handlers
    compensationService.registerHandler(
        "task",
        context -> {
          System.out.println("Compensating task: " + context.getNodeId());
        });

    // When: Rollback to checkpoint
    RollbackCoordinator.RollbackResult result =
        rollbackCoordinator.rollbackToCheckpoint(
            executionId,
            checkpoint,
            RollbackCoordinator.RollbackReason.executionFailed("Downstream validation failed"));

    // Then: Should rollback nodes after checkpoint (node2 and node3)
    assertThat(result.isSuccess()).isTrue();
    Map<String, Object> details = result.getDetails();
    assertThat(details).containsKey("rolledBackNodes");
    @SuppressWarnings("unchecked")
    List<String> rolledBack = (List<String>) details.get("rolledBackNodes");
    assertThat(rolledBack).containsExactlyInAnyOrder("node2", "node3");
  }

  @Test
  public void testWorkflowLevelRollback() {
    // Given: Complete workflow execution
    String executionId = "rollback-workflow-" + System.currentTimeMillis();
    WorkflowInstanceEntity workflow = setupCompleteWorkflow(executionId);

    // Register handlers
    compensationService.registerHandler(
        "task",
        context -> {
          System.out.println("Compensating task: " + context.getNodeId());
        });

    // When: Rollback entire workflow
    RollbackCoordinator.RollbackResult result =
        rollbackCoordinator.rollbackWorkflow(
            executionId,
            RollbackCoordinator.RollbackReason.userRequested("Full rollback requested"));

    // Then: All nodes should be compensated
    assertThat(result.isSuccess()).isTrue();
    Map<String, Object> details = result.getDetails();
    assertThat(details.get("workflowState")).isEqualTo("CANCELLED");

    // And: Workflow instance should be cancelled
    WorkflowInstanceEntity updated = workflowInstanceRepository.findById(executionId).orElseThrow();
    assertThat(updated.getState()).isEqualTo(WorkflowState.CANCELLED);
  }

  @Test
  public void testRollbackWithoutCompensationHandler() {
    // Given: Node without compensation handler
    String executionId = "rollback-no-handler-" + System.currentTimeMillis();
    setupWorkflowWithCompletedNode(executionId);

    // When: Attempt rollback without handler
    RollbackCoordinator.RollbackResult result =
        rollbackCoordinator.rollbackNode(
            executionId,
            "payment-node",
            RollbackCoordinator.RollbackReason.executionFailed("Test"));

    // Then: Should fail gracefully
    assertThat(result.isSuccess()).isFalse();
    assertThat(result.getMessage()).contains("No compensation handler");

    // But: Events should still be recorded
    var events = eventService.getExecutionTimeline(executionId);
    assertThat(events).anyMatch(e -> e.getEventType() == ExecutionEventType.COMPENSATION_INITIATED);
  }

  @Test
  public void testCheckpointManagement() {
    // Given: Workflow with checkpoints
    String executionId = "checkpoint-mgmt-" + System.currentTimeMillis();
    setupWorkflowWithMultipleNodes(executionId);

    // When: Create multiple checkpoints
    Long cp1 = rollbackCoordinator.createCheckpoint(executionId, "checkpoint1");
    Long cp2 = rollbackCoordinator.createCheckpoint(executionId, "checkpoint2");

    // Then: Should be able to retrieve checkpoints
    var checkpoints = rollbackCoordinator.getCheckpoints(executionId);
    assertThat(checkpoints).hasSize(2);
    assertThat(checkpoints.get(0).sequenceNumber()).isEqualTo(cp1);
    assertThat(checkpoints.get(1).sequenceNumber()).isEqualTo(cp2);
  }

  // Helper methods

  private void setupWorkflowWithCompletedNode(String executionId) {
    WorkflowInstanceEntity workflow =
        new WorkflowInstanceEntity(executionId, "test-workflow", "1.0", "default");
    workflow.start();
    workflowInstanceRepository.save(workflow);

    Map<String, Object> eventData = new HashMap<>();
    eventService.recordEvent(executionId, ExecutionEventType.WORKFLOW_STARTED, eventData);

    eventData.put("nodeId", "payment-node");
    eventData.put("nodeType", "no-handler-node-type"); // Use unique type with no handler
    eventService.recordEvent(executionId, ExecutionEventType.NODE_STARTED, eventData);
    eventData.put("outputSnapshot", "{\"paymentId\": \"12345\"}");
    eventService.recordEvent(executionId, ExecutionEventType.NODE_COMPLETED, eventData);
  }

  private void setupWorkflowWithMultipleNodes(String executionId) {
    WorkflowInstanceEntity workflow =
        new WorkflowInstanceEntity(executionId, "test-workflow", "1.0", "default");
    workflow.start();
    workflowInstanceRepository.save(workflow);

    Map<String, Object> eventData = new HashMap<>();
    eventService.recordEvent(executionId, ExecutionEventType.WORKFLOW_STARTED, eventData);

    // Node 1
    eventData.put("nodeId", "node1");
    eventData.put("nodeType", "task");
    eventService.recordEvent(executionId, ExecutionEventType.NODE_STARTED, eventData);
    eventService.recordEvent(executionId, ExecutionEventType.NODE_COMPLETED, eventData);

    // Node 2
    eventData.put("nodeId", "node2");
    eventService.recordEvent(executionId, ExecutionEventType.NODE_STARTED, eventData);
    eventService.recordEvent(executionId, ExecutionEventType.NODE_COMPLETED, eventData);

    // Node 3
    eventData.put("nodeId", "node3");
    eventService.recordEvent(executionId, ExecutionEventType.NODE_STARTED, eventData);
    eventService.recordEvent(executionId, ExecutionEventType.NODE_COMPLETED, eventData);
  }

  private WorkflowInstanceEntity setupCompleteWorkflow(String executionId) {
    WorkflowInstanceEntity workflow =
        new WorkflowInstanceEntity(executionId, "test-workflow", "1.0", "default");
    workflow.start();
    workflowInstanceRepository.save(workflow);

    Map<String, Object> eventData = new HashMap<>();
    eventService.recordEvent(executionId, ExecutionEventType.WORKFLOW_STARTED, eventData);

    for (int i = 1; i <= 3; i++) {
      eventData.put("nodeId", "node" + i);
      eventData.put("nodeType", "task");
      eventService.recordEvent(executionId, ExecutionEventType.NODE_STARTED, eventData);
      eventService.recordEvent(executionId, ExecutionEventType.NODE_COMPLETED, eventData);
    }

    eventService.recordEvent(executionId, ExecutionEventType.WORKFLOW_COMPLETED, eventData);

    workflow.complete();
    return workflowInstanceRepository.save(workflow);
  }
}
