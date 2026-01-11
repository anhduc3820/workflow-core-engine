package workflow.core.engine.handler;

import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import workflow.core.engine.model.GraphNode;
import workflow.core.engine.model.NodeConfig;
import workflow.core.engine.model.NodeState;
import workflow.core.engine.model.NodeType;
import workflow.core.engine.model.WorkflowContext;
import workflow.core.engine.service.DroolsService;

/** Handler for BUSINESS_RULE_TASK nodes */
@Slf4j
@Component
@RequiredArgsConstructor
public class BusinessRuleTaskHandler implements NodeHandler {

  private final DroolsService droolsService;

  @Override
  public void execute(GraphNode node, WorkflowContext context) throws NodeExecutionException {
    log.info("Executing BUSINESS_RULE_TASK: {}", node.getName());

    NodeConfig config = node.getConfig();
    String ruleFile = config.getRuleFile();
    String ruleflowGroup = config.getRuleflowGroup();

    try {
      // Prepare input variables
      Map<String, Object> input = prepareInput(config, context);

      log.debug("Executing rules from file: {} with ruleflow-group: {}", ruleFile, ruleflowGroup);

      // Execute rules
      Map<String, Object> output = droolsService.executeRules(ruleFile, ruleflowGroup, input);

      // Map output variables
      mapOutput(config, output, context);

      context.recordNodeExecution(
          node.getId(), NodeState.COMPLETED, "Rules executed successfully: " + ruleflowGroup);

      log.info("Business rule task '{}' completed", node.getName());

    } catch (Exception e) {
      log.error("Failed to execute business rule task: {}", node.getName(), e);
      context.recordNodeExecution(
          node.getId(), NodeState.FAILED, "Rule execution failed: " + e.getMessage());
      throw new NodeExecutionException(
          node.getId(), "Failed to execute business rules: " + e.getMessage(), e);
    }
  }

  /** Prepare input variables from context */
  private Map<String, Object> prepareInput(NodeConfig config, WorkflowContext context) {
    Map<String, Object> input = new HashMap<>();

    if (config.getInputMappings() != null) {
      for (NodeConfig.VariableMapping mapping : config.getInputMappings()) {
        Object value = context.getVariables().get(mapping.getSource());
        input.put(mapping.getTarget(), value);
        log.debug("Input mapping: {} -> {} = {}", mapping.getSource(), mapping.getTarget(), value);
      }
    }

    return input;
  }

  /** Map output variables to context */
  private void mapOutput(NodeConfig config, Map<String, Object> output, WorkflowContext context) {
    if (config.getOutputMappings() != null) {
      for (NodeConfig.VariableMapping mapping : config.getOutputMappings()) {
        Object value = output.get(mapping.getSource());
        context.setVariable(mapping.getTarget(), value);
        log.debug("Output mapping: {} -> {} = {}", mapping.getSource(), mapping.getTarget(), value);
      }
    }
  }

  @Override
  public boolean supports(GraphNode node) {
    return node.getType() == NodeType.BUSINESS_RULE_TASK;
  }
}
