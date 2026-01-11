package workflow.core.engine.executor;

import java.util.Map;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import workflow.core.engine.model.WorkflowContext;

/** Evaluates conditions on edges Supports simple JavaScript expressions */
@Slf4j
@Component
public class ConditionEvaluator {

  private final ScriptEngine scriptEngine;

  public ConditionEvaluator() {
    ScriptEngine scriptEngine1;
    ScriptEngineManager manager = new ScriptEngineManager();
    scriptEngine1 = manager.getEngineByName("JavaScript");

    if (scriptEngine1 == null) {
      // Fallback to Nashorn or GraalVM
      scriptEngine1 = manager.getEngineByName("Nashorn");
    }

    this.scriptEngine = scriptEngine1;
    if (this.scriptEngine == null) {
      log.warn("No JavaScript engine available. Condition evaluation will be limited.");
    }
  }

  /**
   * Evaluate condition expression
   *
   * @param condition The condition expression (e.g., "validationResult == true")
   * @param context The workflow context containing variables
   * @return true if condition evaluates to true, false otherwise
   */
  public boolean evaluate(String condition, WorkflowContext context) {
    if (condition == null || condition.trim().isEmpty()) {
      return true; // No condition means always true
    }

    return evaluate(condition, context.getVariables());
  }

  /**
   * Evaluate condition expression with variables map
   *
   * @param condition The condition expression
   * @param variables The variables map
   * @return true if condition evaluates to true, false otherwise
   */
  public boolean evaluate(String condition, Map<String, Object> variables) {
    if (condition == null || condition.trim().isEmpty()) {
      return true; // No condition means always true
    }

    // Try script engine first if available
    if (scriptEngine != null) {
      return evaluateWithScriptEngine(condition, variables);
    }

    // Fallback to simple expression evaluation
    log.debug("Using simple expression evaluator for: {}", condition);
    return evaluateSimpleExpression(condition, variables);
  }

  /** Evaluate using script engine */
  private boolean evaluateWithScriptEngine(String condition, Map<String, Object> variables) {
    try {
      // Bind variables to script engine
      for (Map.Entry<String, Object> entry : variables.entrySet()) {
        scriptEngine.put(entry.getKey(), entry.getValue());
      }

      // Evaluate expression
      Object result = scriptEngine.eval(condition);

      // Convert to boolean
      boolean boolResult = toBoolean(result);

      log.debug("Condition '{}' evaluated to: {}", condition, boolResult);

      return boolResult;
    } catch (ScriptException e) {
      log.error("Failed to evaluate condition: {}", condition, e);
      return false;
    }
  }

  /**
   * Simple expression evaluator for basic conditions when no script engine available Supports:
   * variable == value, variable != value, variable > value, etc.
   */
  private boolean evaluateSimpleExpression(String condition, Map<String, Object> variables) {
    condition = condition.trim();

    // Handle simple equality: "variableName == value"
    if (condition.contains("==")) {
      String[] parts = condition.split("==");
      if (parts.length == 2) {
        String varName = parts[0].trim();
        String expectedValue = parts[1].trim();

        Object actualValue = variables.get(varName);

        // Compare values
        if (expectedValue.equals("true")) {
          return Boolean.TRUE.equals(actualValue);
        } else if (expectedValue.equals("false")) {
          return Boolean.FALSE.equals(actualValue);
        } else if (expectedValue.equals("null")) {
          return actualValue == null;
        } else {
          // Try to parse as number
          try {
            double expected = Double.parseDouble(expectedValue);
            if (actualValue instanceof Number) {
              return ((Number) actualValue).doubleValue() == expected;
            }
          } catch (NumberFormatException e) {
            // Not a number, compare as string
            return expectedValue.equals(String.valueOf(actualValue));
          }
        }
      }
    }

    // Handle inequality: "variableName != value"
    if (condition.contains("!=")) {
      String[] parts = condition.split("!=");
      if (parts.length == 2) {
        String varName = parts[0].trim();
        String expectedValue = parts[1].trim();

        Object actualValue = variables.get(varName);

        if (expectedValue.equals("true")) {
          return !Boolean.TRUE.equals(actualValue);
        } else if (expectedValue.equals("false")) {
          return !Boolean.FALSE.equals(actualValue);
        } else if (expectedValue.equals("null")) {
          return actualValue != null;
        } else {
          return !expectedValue.equals(String.valueOf(actualValue));
        }
      }
    }

    // Handle greater than: "variableName > value"
    if (condition.contains(">") && !condition.contains(">=")) {
      String[] parts = condition.split(">");
      if (parts.length == 2) {
        String varName = parts[0].trim();
        String valueStr = parts[1].trim();

        Object actualValue = variables.get(varName);
        if (actualValue instanceof Number) {
          try {
            double threshold = Double.parseDouble(valueStr);
            return ((Number) actualValue).doubleValue() > threshold;
          } catch (NumberFormatException e) {
            log.warn("Cannot parse numeric value: {}", valueStr);
          }
        }
      }
    }

    // Handle less than: "variableName < value"
    if (condition.contains("<") && !condition.contains("<=")) {
      String[] parts = condition.split("<");
      if (parts.length == 2) {
        String varName = parts[0].trim();
        String valueStr = parts[1].trim();

        Object actualValue = variables.get(varName);
        if (actualValue instanceof Number) {
          try {
            double threshold = Double.parseDouble(valueStr);
            return ((Number) actualValue).doubleValue() < threshold;
          } catch (NumberFormatException e) {
            log.warn("Cannot parse numeric value: {}", valueStr);
          }
        }
      }
    }

    // If just a variable name, check if it's truthy
    if (!condition.contains(" ") && !condition.contains("=")) {
      Object value = variables.get(condition);
      return toBoolean(value);
    }

    log.warn("Cannot evaluate complex condition without script engine: {}", condition);
    return false;
  }

  /** Convert object to boolean */
  private boolean toBoolean(Object value) {
    if (value == null) {
      return false;
    }

    if (value instanceof Boolean) {
      return (Boolean) value;
    }

    if (value instanceof Number) {
      return ((Number) value).doubleValue() != 0;
    }

    if (value instanceof String) {
      String str = (String) value;
      return !str.isEmpty() && !str.equalsIgnoreCase("false");
    }

    return true;
  }

  /**
   * Simple condition evaluation without script engine Supports basic comparisons: ==, !=, >, <, >=,
   * <=
   */
  public boolean evaluateSimple(String condition, WorkflowContext context) {
    if (condition == null || condition.trim().isEmpty()) {
      return true;
    }

    condition = condition.trim();

    // Parse condition: "variable operator value"
    String[] operators = {"==", "!=", ">=", "<=", ">", "<"};

    for (String operator : operators) {
      if (condition.contains(operator)) {
        String[] parts = condition.split(operator, 2);
        if (parts.length == 2) {
          String varName = parts[0].trim();
          String expectedValue = parts[1].trim();

          Object actualValue = context.getVariables().get(varName);

          return compareValues(actualValue, expectedValue, operator);
        }
      }
    }

    // If no operator found, check if variable is truthy
    Object value = context.getVariables().get(condition);
    return toBoolean(value);
  }

  /** Compare values based on operator */
  private boolean compareValues(Object actual, String expected, String operator) {
    if (actual == null) {
      return "!=".equals(operator);
    }

    // Remove quotes from expected value
    expected = expected.replaceAll("^['\"]|['\"]$", "");

    String actualStr = actual.toString();

    switch (operator) {
      case "==":
        return actualStr.equals(expected) || actual.toString().equalsIgnoreCase(expected);
      case "!=":
        return !actualStr.equals(expected) && !actual.toString().equalsIgnoreCase(expected);
      case ">":
        return compareNumeric(actual, expected) > 0;
      case "<":
        return compareNumeric(actual, expected) < 0;
      case ">=":
        return compareNumeric(actual, expected) >= 0;
      case "<=":
        return compareNumeric(actual, expected) <= 0;
      default:
        return false;
    }
  }

  /** Compare numeric values */
  private int compareNumeric(Object actual, String expected) {
    try {
      double actualNum = Double.parseDouble(actual.toString());
      double expectedNum = Double.parseDouble(expected);
      return Double.compare(actualNum, expectedNum);
    } catch (NumberFormatException e) {
      return 0;
    }
  }
}
