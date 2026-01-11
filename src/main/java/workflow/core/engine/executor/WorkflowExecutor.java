package workflow.core.engine.executor;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import workflow.core.engine.handler.NodeExecutionException;
import workflow.core.engine.handler.NodeHandler;
import workflow.core.engine.model.*;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Core workflow execution engine
 * Orchestrates workflow execution in a deterministic, node-driven manner
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowExecutor {

    private final List<NodeHandler> nodeHandlers;
    private final ConditionEvaluator conditionEvaluator;

    /**
     * Execute workflow
     *
     * @param graph The workflow graph
     * @param initialVariables Initial workflow variables
     * @return Execution context with results
     */
    public WorkflowContext execute(WorkflowGraph graph, Map<String, Object> initialVariables) {
        log.info("Starting workflow execution: {} (v{})", graph.getName(), graph.getVersion());

        // Create execution context
        WorkflowContext context = new WorkflowContext(graph.getWorkflowId(), graph.getVersion());
        context.setState(ExecutionState.RUNNING);
        context.setStartTime(Instant.now());

        // Set initial variables
        if (initialVariables != null) {
            context.getVariables().putAll(initialVariables);
        }

        try {
            // Start from start event
            if (graph.getStartEvent() == null) {
                throw new IllegalStateException("Workflow has no START_EVENT");
            }

            // Execute workflow starting from start event
            executeNode(graph.getStartEvent(), graph, context);

            // Mark as completed
            context.setState(ExecutionState.COMPLETED);
            context.setEndTime(Instant.now());

            log.info("Workflow execution completed: {}", graph.getName());

        } catch (Exception e) {
            log.error("Workflow execution failed: {}", graph.getName(), e);
            context.setState(ExecutionState.FAILED);
            context.setEndTime(Instant.now());
            context.setErrorMessage(e.getMessage());

            if (e instanceof NodeExecutionException) {
                context.setErrorNodeId(((NodeExecutionException) e).getNodeId());
            }
        }

        return context;
    }

    /**
     * Execute a single node and continue to next nodes
     */
    private void executeNode(GraphNode node, WorkflowGraph graph, WorkflowContext context) {
        log.debug("Executing node: {} ({})", node.getName(), node.getType());

        // Mark node as running
        node.markRunning();
        context.setCurrentNodeId(node.getId());
        context.recordNodeExecution(node.getId(), NodeState.RUNNING, "Node started");

        try {
            // Find appropriate handler
            NodeHandler handler = findHandler(node);

            if (handler == null) {
                throw new NodeExecutionException(node.getId(),
                        "No handler found for node type: " + node.getType());
            }

            // Execute node
            handler.execute(node, context);

            // Mark as completed
            node.markCompleted();

            // Check if this is an end event
            if (node.getType() == NodeType.END_EVENT) {
                log.info("Reached END_EVENT: {}", node.getName());
                return;
            }

            // Continue to next nodes
            continueExecution(node, graph, context);

        } catch (NodeExecutionException e) {
            node.markFailed();
            throw e;
        } catch (Exception e) {
            node.markFailed();
            throw new NodeExecutionException(node.getId(),
                    "Node execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Continue execution to next nodes based on gateway logic
     */
    private void continueExecution(GraphNode currentNode, WorkflowGraph graph, WorkflowContext context) {
        List<GraphEdge> outgoingEdges = graph.getOutgoingEdges(currentNode.getId());

        if (outgoingEdges.isEmpty()) {
            log.warn("Node '{}' has no outgoing edges", currentNode.getName());
            return;
        }

        // Determine which edges to take based on gateway type
        List<GraphEdge> edgesToTake = selectEdgesToTake(currentNode, outgoingEdges, context);

        // Execute target nodes
        for (GraphEdge edge : edgesToTake) {
            edge.markTraversed();
            log.debug("Traversing edge: {} -> {}", edge.getSource(), edge.getTarget());

            GraphNode targetNode = graph.getNode(edge.getTarget());
            if (targetNode == null) {
                throw new IllegalStateException("Target node not found: " + edge.getTarget());
            }

            executeNode(targetNode, graph, context);
        }
    }

    /**
     * Select which edges to take based on gateway logic
     */
    private List<GraphEdge> selectEdgesToTake(GraphNode node, List<GraphEdge> edges, WorkflowContext context) {
        // If not a gateway, take all edges (should be only one in most cases)
        if (!node.getType().isGateway()) {
            return edges;
        }

        GatewayType gatewayType = node.getConfig().getGatewayType();

        switch (gatewayType) {
            case XOR:
                return selectExclusiveGatewayEdge(edges, context);
            case AND:
                return selectParallelGatewayEdges(edges);
            case OR:
                return selectInclusiveGatewayEdges(edges, context);
            default:
                log.warn("Unknown gateway type: {}, taking all edges", gatewayType);
                return edges;
        }
    }

    /**
     * XOR Gateway: Take exactly one edge based on priority and conditions
     */
    private List<GraphEdge> selectExclusiveGatewayEdge(List<GraphEdge> edges, WorkflowContext context) {
        // Sort by priority
        List<GraphEdge> sortedEdges = edges.stream()
                .sorted(Comparator.comparingInt(GraphEdge::getPriority))
                .collect(Collectors.toList());

        // Take first edge where condition evaluates to true
        for (GraphEdge edge : sortedEdges) {
            if (evaluateEdgeCondition(edge, context)) {
                log.debug("XOR Gateway: Selected edge {} (condition: {})",
                        edge.getId(), edge.getCondition());
                return Collections.singletonList(edge);
            }
        }

        // If no condition matches, take default (first edge without condition)
        for (GraphEdge edge : sortedEdges) {
            if (!edge.hasCondition()) {
                log.debug("XOR Gateway: Selected default edge {}", edge.getId());
                return Collections.singletonList(edge);
            }
        }

        log.warn("XOR Gateway: No edge selected, taking first edge");
        return Collections.singletonList(sortedEdges.get(0));
    }

    /**
     * AND Gateway: Take all edges (parallel execution)
     */
    private List<GraphEdge> selectParallelGatewayEdges(List<GraphEdge> edges) {
        log.debug("AND Gateway: Taking all {} edges", edges.size());
        return edges;
    }

    /**
     * OR Gateway: Take all edges where condition evaluates to true
     */
    private List<GraphEdge> selectInclusiveGatewayEdges(List<GraphEdge> edges, WorkflowContext context) {
        List<GraphEdge> selectedEdges = new ArrayList<>();

        for (GraphEdge edge : edges) {
            if (evaluateEdgeCondition(edge, context)) {
                selectedEdges.add(edge);
            }
        }

        if (selectedEdges.isEmpty()) {
            // Take default edge if no conditions match
            for (GraphEdge edge : edges) {
                if (!edge.hasCondition()) {
                    selectedEdges.add(edge);
                    break;
                }
            }
        }

        log.debug("OR Gateway: Selected {} edges", selectedEdges.size());
        return selectedEdges;
    }

    /**
     * Evaluate edge condition
     */
    private boolean evaluateEdgeCondition(GraphEdge edge, WorkflowContext context) {
        if (!edge.hasCondition()) {
            return true; // No condition means always true
        }

        try {
            // Try simple evaluation first (faster)
            return conditionEvaluator.evaluateSimple(edge.getCondition(), context);
        } catch (Exception e) {
            log.warn("Simple evaluation failed for condition: {}, trying full evaluation",
                    edge.getCondition());
            // Fallback to full JavaScript evaluation
            return conditionEvaluator.evaluate(edge.getCondition(), context);
        }
    }

    /**
     * Find appropriate handler for node
     */
    private NodeHandler findHandler(GraphNode node) {
        for (NodeHandler handler : nodeHandlers) {
            if (handler.supports(node)) {
                return handler;
            }
        }
        return null;
    }
}

