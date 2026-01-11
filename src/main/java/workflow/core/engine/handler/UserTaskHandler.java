package workflow.core.engine.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import workflow.core.engine.model.GraphNode;
import workflow.core.engine.model.NodeState;
import workflow.core.engine.model.NodeType;
import workflow.core.engine.model.WorkflowContext;

/**
 * Handler for USER_TASK nodes
 * User tasks pause workflow execution until manual completion
 */
@Slf4j
@Component
public class UserTaskHandler implements NodeHandler {

    @Override
    public void execute(GraphNode node, WorkflowContext context) throws NodeExecutionException {
        log.info("Executing USER_TASK: {}", node.getName());

        // User tasks pause the workflow
        // In a real implementation, this would:
        // 1. Create a task in a task management system
        // 2. Assign it to user/group
        // 3. Wait for completion
        // 4. Resume workflow with user input

        context.recordNodeExecution(node.getId(), NodeState.COMPLETED,
                "User task created: " + node.getName());

        log.info("User task '{}' created and waiting for completion", node.getName());
    }

    @Override
    public boolean supports(GraphNode node) {
        return node.getType() == NodeType.USER_TASK;
    }
}

