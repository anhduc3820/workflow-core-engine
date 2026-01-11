package workflow.core.engine.handler;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import workflow.core.engine.model.GraphNode;
import workflow.core.engine.model.NodeConfig;
import workflow.core.engine.model.NodeState;
import workflow.core.engine.model.NodeType;
import workflow.core.engine.model.WorkflowContext;

/** Handler for SERVICE_TASK nodes Invokes Spring beans by service name */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceTaskHandler implements NodeHandler {

  private final ApplicationContext applicationContext;

  @Override
  public void execute(GraphNode node, WorkflowContext context) throws NodeExecutionException {
    log.info("Executing SERVICE_TASK: {}", node.getName());

    NodeConfig config = node.getConfig();
    String serviceName = config.getServiceName();
    String methodName = config.getServiceMethod();

    // Validate service name
    if (serviceName == null || serviceName.trim().isEmpty()) {
      log.warn(
          "Service task '{}' has no service name configured - treating as pass-through",
          node.getName());
      context.recordNodeExecution(
          node.getId(), NodeState.COMPLETED, "Service task pass-through (no service configured)");
      return;
    }

    try {
      // Get service bean
      Object serviceBean = applicationContext.getBean(serviceName);

      // Prepare input
      Map<String, Object> input = prepareInput(config, context);

      // Invoke service method
      Map<String, Object> output;
      if (methodName != null && !methodName.isEmpty()) {
        output = invokeServiceMethod(serviceBean, methodName, input);
      } else {
        // Default method name: execute
        output = invokeServiceMethod(serviceBean, "execute", input);
      }

      // Map output
      mapOutput(config, output, context);

      context.recordNodeExecution(
          node.getId(), NodeState.COMPLETED, "Service task completed: " + serviceName);

      log.info("Service task '{}' completed", node.getName());

    } catch (Exception e) {
      log.error("Failed to execute service task: {}", node.getName(), e);
      context.recordNodeExecution(
          node.getId(), NodeState.FAILED, "Service execution failed: " + e.getMessage());

      // Check retry policy
      if (shouldRetry(config, node)) {
        log.info("Retrying service task: {}", node.getName());
        // TODO: Implement retry logic with backoff strategy
      }

      throw new NodeExecutionException(
          node.getId(), "Failed to execute service task: " + e.getMessage(), e);
    }
  }

  /** Invoke service method using reflection */
  @SuppressWarnings("unchecked")
  private Map<String, Object> invokeServiceMethod(
      Object serviceBean, String methodName, Map<String, Object> input) throws Exception {
    Method method = serviceBean.getClass().getMethod(methodName, Map.class);
    Object result = method.invoke(serviceBean, input);

    if (result instanceof Map) {
      return (Map<String, Object>) result;
    } else {
      Map<String, Object> output = new HashMap<>();
      output.put("result", result);
      return output;
    }
  }

  /** Prepare input variables */
  private Map<String, Object> prepareInput(NodeConfig config, WorkflowContext context) {
    Map<String, Object> input = new HashMap<>();

    if (config.getInputMappings() != null) {
      for (NodeConfig.VariableMapping mapping : config.getInputMappings()) {
        Object value = context.getVariables().get(mapping.getSource());
        input.put(mapping.getTarget(), value);
      }
    }

    return input;
  }

  /** Map output variables */
  private void mapOutput(NodeConfig config, Map<String, Object> output, WorkflowContext context) {
    if (config.getOutputMappings() != null) {
      for (NodeConfig.VariableMapping mapping : config.getOutputMappings()) {
        Object value = output.get(mapping.getSource());
        context.setVariable(mapping.getTarget(), value);
      }
    }
  }

  /** Check if should retry based on retry policy */
  private boolean shouldRetry(NodeConfig config, GraphNode node) {
    NodeConfig.RetryPolicy retryPolicy = config.getRetryPolicy();
    if (retryPolicy == null) {
      return false;
    }

    int executionCount = node.getExecutionCount();
    return executionCount < retryPolicy.getMaxAttempts();
  }

  @Override
  public boolean supports(GraphNode node) {
    return node.getType() == NodeType.SERVICE_TASK;
  }
}
