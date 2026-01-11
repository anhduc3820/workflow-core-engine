package workflow.core.engine.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import workflow.core.engine.model.GraphNode;
import workflow.core.engine.model.NodeState;
import workflow.core.engine.model.NodeType;
import workflow.core.engine.model.WorkflowContext;

/**
 * Handler for START_EVENT nodes
 */
@Slf4j
@Component
public class StartEventHandler implements NodeHandler {

    @Override
    public void execute(GraphNode node, WorkflowContext context) throws NodeExecutionException {
        log.info("Executing START_EVENT: {}", node.getName());

        // Start events just mark the beginning of workflow
        context.recordNodeExecution(node.getId(), NodeState.COMPLETED,
                "Workflow started: " + node.getName());
    }

    @Override
    public boolean supports(GraphNode node) {
        return node.getType() == NodeType.START_EVENT;
    }
}

