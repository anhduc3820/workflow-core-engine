package workflow.core.engine.application.transaction;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import workflow.core.engine.application.replay.ExecutionEventService;
import workflow.core.engine.domain.replay.ExecutionEventEntity;
import workflow.core.engine.domain.replay.ExecutionEventType;

/**
 * Compensation Service Handles compensating actions for rollback scenarios
 *
 * <p>Compensation Strategies: 1. Node-level compensation (undo single node) 2. Step-level
 * compensation (undo sequence of nodes) 3. Workflow-level compensation (undo entire workflow)
 *
 * <p>Each compensable action must register a compensation handler
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompensationService {

  private final ExecutionEventService eventService;

  // Registered compensation handlers
  private final Map<String, CompensationHandler> compensationHandlers = new ConcurrentHashMap<>();

  // Per-node compensation handlers (for specific executions)
  private final Map<String, CompensationHandler> nodeCompensationHandlers =
      new ConcurrentHashMap<>();

  /** Register a compensation handler for a node type */
  public void registerHandler(String nodeType, CompensationHandler handler) {
    compensationHandlers.put(nodeType, handler);
    log.info("Registered compensation handler for node type: {}", nodeType);
  }

  /** Register a compensation handler for specific node execution */
  public void registerNodeCompensation(
      String executionId, String nodeId, CompensationHandler handler) {
    String key = executionId + ":" + nodeId;
    nodeCompensationHandlers.put(key, handler);
    log.info("Registered node-specific compensation handler: {}", key);
  }

  /** Compensate a single node execution */
  @Transactional
  public CompensationResult compensateNode(String executionId, String nodeId) {
    log.info("Compensating node: {} in execution: {}", nodeId, executionId);

    // Get node events
    List<ExecutionEventEntity> nodeEvents = eventService.getNodeEvents(executionId, nodeId);
    if (nodeEvents.isEmpty()) {
      log.warn("No events found for node: {}", nodeId);
      return new CompensationResult(false, "No events found", null);
    }

    // Find the last completed event
    Optional<ExecutionEventEntity> lastEvent =
        nodeEvents.stream()
            .filter(e -> e.getEventType() == ExecutionEventType.NODE_COMPLETED)
            .findFirst();

    if (lastEvent.isEmpty()) {
      log.warn("Node not completed, cannot compensate: {}", nodeId);
      return new CompensationResult(false, "Node not completed", null);
    }

    ExecutionEventEntity completedEvent = lastEvent.get();
    String nodeType = completedEvent.getNodeType();

    // Get compensation handler (check node-specific first, then type-based)
    String nodeKey = executionId + ":" + nodeId;
    CompensationHandler handler =
        nodeCompensationHandlers.getOrDefault(nodeKey, compensationHandlers.get(nodeType));

    if (handler == null) {
      log.warn("No compensation handler for node type: {}", nodeType);
      // Record compensation initiated but not implemented
      Map<String, Object> eventData =
          Map.of(
              "nodeId", nodeId,
              "nodeType", nodeType,
              "reason", "No compensation handler available");
      eventService.recordEvent(executionId, ExecutionEventType.COMPENSATION_INITIATED, eventData);
      return new CompensationResult(false, "No compensation handler", null);
    }

    // Record compensation initiated
    Map<String, Object> eventData =
        Map.of(
            "nodeId", nodeId,
            "nodeType", nodeType);
    ExecutionEventEntity compensationEvent =
        eventService.recordEvent(executionId, ExecutionEventType.COMPENSATION_INITIATED, eventData);

    try {
      // Execute compensation
      CompensationContext context =
          new CompensationContext(
              executionId, nodeId, nodeType, completedEvent.getOutputSnapshot());

      handler.compensate(context);

      // Mark event as compensated
      completedEvent.markCompensated(compensationEvent.getId());

      // Record compensation completed
      eventData =
          Map.of(
              "nodeId", nodeId,
              "nodeType", nodeType,
              "compensationEventId", compensationEvent.getId());
      eventService.recordEvent(executionId, ExecutionEventType.COMPENSATION_COMPLETED, eventData);

      log.info("Compensation successful for node: {}", nodeId);
      return new CompensationResult(true, "Compensation successful", compensationEvent.getId());

    } catch (Exception e) {
      log.error("Compensation failed for node: {}", nodeId, e);

      // Record compensation failed
      Map<String, Object> errorData =
          Map.of(
              "nodeId", nodeId,
              "nodeType", nodeType,
              "error", e.getMessage());
      eventService.recordEvent(executionId, ExecutionEventType.COMPENSATION_FAILED, errorData);

      return new CompensationResult(false, "Compensation failed: " + e.getMessage(), null);
    }
  }

  /** Compensate a sequence of nodes (from endNodeId back to startNodeId) */
  @Transactional
  public List<CompensationResult> compensateSequence(
      String executionId, String startNodeId, String endNodeId) {
    log.info(
        "Compensating sequence from {} to {} in execution: {}",
        startNodeId,
        endNodeId,
        executionId);

    List<ExecutionEventEntity> allEvents = eventService.getExecutionTimeline(executionId);

    // Find nodes between start and end
    List<String> nodesToCompensate = extractNodesInSequence(allEvents, startNodeId, endNodeId);

    // Compensate in reverse order
    Collections.reverse(nodesToCompensate);

    List<CompensationResult> results = new ArrayList<>();
    for (String nodeId : nodesToCompensate) {
      CompensationResult result = compensateNode(executionId, nodeId);
      results.add(result);

      // Stop if compensation fails
      if (!result.success()) {
        log.warn("Compensation sequence stopped at node: {} due to failure", nodeId);
        break;
      }
    }

    return results;
  }

  /** Compensate entire workflow execution */
  @Transactional
  public List<CompensationResult> compensateWorkflow(String executionId) {
    log.info("Compensating entire workflow execution: {}", executionId);

    List<ExecutionEventEntity> allEvents = eventService.getExecutionTimeline(executionId);

    // Extract all completed nodes
    Set<String> completedNodes = new LinkedHashSet<>();
    for (ExecutionEventEntity event : allEvents) {
      if (event.getEventType() == ExecutionEventType.NODE_COMPLETED && event.getNodeId() != null) {
        completedNodes.add(event.getNodeId());
      }
    }

    // Compensate in reverse order
    List<String> nodesToCompensate = new ArrayList<>(completedNodes);
    Collections.reverse(nodesToCompensate);

    List<CompensationResult> results = new ArrayList<>();
    for (String nodeId : nodesToCompensate) {
      CompensationResult result = compensateNode(executionId, nodeId);
      results.add(result);
    }

    return results;
  }

  /** Extract nodes in sequence between start and end */
  private List<String> extractNodesInSequence(
      List<ExecutionEventEntity> events, String startNodeId, String endNodeId) {
    List<String> nodes = new ArrayList<>();
    boolean collecting = false;

    for (ExecutionEventEntity event : events) {
      if (event.getNodeId() == null) continue;

      if (event.getNodeId().equals(startNodeId)) {
        collecting = true;
      }

      if (collecting && event.getEventType() == ExecutionEventType.NODE_COMPLETED) {
        if (!nodes.contains(event.getNodeId())) {
          nodes.add(event.getNodeId());
        }
      }

      if (event.getNodeId().equals(endNodeId)) {
        break;
      }
    }

    return nodes;
  }

  /** Compensation Handler Interface */
  @FunctionalInterface
  public interface CompensationHandler {
    void compensate(CompensationContext context) throws Exception;
  }

  /** Compensation Context */
  public static class CompensationContext {
    private final String executionId;
    private final String nodeId;
    private final String nodeType;
    private final String originalOutputSnapshot;

    public CompensationContext(
        String executionId, String nodeId, String nodeType, String originalOutputSnapshot) {
      this.executionId = executionId;
      this.nodeId = nodeId;
      this.nodeType = nodeType;
      this.originalOutputSnapshot = originalOutputSnapshot;
    }

    public String getExecutionId() {
      return executionId;
    }

    public String getNodeId() {
      return nodeId;
    }

    public String getNodeType() {
      return nodeType;
    }

    public String getOriginalOutputSnapshot() {
      return originalOutputSnapshot;
    }
  }

  /** Compensation Result */
  public record CompensationResult(boolean success, String message, Long compensationEventId) {}
}
