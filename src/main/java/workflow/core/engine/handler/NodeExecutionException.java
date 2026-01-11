package workflow.core.engine.handler;

/** Exception thrown during node execution */
public class NodeExecutionException extends RuntimeException {

  private final String nodeId;

  public NodeExecutionException(String nodeId, String message) {
    super(message);
    this.nodeId = nodeId;
  }

  public NodeExecutionException(String nodeId, String message, Throwable cause) {
    super(message, cause);
    this.nodeId = nodeId;
  }

  public String getNodeId() {
    return nodeId;
  }
}
