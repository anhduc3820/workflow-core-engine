package workflow.core.engine.handler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import workflow.core.engine.executor.ConditionEvaluator;
import workflow.core.engine.model.*;

/** Handler for Gateway nodes (EXCLUSIVE, PARALLEL, INCLUSIVE) */
@Slf4j
@Component
@RequiredArgsConstructor
public class GatewayHandler implements NodeHandler {

  private final ConditionEvaluator conditionEvaluator;

  @Override
  public void execute(GraphNode node, WorkflowContext context) throws NodeExecutionException {
    log.info("Executing Gateway: {} ({})", node.getName(), node.getConfig().getGatewayType());

    GatewayType gatewayType = node.getConfig().getGatewayType();

    if (gatewayType == null) {
      throw new NodeExecutionException(node.getId(), "Gateway type not specified");
    }

    switch (gatewayType) {
      case XOR:
        handleExclusiveGateway(node, context);
        break;
      case AND:
        handleParallelGateway(node, context);
        break;
      case OR:
        handleInclusiveGateway(node, context);
        break;
      default:
        throw new NodeExecutionException(node.getId(), "Unsupported gateway type: " + gatewayType);
    }

    context.recordNodeExecution(
        node.getId(), NodeState.COMPLETED, "Gateway processed: " + gatewayType);
  }

  /** Handle XOR (Exclusive) Gateway Takes exactly one path based on conditions */
  private void handleExclusiveGateway(GraphNode node, WorkflowContext context) {
    log.debug("Processing XOR gateway: {}", node.getName());
    // Logic handled by executor when selecting next edges
  }

  /** Handle AND (Parallel) Gateway Takes all paths in parallel */
  private void handleParallelGateway(GraphNode node, WorkflowContext context) {
    log.debug("Processing AND gateway: {}", node.getName());
    // Logic handled by executor when selecting next edges
  }

  /** Handle OR (Inclusive) Gateway Takes one or more paths based on conditions */
  private void handleInclusiveGateway(GraphNode node, WorkflowContext context) {
    log.debug("Processing OR gateway: {}", node.getName());
    // Logic handled by executor when selecting next edges
  }

  @Override
  public boolean supports(GraphNode node) {
    return node.getType().isGateway();
  }
}
