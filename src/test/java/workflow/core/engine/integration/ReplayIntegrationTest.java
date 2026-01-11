package workflow.core.engine.integration;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import workflow.core.engine.application.replay.ExecutionEventService;
import workflow.core.engine.application.replay.ReplayEngine;
import workflow.core.engine.domain.replay.ExecutionEventType;

/**
 * Replay Integration Test - Financial-Grade Requirements: - Replay must work after pod restart -
 * Replay must be deterministic - State must be reconstructable from events only
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
public class ReplayIntegrationTest {

  @Autowired private ExecutionEventService eventService;

  @Autowired private ReplayEngine replayEngine;

  @Test
  public void testDeterministicReplay() {
    // Given: A workflow execution with multiple events
    String executionId = "test-exec-" + System.currentTimeMillis();

    // Record workflow events
    recordWorkflowExecution(executionId);

    // When: Replay state multiple times
    ReplayEngine.ReconstructedState state1 = replayEngine.reconstructState(executionId, null);
    ReplayEngine.ReconstructedState state2 = replayEngine.reconstructState(executionId, null);

    // Then: Results should be identical (deterministic)
    assertThat(state1).isEqualTo(state2);
    assertThat(state1.getWorkflowState()).isEqualTo("COMPLETED");
    assertThat(state1.getCompletedNodes()).hasSize(3);
  }

  // Helper methods to record test executions

  private void recordWorkflowExecution(String executionId) {
    Map<String, Object> eventData = new HashMap<>();

    // Workflow started
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

    // Workflow completed
    eventService.recordEvent(executionId, ExecutionEventType.WORKFLOW_COMPLETED, eventData);
  }
}
