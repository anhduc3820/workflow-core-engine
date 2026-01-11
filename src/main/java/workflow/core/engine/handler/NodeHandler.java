package workflow.core.engine.handler;

import workflow.core.engine.model.GraphNode;
import workflow.core.engine.model.WorkflowContext;

/**
 * Node handler interface
 * Each node type has a specific handler implementation
 */
public interface NodeHandler {

    /**
     * Execute the node
     *
     * @param node The node to execute
     * @param context The workflow execution context
     * @throws NodeExecutionException If execution fails
     */
    void execute(GraphNode node, WorkflowContext context) throws NodeExecutionException;

    /**
     * Check if this handler supports the given node
     */
    boolean supports(GraphNode node);
}

