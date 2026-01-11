package workflow.core.engine.validator;

import java.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import workflow.core.engine.model.*;

/** Workflow validation service Ensures workflow integrity and executability */
@Slf4j
@Service
public class WorkflowValidator {

  /** Validate workflow graph */
  public ValidationResult validate(WorkflowGraph graph) {
    ValidationResult result = new ValidationResult();

    // Rule 1: Must have exactly one start event
    validateStartEvent(graph, result);

    // Rule 2: Must have at least one end event
    validateEndEvents(graph, result);

    // Rule 3: No dangling edges
    validateEdgeConnectivity(graph, result);

    // Rule 4: Gateway semantics
    validateGateways(graph, result);

    // Rule 5: Reachability analysis
    validateReachability(graph, result);

    // Rule 6: Business rule task configuration
    validateBusinessRuleTasks(graph, result);

    // Rule 7: Service task configuration
    validateServiceTasks(graph, result);

    log.info(
        "Validation completed: {} errors, {} warnings",
        result.getErrors().size(),
        result.getWarnings().size());

    return result;
  }

  /** Validate start event */
  private void validateStartEvent(WorkflowGraph graph, ValidationResult result) {
    if (graph.getStartEvent() == null) {
      result.addError("START_EVENT_MISSING", "Workflow must have exactly one START_EVENT");
      return;
    }

    List<GraphEdge> outgoing = graph.getOutgoingEdges(graph.getStartEvent().getId());
    if (outgoing.isEmpty()) {
      result.addError(
          "START_EVENT_NO_OUTGOING", "START_EVENT must have at least one outgoing edge");
    }

    List<GraphEdge> incoming = graph.getIncomingEdges(graph.getStartEvent().getId());
    if (!incoming.isEmpty()) {
      result.addError("START_EVENT_HAS_INCOMING", "START_EVENT must not have incoming edges");
    }
  }

  /** Validate end events */
  private void validateEndEvents(WorkflowGraph graph, ValidationResult result) {
    if (graph.getEndEvents().isEmpty()) {
      result.addError("END_EVENT_MISSING", "Workflow must have at least one END_EVENT");
      return;
    }

    for (GraphNode endEvent : graph.getEndEvents()) {
      List<GraphEdge> incoming = graph.getIncomingEdges(endEvent.getId());
      if (incoming.isEmpty()) {
        result.addWarning(
            "END_EVENT_NO_INCOMING",
            "END_EVENT '" + endEvent.getName() + "' has no incoming edges");
      }

      List<GraphEdge> outgoing = graph.getOutgoingEdges(endEvent.getId());
      if (!outgoing.isEmpty()) {
        result.addError(
            "END_EVENT_HAS_OUTGOING",
            "END_EVENT '" + endEvent.getName() + "' must not have outgoing edges");
      }
    }
  }

  /** Validate edge connectivity */
  private void validateEdgeConnectivity(WorkflowGraph graph, ValidationResult result) {
    for (GraphNode node : graph.getAllNodes()) {
      String nodeId = node.getId();

      // Check for self-loops
      for (GraphEdge edge : graph.getOutgoingEdges(nodeId)) {
        if (edge.getSource().equals(edge.getTarget())) {
          result.addError("SELF_LOOP", "Node '" + node.getName() + "' has a self-loop edge");
        }

        // Verify target exists
        if (graph.getNode(edge.getTarget()) == null) {
          result.addError(
              "EDGE_TARGET_NOT_FOUND",
              "Edge '" + edge.getId() + "' targets non-existent node: " + edge.getTarget());
        }
      }
    }
  }

  /** Validate gateway semantics */
  private void validateGateways(WorkflowGraph graph, ValidationResult result) {
    for (GraphNode node : graph.getAllNodes()) {
      if (!node.getType().isGateway()) {
        continue;
      }

      GatewayType gatewayType = node.getConfig().getGatewayType();
      if (gatewayType == null) {
        result.addError(
            "GATEWAY_TYPE_MISSING", "Gateway '" + node.getName() + "' must specify gatewayType");
        continue;
      }

      List<GraphEdge> outgoing = graph.getOutgoingEdges(node.getId());
      List<GraphEdge> incoming = graph.getIncomingEdges(node.getId());

      // Diverging gateway (1 input, multiple outputs)
      if (incoming.size() == 1 && outgoing.size() > 1) {
        validateDivergingGateway(node, outgoing, gatewayType, result);
      }
      // Converging gateway (multiple inputs, 1 output)
      else if (incoming.size() > 1 && outgoing.size() == 1) {
        // Converging gateways are valid
      }
      // Mixed gateway
      else if (incoming.size() > 1 && outgoing.size() > 1) {
        result.addWarning(
            "GATEWAY_MIXED", "Gateway '" + node.getName() + "' is both diverging and converging");
      } else {
        result.addWarning(
            "GATEWAY_INVALID_STRUCTURE", "Gateway '" + node.getName() + "' has invalid structure");
      }
    }
  }

  /** Validate diverging gateway */
  private void validateDivergingGateway(
      GraphNode gateway,
      List<GraphEdge> outgoing,
      GatewayType gatewayType,
      ValidationResult result) {
    if (gatewayType == GatewayType.XOR || gatewayType == GatewayType.OR) {
      // XOR/OR gateways should have conditions on outgoing edges
      boolean hasDefaultPath = false;
      for (GraphEdge edge : outgoing) {
        if (!edge.hasCondition()) {
          if (!hasDefaultPath) {
            hasDefaultPath = true;
          } else {
            result.addWarning(
                "GATEWAY_MULTIPLE_DEFAULT",
                "Gateway '" + gateway.getName() + "' has multiple edges without conditions");
          }
        }
      }

      if (!hasDefaultPath) {
        result.addWarning(
            "GATEWAY_NO_DEFAULT",
            "Gateway '" + gateway.getName() + "' should have at least one default path");
      }
    }
  }

  /** Validate reachability using BFS */
  private void validateReachability(WorkflowGraph graph, ValidationResult result) {
    if (graph.getStartEvent() == null) {
      return; // Already reported error
    }

    // Forward reachability from start event
    Set<String> reachableFromStart = new HashSet<>();
    Queue<String> queue = new LinkedList<>();
    queue.add(graph.getStartEvent().getId());
    reachableFromStart.add(graph.getStartEvent().getId());

    while (!queue.isEmpty()) {
      String nodeId = queue.poll();
      for (GraphEdge edge : graph.getOutgoingEdges(nodeId)) {
        if (!reachableFromStart.contains(edge.getTarget())) {
          reachableFromStart.add(edge.getTarget());
          queue.add(edge.getTarget());
        }
      }
    }

    // Check unreachable nodes
    for (GraphNode node : graph.getAllNodes()) {
      if (!reachableFromStart.contains(node.getId())) {
        result.addWarning(
            "NODE_UNREACHABLE", "Node '" + node.getName() + "' is unreachable from START_EVENT");
      }
    }

    // Verify at least one end event is reachable
    boolean endEventReachable = false;
    for (GraphNode endEvent : graph.getEndEvents()) {
      if (reachableFromStart.contains(endEvent.getId())) {
        endEventReachable = true;
        break;
      }
    }

    if (!endEventReachable && !graph.getEndEvents().isEmpty()) {
      result.addError("NO_REACHABLE_END_EVENT", "No END_EVENT is reachable from START_EVENT");
    }
  }

  /** Validate business rule tasks */
  private void validateBusinessRuleTasks(WorkflowGraph graph, ValidationResult result) {
    for (GraphNode node : graph.getAllNodes()) {
      if (node.getType() != NodeType.BUSINESS_RULE_TASK) {
        continue;
      }

      NodeConfig config = node.getConfig();

      if (config.getRuleFile() == null || config.getRuleFile().trim().isEmpty()) {
        result.addError(
            "RULE_TASK_NO_FILE",
            "Business rule task '" + node.getName() + "' must specify ruleFile");
      }

      if (config.getRuleflowGroup() == null || config.getRuleflowGroup().trim().isEmpty()) {
        result.addError(
            "RULE_TASK_NO_GROUP",
            "Business rule task '" + node.getName() + "' must specify ruleflowGroup");
      }
    }
  }

  /** Validate service tasks */
  private void validateServiceTasks(WorkflowGraph graph, ValidationResult result) {
    for (GraphNode node : graph.getAllNodes()) {
      if (node.getType() != NodeType.SERVICE_TASK) {
        continue;
      }

      NodeConfig config = node.getConfig();

      if (config.getServiceName() == null || config.getServiceName().trim().isEmpty()) {
        result.addError(
            "SERVICE_TASK_NO_NAME",
            "Service task '" + node.getName() + "' must specify serviceName");
      }
    }
  }
}
