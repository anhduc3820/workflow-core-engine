package workflow.core.engine.application.executor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import workflow.core.engine.domain.node.NodeExecutionEntity;
import workflow.core.engine.executor.ConditionEvaluator;
import workflow.core.engine.handler.NodeExecutionException;
import workflow.core.engine.handler.NodeHandler;
import workflow.core.engine.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service: Node Executor
 * Handles individual node execution logic
 * Separated from main executor for better testability
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NodeExecutorService {

    private final List<NodeHandler> nodeHandlers;
    private final ConditionEvaluator conditionEvaluator;

    /**
     * Execute a node using appropriate handler
     */
    public void execute(GraphNode node, NodeExecutionEntity execution, Map<String, Object> variables) {
        NodeHandler handler = findHandler(node);

        if (handler == null) {
            throw new NodeExecutionException(node.getId(),
                    "No handler found for node type: " + node.getType());
        }

        // Create temporary context for handler compatibility
        WorkflowContext tempContext = new WorkflowContext();
        tempContext.setExecutionId(execution.getWorkflowInstance().getExecutionId());
        tempContext.setWorkflowId(execution.getWorkflowInstance().getWorkflowId());
        tempContext.setCurrentNodeId(node.getId());
        tempContext.getVariables().putAll(variables);

        // Execute handler
        handler.execute(node, tempContext);

        // Update variables from context
        variables.clear();
        variables.putAll(tempContext.getVariables());
    }

    /**
     * Select which edges to take based on node type and conditions
     */
    public List<GraphEdge> selectEdges(GraphNode node, List<GraphEdge> outgoingEdges, Map<String, Object> variables) {
        // If only one edge, take it
        if (outgoingEdges.size() == 1) {
            return outgoingEdges;
        }

        // Based on node type
        if (node.getType() == NodeType.EXCLUSIVE_GATEWAY) {
            return selectExclusiveGatewayEdge(outgoingEdges, variables);
        } else if (node.getType() == NodeType.PARALLEL_GATEWAY) {
            return selectParallelGatewayEdges(outgoingEdges, variables);
        } else if (node.getType() == NodeType.INCLUSIVE_GATEWAY) {
            return selectInclusiveGatewayEdges(outgoingEdges, variables);
        } else {
            // For regular nodes with multiple outgoing edges, take first edge
            // (This shouldn't normally happen in valid workflows)
            log.warn("Node {} has multiple outgoing edges but is not a gateway, taking first edge", node.getId());
            return List.of(outgoingEdges.get(0));
        }
    }

    /**
     * Select edge for XOR gateway (exactly one edge)
     */
    private List<GraphEdge> selectExclusiveGatewayEdge(List<GraphEdge> edges, Map<String, Object> variables) {
        // Find first edge with satisfied condition
        for (GraphEdge edge : edges) {
            if (evaluateEdgeCondition(edge, variables)) {
                return List.of(edge);
            }
        }

        // If no condition is met, look for default edge
        for (GraphEdge edge : edges) {
            if (edge.getPathType() == PathType.DEFAULT) {
                log.debug("Taking default edge: {}", edge.getId());
                return List.of(edge);
            }
        }

        throw new IllegalStateException("No edge condition satisfied in XOR gateway and no default edge found");
    }

    /**
     * Select edges for AND gateway (all edges)
     */
    private List<GraphEdge> selectParallelGatewayEdges(List<GraphEdge> edges, Map<String, Object> variables) {
        // Take all edges (parallel execution)
        log.debug("Taking all {} edges for parallel gateway", edges.size());
        return new ArrayList<>(edges);
    }

    /**
     * Select edges for OR gateway (all edges with satisfied conditions)
     */
    private List<GraphEdge> selectInclusiveGatewayEdges(List<GraphEdge> edges, Map<String, Object> variables) {
        List<GraphEdge> selectedEdges = edges.stream()
                .filter(edge -> evaluateEdgeCondition(edge, variables))
                .collect(Collectors.toList());

        if (selectedEdges.isEmpty()) {
            // Take default edge if no conditions are met
            for (GraphEdge edge : edges) {
                if (edge.getPathType() == PathType.DEFAULT) {
                    log.debug("Taking default edge for OR gateway: {}", edge.getId());
                    return List.of(edge);
                }
            }
            throw new IllegalStateException("No edge condition satisfied in OR gateway and no default edge found");
        }

        log.debug("Taking {} edges for inclusive gateway", selectedEdges.size());
        return selectedEdges;
    }

    /**
     * Evaluate edge condition
     */
    private boolean evaluateEdgeCondition(GraphEdge edge, Map<String, Object> variables) {
        String condition = edge.getCondition();

        if (condition == null || condition.trim().isEmpty()) {
            // No condition means always true (except for default path)
            return edge.getPathType() != PathType.DEFAULT;
        }

        try {
            boolean result = conditionEvaluator.evaluate(condition, variables);
            log.debug("Edge {} condition '{}' evaluated to: {}", edge.getId(), condition, result);
            return result;
        } catch (Exception e) {
            log.error("Failed to evaluate condition for edge {}: {}", edge.getId(), condition, e);
            return false;
        }
    }

    /**
     * Find appropriate handler for node type
     */
    private NodeHandler findHandler(GraphNode node) {
        return nodeHandlers.stream()
                .filter(handler -> handler.supports(node))
                .findFirst()
                .orElse(null);
    }
}

