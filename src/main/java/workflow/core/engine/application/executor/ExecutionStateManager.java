package workflow.core.engine.application.executor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import workflow.core.engine.domain.audit.ExecutionAuditLogEntity;
import workflow.core.engine.domain.audit.ExecutionAuditLogRepository;
import workflow.core.engine.domain.node.NodeExecutionEntity;
import workflow.core.engine.domain.node.NodeExecutionRepository;
import workflow.core.engine.domain.workflow.WorkflowInstanceEntity;
import workflow.core.engine.domain.workflow.WorkflowInstanceRepository;
import workflow.core.engine.model.GraphNode;

/**
 * Infrastructure: Execution State Manager Manages workflow execution state with persistence
 * Supports HA by externalizing all state to database
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionStateManager {

  private final WorkflowInstanceRepository instanceRepository;
  private final NodeExecutionRepository nodeExecutionRepository;
  private final ObjectMapper objectMapper;
  private final ExecutionAuditLogRepository auditLogRepository;

  @PersistenceContext private EntityManager entityManager;

  private static final String INSTANCE_ID = generateInstanceId();

  /** Create a new workflow instance */
  @Transactional
  public WorkflowInstanceEntity createInstance(
      String workflowId, String version, Map<String, Object> variables) {
    String executionId = UUID.randomUUID().toString();
    WorkflowInstanceEntity instance = new WorkflowInstanceEntity(executionId, workflowId, version);
    instance.setVariablesJson(serializeVariables(variables));

    WorkflowInstanceEntity saved = instanceRepository.save(instance);

    // Flush to ensure instance is persisted before acquireLock is called
    // This is critical for HA scenarios where acquireLock uses REQUIRES_NEW
    entityManager.flush();

    log.info("Created workflow instance: {} for workflow: {}", executionId, workflowId);
    return saved;
  }

  /**
   * Acquire lock on workflow instance (for HA execution) Returns true if lock acquired, false if
   * already locked by another instance
   */
  @Transactional
  public boolean acquireLock(String executionId) {
    Optional<WorkflowInstanceEntity> optional = instanceRepository.findByIdWithLock(executionId);

    if (optional.isEmpty()) {
      log.warn("Workflow instance not found: {}", executionId);
      return false;
    }

    WorkflowInstanceEntity instance = optional.get();

    if (instance.tryAcquireLock(INSTANCE_ID)) {
      instanceRepository.save(instance);
      entityManager.flush(); // Ensure lock is persisted immediately
      log.debug("Lock acquired for instance: {} by {}", executionId, INSTANCE_ID);
      return true;
    }

    log.debug("Lock already held for instance: {} by {}", executionId, instance.getLockOwner());
    return false;
  }

  /** Release lock on workflow instance */
  @Transactional
  public void releaseLock(String executionId) {
    Optional<WorkflowInstanceEntity> optional = instanceRepository.findById(executionId);

    if (optional.isPresent()) {
      WorkflowInstanceEntity instance = optional.get();
      instance.releaseLock();
      instanceRepository.save(instance);
      log.debug("Lock released for instance: {}", executionId);
    }
  }

  /** Start workflow instance execution */
  @Transactional
  public void startExecution(String executionId) {
    WorkflowInstanceEntity instance = getInstance(executionId);
    instance.start();
    instanceRepository.save(instance);
    log.info("Started execution for instance: {}", executionId);
  }

  /** Update current node */
  @Transactional
  public void updateCurrentNode(String executionId, String nodeId) {
    WorkflowInstanceEntity instance = getInstance(executionId);
    instance.setCurrentNodeId(nodeId);
    instanceRepository.save(instance);
  }

  /** Update workflow variables */
  @Transactional
  public void updateVariables(String executionId, Map<String, Object> variables) {
    WorkflowInstanceEntity instance = getInstance(executionId);
    instance.setVariablesJson(serializeVariables(variables));
    instanceRepository.save(instance);
  }

  /** Record node execution start */
  @Transactional
  public NodeExecutionEntity recordNodeStart(
      String executionId, GraphNode node, Map<String, Object> inputVars) {
    WorkflowInstanceEntity instance = getInstance(executionId);

    NodeExecutionEntity execution =
        new NodeExecutionEntity(instance, node.getId(), node.getType().name());
    execution.setInputVariables(serializeVariables(inputVars));
    execution.setExecutedBy(INSTANCE_ID);
    execution.markStarted();

    NodeExecutionEntity saved = nodeExecutionRepository.save(execution);
    log.debug("Recorded node execution start: {} in instance: {}", node.getId(), executionId);
    return saved;
  }

  /** Record node execution completion */
  @Transactional
  public void recordNodeComplete(NodeExecutionEntity execution, Map<String, Object> outputVars) {
    execution.markCompleted(serializeVariables(outputVars));
    nodeExecutionRepository.save(execution);
    log.debug("Recorded node execution complete: {}", execution.getNodeId());
  }

  /** Record node execution failure */
  @Transactional
  public void recordNodeFailure(NodeExecutionEntity execution, String errorMessage) {
    execution.markFailed(errorMessage);
    nodeExecutionRepository.save(execution);
    log.debug("Recorded node execution failure: {}", execution.getNodeId());
  }

  /** Complete workflow instance */
  @Transactional
  public void completeWorkflow(String executionId) {
    WorkflowInstanceEntity instance = getInstance(executionId);
    instance.complete();
    instanceRepository.save(instance);
    log.info("Completed workflow instance: {}", executionId);
  }

  /** Fail workflow instance */
  @Transactional
  public void failWorkflow(String executionId, String errorMessage, String errorNodeId) {
    WorkflowInstanceEntity instance = getInstance(executionId);
    instance.fail(errorMessage, errorNodeId);
    instanceRepository.save(instance);
    log.info("Failed workflow instance: {} at node: {}", executionId, errorNodeId);
  }

  /** Pause workflow instance */
  @Transactional
  public void pauseWorkflow(String executionId) {
    WorkflowInstanceEntity instance = getInstance(executionId);
    instance.pause();
    instanceRepository.save(instance);
    log.info("Paused workflow instance: {}", executionId);
  }

  /** Get workflow instance */
  @Transactional(readOnly = true)
  public WorkflowInstanceEntity getInstance(String executionId) {
    return instanceRepository
        .findById(executionId)
        .orElseThrow(
            () -> new IllegalArgumentException("Workflow instance not found: " + executionId));
  }

  /** Get workflow variables */
  @Transactional(readOnly = true)
  public Map<String, Object> getVariables(String executionId) {
    WorkflowInstanceEntity instance = getInstance(executionId);
    return deserializeVariables(instance.getVariablesJson());
  }

  /** Check if node has been executed successfully (for idempotency) */
  @Transactional(readOnly = true)
  public boolean hasNodeBeenExecuted(String executionId, String nodeId) {
    return nodeExecutionRepository.hasNodeBeenExecutedSuccessfully(executionId, nodeId);
  }

  /** Get node execution history */
  @Transactional(readOnly = true)
  public java.util.List<NodeExecutionEntity> getExecutionHistory(String executionId) {
    return nodeExecutionRepository.findByExecutionId(executionId);
  }

  // Helper methods

  private String serializeVariables(Map<String, Object> variables) {
    if (variables == null || variables.isEmpty()) {
      return "{}";
    }
    try {
      return objectMapper.writeValueAsString(variables);
    } catch (JsonProcessingException e) {
      log.error("Failed to serialize variables", e);
      return "{}";
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> deserializeVariables(String json) {
    if (json == null || json.trim().isEmpty()) {
      return new HashMap<>();
    }
    try {
      return objectMapper.readValue(json, Map.class);
    } catch (JsonProcessingException e) {
      log.error("Failed to deserialize variables", e);
      return new HashMap<>();
    }
  }

  private static String generateInstanceId() {
    try {
      String hostname = InetAddress.getLocalHost().getHostName();
      return hostname + "-" + UUID.randomUUID().toString().substring(0, 8);
    } catch (UnknownHostException e) {
      return "unknown-" + UUID.randomUUID().toString().substring(0, 8);
    }
  }

  /** Helper method for audit logging (v2) */
  private void auditLog(
      String executionId,
      String tenantId,
      ExecutionAuditLogEntity.AuditEventType eventType,
      String eventData,
      String actor) {
    if (auditLogRepository != null) {
      try {
        ExecutionAuditLogEntity audit =
            new ExecutionAuditLogEntity(executionId, tenantId, eventType, eventData, actor);
        auditLogRepository.save(audit);
      } catch (Exception e) {
        log.error("Failed to write audit log", e);
      }
    }
  }
}
