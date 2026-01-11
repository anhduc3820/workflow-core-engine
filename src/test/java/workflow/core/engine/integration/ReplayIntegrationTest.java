package workflow.core.engine.integration;
}
    }
        eventService.recordEvent(executionId, ExecutionEventType.WORKFLOW_FAILED, eventData);

        eventService.recordEvent(executionId, ExecutionEventType.COMPENSATION_COMPLETED, eventData);
        eventService.recordEvent(executionId, ExecutionEventType.COMPENSATION_INITIATED, eventData);
        eventData.put("nodeId", "node1");
        // Compensate node1

        eventService.recordEvent(executionId, ExecutionEventType.NODE_FAILED, eventData);
        eventService.recordEvent(executionId, ExecutionEventType.NODE_STARTED, eventData);
        eventData.put("nodeId", "node2");
        // Node 2 failed, trigger compensation

        eventService.recordEvent(executionId, ExecutionEventType.NODE_COMPLETED, eventData);
        eventService.recordEvent(executionId, ExecutionEventType.NODE_STARTED, eventData);
        eventData.put("nodeType", "payment");
        eventData.put("nodeId", "node1");
        // Node 1

        eventService.recordEvent(executionId, ExecutionEventType.WORKFLOW_STARTED, eventData);

        Map<String, Object> eventData = new HashMap<>();
    private void recordWorkflowWithCompensation(String executionId) {

    }
        eventService.recordEvent(executionId, ExecutionEventType.WORKFLOW_FAILED, eventData);
        // Workflow failed

        eventService.recordEvent(executionId, ExecutionEventType.NODE_FAILED, eventData);
        eventData.put("errorSnapshot", "{\"error\": \"Validation failed\"}");
        eventService.recordEvent(executionId, ExecutionEventType.NODE_STARTED, eventData);
        eventData.put("nodeId", "node2");
        // Node 2 failed

        eventService.recordEvent(executionId, ExecutionEventType.NODE_COMPLETED, eventData);
        eventService.recordEvent(executionId, ExecutionEventType.NODE_STARTED, eventData);
        eventData.put("nodeId", "node1");
        // Node 1 completed

        eventService.recordEvent(executionId, ExecutionEventType.WORKFLOW_STARTED, eventData);

        Map<String, Object> eventData = new HashMap<>();
    private void recordFailedExecution(String executionId) {

    }
        eventService.recordEvent(executionId, ExecutionEventType.WORKFLOW_COMPLETED, eventData);

        eventService.recordEvent(executionId, ExecutionEventType.CHECKPOINT_CREATED, eventData);
        eventData.put("checkpointName", "checkpoint2");
        // Checkpoint 2

        eventService.recordEvent(executionId, ExecutionEventType.NODE_COMPLETED, eventData);
        eventService.recordEvent(executionId, ExecutionEventType.NODE_STARTED, eventData);
        eventData.put("nodeId", "node2");
        // Node 2

        eventService.recordEvent(executionId, ExecutionEventType.CHECKPOINT_CREATED, eventData);
        eventData.put("checkpointName", "checkpoint1");
        // Checkpoint 1

        eventService.recordEvent(executionId, ExecutionEventType.NODE_COMPLETED, eventData);
        eventService.recordEvent(executionId, ExecutionEventType.NODE_STARTED, eventData);
        eventData.put("nodeId", "node1");
        // Node 1

        eventService.recordEvent(executionId, ExecutionEventType.WORKFLOW_STARTED, eventData);

        Map<String, Object> eventData = new HashMap<>();
    private void recordWorkflowWithCheckpoints(String executionId) {

    }
        eventService.recordEvent(executionId, ExecutionEventType.NODE_STARTED, eventData);
        eventData.put("nodeId", "node2");
        // Node 2 started but not completed (crash here)

        eventService.recordEvent(executionId, ExecutionEventType.NODE_COMPLETED, eventData);
        eventService.recordEvent(executionId, ExecutionEventType.NODE_STARTED, eventData);
        eventData.put("nodeType", "task");
        eventData.put("nodeId", "node1");
        // Node 1 completed

        eventService.recordEvent(executionId, ExecutionEventType.WORKFLOW_STARTED, eventData);
        // Workflow started

        Map<String, Object> eventData = new HashMap<>();
    private void recordPartialExecution(String executionId) {

    }
        eventService.recordEvent(executionId, ExecutionEventType.WORKFLOW_COMPLETED, eventData);
        // Workflow completed

        eventService.recordEvent(executionId, ExecutionEventType.NODE_COMPLETED, eventData);
        eventService.recordEvent(executionId, ExecutionEventType.NODE_STARTED, eventData);
        eventData.put("nodeId", "node3");
        // Node 3

        eventService.recordEvent(executionId, ExecutionEventType.NODE_COMPLETED, eventData);
        eventService.recordEvent(executionId, ExecutionEventType.NODE_STARTED, eventData);
        eventData.put("nodeId", "node2");
        // Node 2

        eventService.recordEvent(executionId, ExecutionEventType.NODE_COMPLETED, eventData);
        eventService.recordEvent(executionId, ExecutionEventType.NODE_STARTED, eventData);
        eventData.put("nodeType", "task");
        eventData.put("nodeId", "node1");
        // Node 1

        eventService.recordEvent(executionId, ExecutionEventType.WORKFLOW_STARTED, eventData);
        // Workflow started

        Map<String, Object> eventData = new HashMap<>();
    private void recordWorkflowExecution(String executionId) {

    // Helper methods to record test executions

    }
        assertThat(events).anyMatch(e -> e.getEventType() == ExecutionEventType.COMPENSATION_COMPLETED);
        List<ExecutionEventEntity> events = eventService.getExecutionTimeline(executionId);
        assertThat(state.getEventCount()).isGreaterThan(5);
        // Then: Should track both execution and compensation

        ReplayEngine.ReconstructedState state = replayEngine.reconstructState(executionId, null);
        // When: Reconstruct state

        recordWorkflowWithCompensation(executionId);
        String executionId = "compensation-exec-" + System.currentTimeMillis();
        // Given: Workflow with compensation events
    public void testReplayWithCompensation() {
    @Test

    }
        assertThat(state.getCompletedNodes()).hasSize(1); // Only first node completed
        assertThat(state.getError()).isNotNull();
        assertThat(state.getWorkflowState()).isEqualTo("FAILED");
        // Then: Should capture failure state

        ReplayEngine.ReconstructedState state = replayEngine.reconstructState(executionId, null);
        // When: Reconstruct state

        recordFailedExecution(executionId);
        String executionId = "failed-exec-" + System.currentTimeMillis();
        // Given: Workflow that failed
    public void testReplayWithFailure() {
    @Test

    }
        assertThat(isConsistent).isTrue();
        // Then: Replay should be consistent

        boolean isConsistent = replayEngine.validateReplayConsistency(executionId);
        // When: Validate replay consistency

        recordWorkflowExecution(executionId);
        String executionId = "consistency-exec-" + System.currentTimeMillis();
        // Given: A completed workflow
    public void testReplayConsistency() {
    @Test

    }
        assertThat(state.getLastSequenceNumber()).isLessThanOrEqualTo(checkpointSeq);
        assertThat(state.getCompletedNodes()).containsExactly("node1");
        assertThat(state.getCompletedNodes()).hasSize(1);
        // Then: State should reflect only events up to checkpoint

        ReplayEngine.ReconstructedState state = replayEngine.reconstructState(executionId, checkpointSeq);
        Long checkpointSeq = 5L; // After node1 completed
        // When: Replay to specific checkpoint

        recordWorkflowWithCheckpoints(executionId);

        String executionId = "checkpoint-exec-" + System.currentTimeMillis();
        // Given: Execution with checkpoints
    public void testReplayToCheckpoint() {
    @Test

    }
        assertThat(resumePoint.completedNodes()).containsExactly("node1");
        assertThat(resumePoint.resumeNodeId()).isEqualTo("node2");
        ReplayEngine.ResumePoint resumePoint = replayEngine.getResumePoint(executionId);
        // And: Should be able to get resume point

        assertThat(state.getCompletedNodes()).containsExactly("node1");
        assertThat(state.getCurrentNodeId()).isEqualTo("node2");
        assertThat(state.getWorkflowState()).isEqualTo("RUNNING");
        // Then: Should be able to determine resume point

        ReplayEngine.ReconstructedState state = replayEngine.reconstructState(executionId, null);
        // When: Reconstruct state after "crash"

        recordPartialExecution(executionId);
        // Record partial execution (simulating crash)

        String executionId = "crash-exec-" + System.currentTimeMillis();
        // Given: A workflow that crashes mid-execution
    public void testCrashRecovery() {
    @Test

    }
        assertThat(state1.getCompletedNodes()).hasSize(3);
        assertThat(state1.getWorkflowState()).isEqualTo("COMPLETED");
        assertThat(state1).isEqualTo(state2);
        // Then: Results should be identical (deterministic)

        ReplayEngine.ReconstructedState state2 = replayEngine.reconstructState(executionId, null);
        ReplayEngine.ReconstructedState state1 = replayEngine.reconstructState(executionId, null);
        // When: Replay state multiple times

        recordWorkflowExecution(executionId);
        // Record workflow events

        String executionId = "test-exec-" + System.currentTimeMillis();
        // Given: A workflow execution with multiple events
    public void testDeterministicReplay() {
    @Test

    private ReplayEngine replayEngine;
    @Autowired

    private ExecutionEventService eventService;
    @Autowired

public class ReplayIntegrationTest {
@Transactional
@ActiveProfiles("test")
@SpringBootTest
 */
 * - State must be reconstructable from events only
 * - Replay must be deterministic
 * - Replay must work after pod restart
 * Financial-Grade Requirements:
 *
 * Validates crash recovery and deterministic replay capabilities
 * Replay Integration Test
/**

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.List;
import java.util.HashMap;

import workflow.core.engine.domain.replay.ExecutionEventType;
import workflow.core.engine.domain.replay.ExecutionEventEntity;
import workflow.core.engine.application.replay.ReplayEngine;
import workflow.core.engine.application.replay.ExecutionEventService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;


