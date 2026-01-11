package workflow.core.engine.application.replay;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import workflow.core.engine.domain.replay.ExecutionEventEntity;

/**
 * Visual Execution Replay Service Provides deterministic replay of workflow execution for visual
 * frontend
 *
 * <p>Key Features: - Step-by-step replay - Timeline-based replay - Node highlighting based on
 * execution state - Edge traversal visualization - React Flow compatible output
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VisualExecutionReplayService {

  private final ExecutionEventService eventService;

  /** Get full execution replay data for frontend visualization */
  @Transactional(readOnly = true)
  public ExecutionReplayData getExecutionReplay(String executionId) {
    List<ExecutionEventEntity> events = eventService.getExecutionTimeline(executionId);

    if (events.isEmpty()) {
      log.warn("No events found for execution: {}", executionId);
      return new ExecutionReplayData(executionId, List.of(), Map.of());
    }

    // Convert events to replay steps
    List<ReplayStep> steps = new ArrayList<>();
    long stepNumber = 0;

    for (ExecutionEventEntity event : events) {
      ReplayStep step =
          new ReplayStep(
              stepNumber++,
              event.getSequenceNumber(),
              event.getEventType().name(),
              event.getNodeId(),
              event.getNodeType(),
              event.getNodeName(),
              event.getStatus().name(),
              event.getTimestamp().toString(),
              event.getDurationMs(),
              event.getEdgeTaken(),
              event.getDecisionResult(),
              event.getErrorSnapshot());
      steps.add(step);
    }

    // Build execution statistics
    ExecutionEventService.ExecutionStatistics stats = eventService.getStatistics(executionId);
    Map<String, Object> metadata =
        Map.of(
            "totalSteps", steps.size(),
            "totalEvents", stats.totalEvents(),
            "completedEvents", stats.completedEvents(),
            "failedEvents", stats.failedEvents(),
            "totalDurationMs", stats.totalDurationMs());

    return new ExecutionReplayData(executionId, steps, metadata);
  }

  /** Get execution replay for specific time range */
  @Transactional(readOnly = true)
  public ExecutionReplayData getExecutionReplay(String executionId, Long startSeq, Long endSeq) {
    List<ExecutionEventEntity> events =
        eventService.getExecutionTimeline(executionId, startSeq, endSeq);

    List<ReplayStep> steps =
        events.stream()
            .map(
                event ->
                    new ReplayStep(
                        event.getSequenceNumber(),
                        event.getSequenceNumber(),
                        event.getEventType().name(),
                        event.getNodeId(),
                        event.getNodeType(),
                        event.getNodeName(),
                        event.getStatus().name(),
                        event.getTimestamp().toString(),
                        event.getDurationMs(),
                        event.getEdgeTaken(),
                        event.getDecisionResult(),
                        event.getErrorSnapshot()))
            .collect(Collectors.toList());

    return new ExecutionReplayData(executionId, steps, Map.of());
  }

  /** Get node execution states for visualization Returns map of nodeId -> execution state */
  @Transactional(readOnly = true)
  public Map<String, NodeExecutionState> getNodeStates(String executionId) {
    List<ExecutionEventEntity> events = eventService.getExecutionTimeline(executionId);

    Map<String, NodeExecutionState> nodeStates = new HashMap<>();

    for (ExecutionEventEntity event : events) {
      if (event.getNodeId() == null) continue;

      String nodeId = event.getNodeId();
      NodeExecutionState state =
          nodeStates.computeIfAbsent(
              nodeId,
              k -> new NodeExecutionState(nodeId, event.getNodeType(), event.getNodeName()));

      // Update state based on event type
      switch (event.getEventType()) {
        case NODE_ENTERED -> state.setStatus("entered");
        case NODE_STARTED -> {
          state.setStatus("active");
          state.setStartTime(event.getTimestamp().toString());
        }
        case NODE_COMPLETED -> {
          state.setStatus("completed");
          state.setEndTime(event.getTimestamp().toString());
          state.setDurationMs(event.getDurationMs());
        }
        case NODE_FAILED -> {
          state.setStatus("failed");
          state.setEndTime(event.getTimestamp().toString());
          state.setError(event.getErrorSnapshot());
        }
        case NODE_SKIPPED -> state.setStatus("skipped");
      }
    }

    return nodeStates;
  }

  /** Get edge traversal order for visualization */
  @Transactional(readOnly = true)
  public List<EdgeTraversal> getEdgeTraversals(String executionId) {
    List<ExecutionEventEntity> events = eventService.getExecutionTimeline(executionId);

    List<EdgeTraversal> traversals = new ArrayList<>();
    long order = 0;

    for (ExecutionEventEntity event : events) {
      if (event.getEdgeTaken() != null) {
        traversals.add(
            new EdgeTraversal(
                order++,
                event.getEdgeTaken(),
                event.getTimestamp().toString(),
                event.getDecisionResult()));
      }
    }

    return traversals;
  }

  // DTOs for frontend
  public record ExecutionReplayData(
      String executionId, List<ReplayStep> steps, Map<String, Object> metadata) {}

  public record ReplayStep(
      Long stepNumber,
      Long sequenceNumber,
      String eventType,
      String nodeId,
      String nodeType,
      String nodeName,
      String status,
      String timestamp,
      Long durationMs,
      String edgeTaken,
      String decisionResult,
      String error) {}

  public static class NodeExecutionState {
    private final String nodeId;
    private final String nodeType;
    private final String nodeName;
    private String status;
    private String startTime;
    private String endTime;
    private Long durationMs;
    private String error;

    public NodeExecutionState(String nodeId, String nodeType, String nodeName) {
      this.nodeId = nodeId;
      this.nodeType = nodeType;
      this.nodeName = nodeName;
      this.status = "pending";
    }

    // Getters and setters
    public String getNodeId() {
      return nodeId;
    }

    public String getNodeType() {
      return nodeType;
    }

    public String getNodeName() {
      return nodeName;
    }

    public String getStatus() {
      return status;
    }

    public void setStatus(String status) {
      this.status = status;
    }

    public String getStartTime() {
      return startTime;
    }

    public void setStartTime(String startTime) {
      this.startTime = startTime;
    }

    public String getEndTime() {
      return endTime;
    }

    public void setEndTime(String endTime) {
      this.endTime = endTime;
    }

    public Long getDurationMs() {
      return durationMs;
    }

    public void setDurationMs(Long durationMs) {
      this.durationMs = durationMs;
    }

    public String getError() {
      return error;
    }

    public void setError(String error) {
      this.error = error;
    }
  }

  public record EdgeTraversal(Long order, String edgeId, String timestamp, String decisionResult) {}
}
