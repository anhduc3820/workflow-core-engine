package workflow.core.engine.parser;

/**
 * Exception thrown during workflow parsing
 */
public class WorkflowParseException extends RuntimeException {

    public WorkflowParseException(String message) {
        super(message);
    }

    public WorkflowParseException(String message, Throwable cause) {
        super(message, cause);
    }
}

