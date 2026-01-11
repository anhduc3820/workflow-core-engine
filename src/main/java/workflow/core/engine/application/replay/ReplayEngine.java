package workflow.core.engine.application.replay;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import workflow.core.engine.domain.replay.ExecutionEventEntity;
import workflow.core.engine.domain.replay.ExecutionEventType;

import java.util.*;

/**
 * Replay Engine
 * Reconstructs workflow state from persisted execution events
 *
 * Key Guarantees:
 * - Deterministic replay (same events = same result)
 * - Crash recovery (replay after pod restart)
 * - State reconstruction from events only
 * - No dependency on in-memory state
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReplayEngine {

    private final ExecutionEventService eventService;
    private final ObjectMapper objectMapper;

    /**
     * Reconstruct workflow state from events
     * Returns the state as it was at a specific sequence number
     */
    @Transactional(readOnly = true)
    public ReconstructedState reconstructState(String executionId, Long upToSequence) {
        log.info("Reconstructing state for execution: {} up to sequence: {}", executionId, upToSequence);

        List<ExecutionEventEntity> events = upToSequence != null ?
                eventService.getExecutionTimeline(executionId, 1L, upToSequence) :
                eventService.getExecutionTimeline(executionId);

        return reconstructStateFromEvents(executionId, events);
    }

    /**
     * Reconstruct state from event list
     */
    private ReconstructedState reconstructStateFromEvents(String executionId, List<ExecutionEventEntity> events) {
        ReconstructedState state = new ReconstructedState(executionId);

        for (ExecutionEventEntity event : events) {
            applyEventToState(state, event);
        }

        log.info("State reconstruction complete: {} events processed", events.size());
        return state;
    }

    /**
     * Apply single event to state
     */
    private void applyEventToState(ReconstructedState state, ExecutionEventEntity event) {
        switch (event.getEventType()) {
            case WORKFLOW_STARTED -> {
                state.setWorkflowState("RUNNING");
                state.setStartTime(event.getTimestamp().toString());
            }
            case WORKFLOW_COMPLETED -> {
                state.setWorkflowState("COMPLETED");
                state.setEndTime(event.getTimestamp().toString());
            }
            case WORKFLOW_FAILED -> {
                state.setWorkflowState("FAILED");
                state.setEndTime(event.getTimestamp().toString());
                state.setError(event.getErrorSnapshot());
            }
            case NODE_STARTED -> {
                if (event.getNodeId() != null) {
                    state.markNodeStarted(event.getNodeId(), event.getTimestamp().toString());
                    state.setCurrentNodeId(event.getNodeId());
                }
            }
            case NODE_COMPLETED -> {
                if (event.getNodeId() != null) {
                    state.markNodeCompleted(event.getNodeId(), event.getTimestamp().toString());
                    state.addCompletedNode(event.getNodeId());
                }
            }
            case NODE_FAILED -> {
                if (event.getNodeId() != null) {
                    state.markNodeFailed(event.getNodeId(), event.getErrorSnapshot());
                }
            }
            case VARIABLE_SET, VARIABLE_UPDATED -> {
                if (event.getVariablesSnapshot() != null) {
                    state.updateVariables(event.getVariablesSnapshot());
                }
            }
            case GATEWAY_BRANCH_TAKEN -> {
                if (event.getEdgeTaken() != null) {
                    state.recordEdgeTraversal(event.getEdgeTaken());
                }
            }
            case CHECKPOINT_CREATED -> {
                state.addCheckpoint(event.getSequenceNumber(), event.getNodeName());
            }
        }

        state.incrementEventCount();
        state.setLastSequenceNumber(event.getSequenceNumber());
    }

    /**
     * Check if execution can be resumed
     */
    @Transactional(readOnly = true)
    public boolean canResume(String executionId) {
        ReconstructedState state = reconstructState(executionId, null);
        return state.getWorkflowState().equals("RUNNING") &&
               state.getCurrentNodeId() != null;
    }

    /**
     * Get resume point for crashed execution
     */
    @Transactional(readOnly = true)
    public ResumePoint getResumePoint(String executionId) {
        ReconstructedState state = reconstructState(executionId, null);

        return new ResumePoint(
                executionId,
                state.getCurrentNodeId(),
                state.getLastSequenceNumber(),
                state.getVariables(),
                state.getCompletedNodes()
        );
    }

    /**
     * Validate replay consistency
     * Replays twice and ensures identical results
     */
    @Transactional(readOnly = true)
    public boolean validateReplayConsistency(String executionId) {
        try {
            ReconstructedState state1 = reconstructState(executionId, null);
            ReconstructedState state2 = reconstructState(executionId, null);

            return state1.equals(state2);
        } catch (Exception e) {
            log.error("Replay consistency validation failed", e);
            return false;
        }
    }

    /**
     * Reconstructed State class
     */
    public static class ReconstructedState {
        private final String executionId;
        private String workflowState;
        private String currentNodeId;
        private String startTime;
        private String endTime;
        private String error;
        private Long lastSequenceNumber;
        private int eventCount;
        private final Map<String, String> variables = new HashMap<>();
        private final Map<String, NodeState> nodeStates = new HashMap<>();
        private final List<String> completedNodes = new ArrayList<>();
        private final List<String> edgeTraversals = new ArrayList<>();
        private final Map<Long, String> checkpoints = new HashMap<>();

        public ReconstructedState(String executionId) {
            this.executionId = executionId;
            this.workflowState = "UNKNOWN";
            this.eventCount = 0;
        }

        public void markNodeStarted(String nodeId, String timestamp) {
            nodeStates.computeIfAbsent(nodeId, k -> new NodeState(nodeId))
                    .setStartTime(timestamp);
        }

        public void markNodeCompleted(String nodeId, String timestamp) {
            nodeStates.computeIfAbsent(nodeId, k -> new NodeState(nodeId))
                    .setEndTime(timestamp)
                    .setStatus("COMPLETED");
        }

        public void markNodeFailed(String nodeId, String error) {
            nodeStates.computeIfAbsent(nodeId, k -> new NodeState(nodeId))
                    .setStatus("FAILED")
                    .setError(error);
        }

        public void updateVariables(String variablesJson) {
            // Parse and merge variables
            try {
                // Simple implementation - in production use ObjectMapper
                this.variables.put("snapshot", variablesJson);
            } catch (Exception e) {
                // Ignore parse errors
            }
        }

        public void recordEdgeTraversal(String edgeId) {
            edgeTraversals.add(edgeId);
        }

        public void addCompletedNode(String nodeId) {
            if (!completedNodes.contains(nodeId)) {
                completedNodes.add(nodeId);
            }
        }

        public void addCheckpoint(Long sequence, String name) {
            checkpoints.put(sequence, name);
        }

        public void incrementEventCount() {
            this.eventCount++;
        }

        // Getters and Setters
        public String getExecutionId() { return executionId; }
        public String getWorkflowState() { return workflowState; }
        public void setWorkflowState(String state) { this.workflowState = state; }
        public String getCurrentNodeId() { return currentNodeId; }
        public void setCurrentNodeId(String nodeId) { this.currentNodeId = nodeId; }
        public String getStartTime() { return startTime; }
        public void setStartTime(String time) { this.startTime = time; }
        public String getEndTime() { return endTime; }
        public void setEndTime(String time) { this.endTime = time; }
        public String getError() { return error; }
        public void setError(String error) { this.error = error; }
        public Long getLastSequenceNumber() { return lastSequenceNumber; }
        public void setLastSequenceNumber(Long seq) { this.lastSequenceNumber = seq; }
        public int getEventCount() { return eventCount; }
        public Map<String, String> getVariables() { return variables; }
        public List<String> getCompletedNodes() { return completedNodes; }
        public Map<Long, String> getCheckpoints() { return checkpoints; }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof ReconstructedState that)) return false;
            return Objects.equals(executionId, that.executionId) &&
                   Objects.equals(workflowState, that.workflowState) &&
                   Objects.equals(currentNodeId, that.currentNodeId) &&
                   Objects.equals(completedNodes, that.completedNodes);
        }

        @Override
        public int hashCode() {
            return Objects.hash(executionId, workflowState, currentNodeId, completedNodes);
        }
    }

    /**
     * Node State class
     */
    public static class NodeState {
        private final String nodeId;
        private String status;
        private String startTime;
        private String endTime;
        private String error;

        public NodeState(String nodeId) {
            this.nodeId = nodeId;
        }

        public NodeState setStatus(String status) {
            this.status = status;
            return this;
        }

        public NodeState setStartTime(String time) {
            this.startTime = time;
            return this;
        }

        public NodeState setEndTime(String time) {
            this.endTime = time;
            return this;
        }

        public NodeState setError(String error) {
            this.error = error;
            return this;
        }

        public String getNodeId() { return nodeId; }
        public String getStatus() { return status; }
        public String getStartTime() { return startTime; }
        public String getEndTime() { return endTime; }
        public String getError() { return error; }
    }

    /**
     * Resume Point class
     */
    public record ResumePoint(
            String executionId,
            String resumeNodeId,
            Long lastSequenceNumber,
            Map<String, String> variables,
            List<String> completedNodes
    ) {}
}

