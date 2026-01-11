package workflow.core.engine.model;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import lombok.Data;

/** Workflow execution context Maintains state and variables during workflow execution */
@Data
public class WorkflowContext {

  private String executionId;
  private String workflowId;
  private String version;

  // Current execution state
  private ExecutionState state;

  // Current node being executed
  private String currentNodeId;

  // Variables (thread-safe)
  private Map<String, Object> variables;

  // Node execution history
  private List<NodeExecutionRecord> executionHistory;

  // Timestamps
  private Instant startTime;
  private Instant endTime;

  // Error tracking
  private String errorMessage;
  private String errorNodeId;

  // Parallel execution tracking (for parallel gateways)
  private Map<String, Set<String>> activeParallelBranches;

  public WorkflowContext() {
    this.executionId = UUID.randomUUID().toString();
    this.state = ExecutionState.IDLE;
    this.variables = new ConcurrentHashMap<>();
    this.executionHistory = new ArrayList<>();
    this.activeParallelBranches = new ConcurrentHashMap<>();
  }

  public WorkflowContext(String workflowId, String version) {
    this();
    this.workflowId = workflowId;
    this.version = version;
  }

  /** Get variable with type casting */
  @SuppressWarnings("unchecked")
  public <T> T getVariable(String name, Class<T> type) {
    Object value = variables.get(name);
    if (value == null) {
      return null;
    }
    if (type.isInstance(value)) {
      return (T) value;
    }
    throw new ClassCastException("Variable '" + name + "' is not of type " + type.getName());
  }

  /** Set variable */
  public void setVariable(String name, Object value) {
    variables.put(name, value);
  }

  /** Add node execution record */
  public void addExecutionRecord(NodeExecutionRecord record) {
    executionHistory.add(record);
  }

  /** Record node execution */
  public void recordNodeExecution(String nodeId, NodeState nodeState, String message) {
    NodeExecutionRecord record = new NodeExecutionRecord();
    record.setNodeId(nodeId);
    record.setState(nodeState);
    record.setMessage(message);
    record.setTimestamp(Instant.now());
    record.setVariablesSnapshot(new HashMap<>(variables));
    addExecutionRecord(record);
  }

  /** Start parallel branch */
  public void startParallelBranch(String gatewayId, String branchId) {
    activeParallelBranches
        .computeIfAbsent(gatewayId, k -> ConcurrentHashMap.newKeySet())
        .add(branchId);
  }

  /** Complete parallel branch */
  public void completeParallelBranch(String gatewayId, String branchId) {
    Set<String> branches = activeParallelBranches.get(gatewayId);
    if (branches != null) {
      branches.remove(branchId);
    }
  }

  /** Check if all parallel branches completed */
  public boolean areAllParallelBranchesCompleted(String gatewayId) {
    Set<String> branches = activeParallelBranches.get(gatewayId);
    return branches == null || branches.isEmpty();
  }

  /** Node execution record */
  @Data
  public static class NodeExecutionRecord {
    private String nodeId;
    private NodeState state;
    private String message;
    private Instant timestamp;
    private Map<String, Object> variablesSnapshot;
  }
}
