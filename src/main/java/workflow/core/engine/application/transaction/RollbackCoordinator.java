package workflow.core.engine.application.transaction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import workflow.core.engine.application.replay.ExecutionEventService;
import workflow.core.engine.domain.replay.ExecutionEventEntity;
import workflow.core.engine.domain.replay.ExecutionEventType;
import workflow.core.engine.domain.workflow.WorkflowInstanceEntity;
import workflow.core.engine.domain.workflow.WorkflowInstanceRepository;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Rollback Coordinator
 * Orchestrates multi-level rollback strategies for financial-grade workflow execution
 *
 * Rollback Levels:
 * 1. Node-level: Rollback single node execution
 * 2. Step-level: Rollback sequence of nodes up to a checkpoint
 * 3. Workflow-level: Rollback entire workflow instance
 *
 * Each rollback:
 * - Restores persisted state
 * - Restores workflow variables
 * - Executes compensation handlers
 * - Creates immutable audit trail
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RollbackCoordinator {

    private final ExecutionEventService eventService;
    private final CompensationService compensationService;
    private final WorkflowInstanceRepository workflowInstanceRepository;

    /**
     * Execute node-level rollback
     * Compensates single node and restores state to before node execution
     */
    @Transactional
    public RollbackResult rollbackNode(String executionId, String nodeId, RollbackReason reason) {
        log.info("Initiating node-level rollback: execution={}, node={}, reason={}",
                executionId, nodeId, reason);

        // Record rollback initiation
        recordRollbackEvent(executionId, nodeId, ExecutionEventType.ROLLBACK_INITIATED, reason);

        try {
            // Get node events
            List<ExecutionEventEntity> nodeEvents = eventService.getNodeEvents(executionId, nodeId);
            if (nodeEvents.isEmpty()) {
                return RollbackResult.failure("No events found for node: " + nodeId);
            }

            // Find last completed event
            Optional<ExecutionEventEntity> lastCompletedEvent = nodeEvents.stream()
                    .filter(e -> e.getEventType() == ExecutionEventType.NODE_COMPLETED)
                    .max(Comparator.comparing(ExecutionEventEntity::getSequenceNumber));

            if (lastCompletedEvent.isEmpty()) {
                return RollbackResult.failure("Node not completed, cannot rollback: " + nodeId);
            }

            // Capture state before rollback
            ExecutionEventEntity completedEvent = lastCompletedEvent.get();
            String preRollbackSnapshot = captureStateSnapshot(executionId, completedEvent);

            // Execute compensation
            CompensationService.CompensationResult compensationResult =
                    compensationService.compensateNode(executionId, nodeId);

            if (!compensationResult.isSuccess()) {
                recordRollbackEvent(executionId, nodeId, ExecutionEventType.ROLLBACK_FAILED, reason);
                return RollbackResult.failure("Compensation failed: " + compensationResult.getMessage());
            }

            // Restore state
            boolean stateRestored = restoreStateFromSnapshot(executionId, completedEvent);
            if (!stateRestored) {
                log.warn("State restoration failed for node: {}", nodeId);
            }

            // Record rollback completed
            recordRollbackEvent(executionId, nodeId, ExecutionEventType.ROLLBACK_COMPLETED, reason);

            log.info("Node-level rollback completed successfully: {}", nodeId);
            return RollbackResult.success("Node rolled back successfully",
                    Map.of("nodeId", nodeId, "compensated", true, "stateRestored", stateRestored));

        } catch (Exception e) {
            log.error("Node-level rollback failed: execution={}, node={}", executionId, nodeId, e);
            recordRollbackEvent(executionId, nodeId, ExecutionEventType.ROLLBACK_FAILED, reason);
            return RollbackResult.failure("Rollback failed: " + e.getMessage());
        }
    }

    /**
     * Execute step-level rollback
     * Compensates sequence of nodes from current position back to checkpoint
     */
    @Transactional
    public RollbackResult rollbackToCheckpoint(String executionId, Long checkpointSequence, RollbackReason reason) {
        log.info("Initiating step-level rollback: execution={}, checkpoint={}, reason={}",
                executionId, checkpointSequence, reason);

        try {
            // Get all events after checkpoint
            List<ExecutionEventEntity> eventsToRollback = eventService.getExecutionTimeline(executionId).stream()
                    .filter(e -> e.getSequenceNumber() > checkpointSequence)
                    .filter(e -> e.getEventType() == ExecutionEventType.NODE_COMPLETED)
                    .sorted(Comparator.comparing(ExecutionEventEntity::getSequenceNumber).reversed())
                    .collect(Collectors.toList());

            if (eventsToRollback.isEmpty()) {
                return RollbackResult.success("No events to rollback", Map.of());
            }

            log.info("Rolling back {} nodes to checkpoint {}", eventsToRollback.size(), checkpointSequence);

            // Rollback nodes in reverse order
            List<String> rolledBackNodes = new ArrayList<>();
            List<String> failedNodes = new ArrayList<>();

            for (ExecutionEventEntity event : eventsToRollback) {
                String nodeId = event.getNodeId();
                RollbackResult nodeResult = rollbackNode(executionId, nodeId, reason);

                if (nodeResult.isSuccess()) {
                    rolledBackNodes.add(nodeId);
                } else {
                    failedNodes.add(nodeId);
                    log.error("Failed to rollback node {} during step-level rollback", nodeId);
                }
            }

            boolean allSuccess = failedNodes.isEmpty();
            String message = allSuccess ?
                    "Step-level rollback completed successfully" :
                    "Step-level rollback completed with failures";

            return new RollbackResult(
                    allSuccess,
                    message,
                    Map.of(
                            "rolledBackNodes", rolledBackNodes,
                            "failedNodes", failedNodes,
                            "totalNodes", eventsToRollback.size()
                    )
            );

        } catch (Exception e) {
            log.error("Step-level rollback failed: execution={}", executionId, e);
            return RollbackResult.failure("Step-level rollback failed: " + e.getMessage());
        }
    }

    /**
     * Execute workflow-level rollback
     * Compensates entire workflow and resets to initial state
     */
    @Transactional
    public RollbackResult rollbackWorkflow(String executionId, RollbackReason reason) {
        log.info("Initiating workflow-level rollback: execution={}, reason={}", executionId, reason);

        try {
            // Get workflow instance
            WorkflowInstanceEntity instance = workflowInstanceRepository.findById(executionId)
                    .orElseThrow(() -> new IllegalArgumentException("Workflow instance not found: " + executionId));

            // Capture pre-rollback state
            String preRollbackState = instance.getState();
            String preRollbackVariables = instance.getCurrentVariables();

            // Get all completed node events
            List<ExecutionEventEntity> completedEvents = eventService.getExecutionTimeline(executionId).stream()
                    .filter(e -> e.getEventType() == ExecutionEventType.NODE_COMPLETED)
                    .sorted(Comparator.comparing(ExecutionEventEntity::getSequenceNumber).reversed())
                    .collect(Collectors.toList());

            log.info("Rolling back entire workflow with {} completed nodes", completedEvents.size());

            // Rollback all nodes in reverse order
            List<String> rolledBackNodes = new ArrayList<>();
            List<String> failedNodes = new ArrayList<>();

            for (ExecutionEventEntity event : completedEvents) {
                String nodeId = event.getNodeId();
                RollbackResult nodeResult = rollbackNode(executionId, nodeId, reason);

                if (nodeResult.isSuccess()) {
                    rolledBackNodes.add(nodeId);
                } else {
                    failedNodes.add(nodeId);
                }
            }

            // Reset workflow instance to initial state
            instance.markCancelled();
            workflowInstanceRepository.save(instance);

            // Record workflow rollback event
            Map<String, Object> eventData = new HashMap<>();
            eventData.put("preRollbackState", preRollbackState);
            eventData.put("rolledBackNodes", rolledBackNodes);
            eventData.put("failedNodes", failedNodes);
            eventData.put("reason", reason.toString());

            eventService.recordEvent(executionId, ExecutionEventType.WORKFLOW_ROLLED_BACK, eventData);

            boolean allSuccess = failedNodes.isEmpty();
            String message = allSuccess ?
                    "Workflow rollback completed successfully" :
                    "Workflow rollback completed with some failures";

            return new RollbackResult(
                    allSuccess,
                    message,
                    Map.of(
                            "rolledBackNodes", rolledBackNodes,
                            "failedNodes", failedNodes,
                            "totalNodes", completedEvents.size(),
                            "workflowState", "CANCELLED"
                    )
            );

        } catch (Exception e) {
            log.error("Workflow-level rollback failed: execution={}", executionId, e);
            return RollbackResult.failure("Workflow-level rollback failed: " + e.getMessage());
        }
    }

    /**
     * Create a checkpoint for potential future rollback
     */
    @Transactional
    public Long createCheckpoint(String executionId, String checkpointName) {
        log.info("Creating checkpoint: {} for execution: {}", checkpointName, executionId);

        Map<String, Object> eventData = new HashMap<>();
        eventData.put("checkpointName", checkpointName);
        eventData.put("timestamp", System.currentTimeMillis());

        ExecutionEventEntity checkpointEvent = eventService.recordEvent(
                executionId, ExecutionEventType.CHECKPOINT_CREATED, eventData);

        return checkpointEvent.getSequenceNumber();
    }

    /**
     * Get available checkpoints for an execution
     */
    @Transactional(readOnly = true)
    public List<CheckpointInfo> getCheckpoints(String executionId) {
        List<ExecutionEventEntity> checkpointEvents = eventService.getExecutionTimeline(executionId).stream()
                .filter(e -> e.getEventType() == ExecutionEventType.CHECKPOINT_CREATED)
                .collect(Collectors.toList());

        return checkpointEvents.stream()
                .map(e -> new CheckpointInfo(
                        e.getSequenceNumber(),
                        e.getNodeName(),
                        e.getTimestamp().toString()
                ))
                .collect(Collectors.toList());
    }

    private String captureStateSnapshot(String executionId, ExecutionEventEntity event) {
        // Capture current state for audit trail
        return String.format("snapshot-%s-%d", executionId, event.getSequenceNumber());
    }

    private boolean restoreStateFromSnapshot(String executionId, ExecutionEventEntity event) {
        try {
            // Restore workflow variables from event
            if (event.getVariablesSnapshot() != null) {
                WorkflowInstanceEntity instance = workflowInstanceRepository.findById(executionId)
                        .orElseThrow(() -> new IllegalArgumentException("Workflow not found"));
                instance.updateVariables(event.getVariablesSnapshot());
                workflowInstanceRepository.save(instance);
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("Failed to restore state from snapshot", e);
            return false;
        }
    }

    private void recordRollbackEvent(String executionId, String nodeId,
                                     ExecutionEventType eventType, RollbackReason reason) {
        Map<String, Object> eventData = new HashMap<>();
        eventData.put("nodeId", nodeId);
        eventData.put("reason", reason.toString());
        eventData.put("reasonDetails", reason.getDetails());
        eventService.recordEvent(executionId, eventType, eventData);
    }

    // Nested Classes

    public record CheckpointInfo(Long sequenceNumber, String name, String timestamp) {}

    public static class RollbackResult {
        private final boolean success;
        private final String message;
        private final Map<String, Object> details;

        public RollbackResult(boolean success, String message, Map<String, Object> details) {
            this.success = success;
            this.message = message;
            this.details = details;
        }

        public static RollbackResult success(String message, Map<String, Object> details) {
            return new RollbackResult(true, message, details);
        }

        public static RollbackResult failure(String message) {
            return new RollbackResult(false, message, Map.of());
        }

        public boolean isSuccess() { return success; }
        public String getMessage() { return message; }
        public Map<String, Object> getDetails() { return details; }
    }

    public static class RollbackReason {
        private final String code;
        private final String details;

        public RollbackReason(String code, String details) {
            this.code = code;
            this.details = details;
        }

        public static RollbackReason userRequested(String details) {
            return new RollbackReason("USER_REQUESTED", details);
        }

        public static RollbackReason executionFailed(String details) {
            return new RollbackReason("EXECUTION_FAILED", details);
        }

        public static RollbackReason validationFailed(String details) {
            return new RollbackReason("VALIDATION_FAILED", details);
        }

        public static RollbackReason timeoutExceeded(String details) {
            return new RollbackReason("TIMEOUT_EXCEEDED", details);
        }

        @Override
        public String toString() {
            return code;
        }

        public String getDetails() { return details; }
        public String getCode() { return code; }
    }
}

