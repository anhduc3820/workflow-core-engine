package workflow.core.engine.executor;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import workflow.core.engine.model.WorkflowContext;

import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import java.util.Map;

/**
 * Evaluates conditions on edges
 * Supports simple JavaScript expressions
 */
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

        if (scriptEngine == null) {
            log.warn("No script engine available, cannot evaluate condition: {}", condition);
            return false;
        }

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
     * Convert object to boolean
     */
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
     * Simple condition evaluation without script engine
     * Supports basic comparisons: ==, !=, >, <, >=, <=
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

    /**
     * Compare values based on operator
     */
    private boolean compareValues(Object actual, String expected, String operator) {
        if (actual == null) {
            return "!=".equals(operator);
        }

        // Remove quotes from expected value
        expected = expected.replaceAll("^['\"]|['\"]$", "");

        String actualStr = actual.toString();

        switch (operator) {
            case "==":
                return actualStr.equals(expected) ||
                       actual.toString().equalsIgnoreCase(expected);
            case "!=":
                return !actualStr.equals(expected) &&
                       !actual.toString().equalsIgnoreCase(expected);
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

    /**
     * Compare numeric values
     */
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

