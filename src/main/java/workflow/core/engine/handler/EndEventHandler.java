package workflow.core.engine.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import workflow.core.engine.model.GraphNode;
import workflow.core.engine.model.NodeState;
import workflow.core.engine.model.NodeType;
import workflow.core.engine.model.WorkflowContext;

/**
 * Handler for END_EVENT nodes
 */
@Slf4j
@Component
public class EndEventHandler implements NodeHandler {

    @Override
    public void execute(GraphNode node, WorkflowContext context) throws NodeExecutionException {
        log.info("Executing END_EVENT: {}", node.getName());

        Boolean terminate = node.getConfig().getTerminate();

        if (Boolean.TRUE.equals(terminate)) {
            log.info("Terminating workflow at END_EVENT: {}", node.getName());
            context.recordNodeExecution(node.getId(), NodeState.COMPLETED,
                    "Workflow terminated: " + node.getName());
        } else {
            context.recordNodeExecution(node.getId(), NodeState.COMPLETED,
                    "Workflow completed: " + node.getName());
        }
    }

    @Override
    public boolean supports(GraphNode node) {
        return node.getType() == NodeType.END_EVENT;
    }
}

