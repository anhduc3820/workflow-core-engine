package workflow.core.engine.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import workflow.core.engine.model.GraphNode;
import workflow.core.engine.model.NodeType;
import workflow.core.engine.model.WorkflowContext;

/**
 * Handler for generic TASK nodes
 *
 * <p>This handler processes simple task nodes that don't require specific business logic. For more
 * complex tasks, use ServiceTaskHandler or UserTaskHandler.
 */
@Slf4j
@Component
public class TaskHandler implements NodeHandler {

  @Override
  public boolean supports(GraphNode node) {
    return node.getType() == NodeType.TASK;
  }

  @Override
  public void execute(GraphNode node, WorkflowContext context) {
    log.debug("Executing TASK: {} ({})", node.getName(), node.getId());

    // Generic task execution - simply marks as complete
    // In a real system, this might invoke external systems or perform computations

    // Log execution
    log.info("Task '{}' completed successfully", node.getName());

    // Task nodes typically just pass through or perform simple operations
    // Any variables set in the workflow definition are already in context
  }
}
