package workflow.core.engine.application.executor;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import workflow.core.engine.domain.node.NodeExecutionEntity;
import workflow.core.engine.domain.workflow.WorkflowInstanceEntity;
import workflow.core.engine.handler.NodeExecutionException;
import workflow.core.engine.handler.NodeHandler;
import workflow.core.engine.model.*;

/**
 * Use Case: Execute Workflow (Stateless, HA-Ready) Core workflow execution orchestrator Fully
 * stateless - all state externalized to ExecutionStateManager Supports horizontal scaling and HA
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatelessWorkflowExecutor {

  private final ExecutionStateManager stateManager;
  private final List<NodeHandler> nodeHandlers;
  private final NodeExecutorService nodeExecutorService;

  /**
   * Execute workflow asynchronously Returns immediately with execution ID
   *
   * @param workflowGraph The compiled workflow graph
   * @param initialVariables Initial workflow variables
   * @return Execution ID
   */
  public String executeAsync(WorkflowGraph workflowGraph, Map<String, Object> initialVariables) {
    // Create workflow instance
    WorkflowInstanceEntity instance =
        stateManager.createInstance(
            workflowGraph.getWorkflowId(), workflowGraph.getVersion(), initialVariables);

    String executionId = instance.getExecutionId();
    log.info("Created async workflow execution: {}", executionId);

    // Execute asynchronously
    executeWorkflowAsync(executionId, workflowGraph);

    return executionId;
  }

  /**
   * Execute workflow synchronously Blocks until completion or failure
   *
   * @param workflowGraph The compiled workflow graph
   * @param initialVariables Initial workflow variables
   * @return Completed workflow instance
   */
  public WorkflowInstanceEntity executeSync(
      WorkflowGraph workflowGraph, Map<String, Object> initialVariables) {
    // Create workflow instance
    WorkflowInstanceEntity instance =
        stateManager.createInstance(
            workflowGraph.getWorkflowId(), workflowGraph.getVersion(), initialVariables);

    String executionId = instance.getExecutionId();
    log.info("Starting sync workflow execution: {}", executionId);

    // Execute synchronously
    executeWorkflow(executionId, workflowGraph);

    // Return completed instance
    return stateManager.getInstance(executionId);
  }

  /**
   * Resume workflow execution (for HA recovery or paused workflows)
   *
   * @param executionId The execution ID to resume
   * @param workflowGraph The workflow graph
   */
  public void resumeExecution(String executionId, WorkflowGraph workflowGraph) {
    log.info("Resuming workflow execution: {}", executionId);

    // Try to acquire lock
    if (!stateManager.acquireLock(executionId)) {
      log.warn("Cannot resume execution {} - locked by another instance", executionId);
      return;
    }

    try {
      WorkflowInstanceEntity instance = stateManager.getInstance(executionId);

      // Resume from current node
      if (instance.getCurrentNodeId() != null) {
        GraphNode currentNode = workflowGraph.getNode(instance.getCurrentNodeId());
        if (currentNode != null) {
          continueFromNode(executionId, currentNode, workflowGraph);
        } else {
          log.error("Current node {} not found in graph", instance.getCurrentNodeId());
          stateManager.failWorkflow(
              executionId, "Current node not found in workflow", instance.getCurrentNodeId());
        }
      } else {
        // Start from beginning
        executeWorkflow(executionId, workflowGraph);
      }
    } finally {
      stateManager.releaseLock(executionId);
    }
  }

  @Async
  @Transactional
  protected CompletableFuture<Void> executeWorkflowAsync(
      String executionId, WorkflowGraph workflowGraph) {
    return CompletableFuture.runAsync(() -> executeWorkflow(executionId, workflowGraph));
  }

  /** Main execution logic (stateless) */
  private void executeWorkflow(String executionId, WorkflowGraph workflowGraph) {
    // Try to acquire lock
    if (!stateManager.acquireLock(executionId)) {
      log.warn("Cannot execute workflow {} - locked by another instance", executionId);
      return;
    }

    try {
      stateManager.startExecution(executionId);

      // Get start event
      GraphNode startEvent = workflowGraph.getStartEvent();
      if (startEvent == null) {
        throw new IllegalStateException("Workflow has no START_EVENT");
      }

      // Execute from start
      executeNode(executionId, startEvent, workflowGraph);

      // Mark as completed if we reached here without errors
      stateManager.completeWorkflow(executionId);
      log.info("Workflow execution completed: {}", executionId);

    } catch (Exception e) {
      log.error("Workflow execution failed: {}", executionId, e);
      String errorNodeId = null;
      if (e instanceof NodeExecutionException) {
        errorNodeId = ((NodeExecutionException) e).getNodeId();
      }
      stateManager.failWorkflow(executionId, e.getMessage(), errorNodeId);
    } finally {
      stateManager.releaseLock(executionId);
    }
  }

  /** Execute a single node (idempotent) */
  private void executeNode(String executionId, GraphNode node, WorkflowGraph workflowGraph) {
    log.debug("Executing node: {} ({})", node.getName(), node.getType());

    // Check if already executed (idempotency)
    if (stateManager.hasNodeBeenExecuted(executionId, node.getId())) {
      log.info("Node {} already executed, skipping", node.getId());
      continueToNextNodes(executionId, node, workflowGraph);
      return;
    }

    // Update current node
    stateManager.updateCurrentNode(executionId, node.getId());

    // Get current variables
    Map<String, Object> variables = stateManager.getVariables(executionId);

    // Record node start
    NodeExecutionEntity execution = stateManager.recordNodeStart(executionId, node, variables);

    try {
      // Execute node
      nodeExecutorService.execute(node, execution, variables);

      // Record completion
      stateManager.recordNodeComplete(execution, variables);

      // Update variables
      stateManager.updateVariables(executionId, variables);

      // Check if this is an end event
      if (node.getType() == NodeType.END_EVENT) {
        log.info("Reached END_EVENT: {}", node.getName());
        return;
      }

      // Continue to next nodes
      continueToNextNodes(executionId, node, workflowGraph);

    } catch (Exception e) {
      log.error("Node execution failed: {} in workflow: {}", node.getId(), executionId, e);
      stateManager.recordNodeFailure(execution, e.getMessage());
      throw new NodeExecutionException(node.getId(), "Node execution failed: " + e.getMessage(), e);
    }
  }

  /** Continue to next nodes after current node completes */
  private void continueToNextNodes(
      String executionId, GraphNode currentNode, WorkflowGraph workflowGraph) {
    List<GraphEdge> outgoingEdges = workflowGraph.getOutgoingEdges(currentNode.getId());

    if (outgoingEdges.isEmpty()) {
      log.warn("Node '{}' has no outgoing edges", currentNode.getName());
      return;
    }

    // Get variables for edge evaluation
    Map<String, Object> variables = stateManager.getVariables(executionId);

    // Determine which edges to take
    List<GraphEdge> edgesToTake =
        nodeExecutorService.selectEdges(currentNode, outgoingEdges, variables);

    // Execute target nodes
    for (GraphEdge edge : edgesToTake) {
      log.debug("Traversing edge: {} -> {}", edge.getSource(), edge.getTarget());

      GraphNode targetNode = workflowGraph.getNode(edge.getTarget());
      if (targetNode == null) {
        throw new IllegalStateException("Target node not found: " + edge.getTarget());
      }

      executeNode(executionId, targetNode, workflowGraph);
    }
  }

  /** Continue from a specific node (for resume) */
  private void continueFromNode(String executionId, GraphNode node, WorkflowGraph workflowGraph) {
    log.info("Continuing from node: {}", node.getId());
    continueToNextNodes(executionId, node, workflowGraph);
  }
}
