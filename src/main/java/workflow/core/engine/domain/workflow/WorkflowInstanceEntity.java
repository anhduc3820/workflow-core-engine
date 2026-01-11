package workflow.core.engine.domain.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.NoArgsConstructor;
import workflow.core.engine.domain.node.NodeExecutionEntity;

/**
 * Domain Entity: Workflow Instance Represents a running or completed workflow instance Stateful
 * entity that can be persisted and resumed across different nodes
 */
@Entity
@Table(
    name = "workflow_instances",
    indexes = {
      @Index(name = "idx_workflow_id", columnList = "workflow_id"),
      @Index(name = "idx_state", columnList = "state"),
      @Index(name = "idx_tenant_id", columnList = "tenant_id"),
      @Index(name = "idx_lock_owner", columnList = "lock_owner"),
      @Index(name = "idx_created_at", columnList = "created_at")
    })
@Data
@NoArgsConstructor
public class WorkflowInstanceEntity {

  @Id
  @Column(name = "execution_id", nullable = false, length = 100)
  private String executionId;

  @Column(name = "workflow_id", nullable = false, length = 100)
  private String workflowId;

  @Column(name = "version", nullable = false, length = 20)
  private String version;

  @Column(name = "tenant_id", nullable = false, length = 100)
  private String tenantId = "default";

  @Enumerated(EnumType.STRING)
  @Column(name = "state", nullable = false, length = 20)
  private WorkflowState state;

  @Column(name = "current_node_id", length = 100)
  private String currentNodeId;

  @Column(name = "variables_json", columnDefinition = "TEXT")
  private String variablesJson;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "completed_at")
  private Instant completedAt;

  @Column(name = "error_message", columnDefinition = "TEXT")
  private String errorMessage;

  @Column(name = "error_node_id", length = 100)
  private String errorNodeId;

  @Column(name = "retry_count", nullable = false)
  private Integer retryCount = 0;

  @Column(name = "lock_owner", length = 100)
  private String lockOwner;

  @Column(name = "lock_acquired_at")
  private Instant lockAcquiredAt;

  @Version
  @Column(name = "version_lock")
  private Long versionLock;

  @OneToMany(
      mappedBy = "workflowInstance",
      cascade = CascadeType.ALL,
      orphanRemoval = true,
      fetch = FetchType.LAZY)
  @OrderBy("executedAt ASC")
  private List<NodeExecutionEntity> nodeExecutions = new ArrayList<>();

  public WorkflowInstanceEntity(String executionId, String workflowId, String version) {
    this.executionId = executionId;
    this.workflowId = workflowId;
    this.version = version;
    this.tenantId = "default";
    this.state = WorkflowState.PENDING;
    this.createdAt = Instant.now();
    this.retryCount = 0;
  }

  public WorkflowInstanceEntity(
      String executionId, String workflowId, String version, String tenantId) {
    this.executionId = executionId;
    this.workflowId = workflowId;
    this.version = version;
    this.tenantId = tenantId;
    this.state = WorkflowState.PENDING;
    this.createdAt = Instant.now();
    this.retryCount = 0;
  }

  public void start() {
    this.state = WorkflowState.RUNNING;
    this.startedAt = Instant.now();
  }

  public void complete() {
    this.state = WorkflowState.COMPLETED;
    this.completedAt = Instant.now();
    this.lockOwner = null;
    this.lockAcquiredAt = null;
  }

  public void fail(String errorMessage, String errorNodeId) {
    this.state = WorkflowState.FAILED;
    this.completedAt = Instant.now();
    this.errorMessage = errorMessage;
    this.errorNodeId = errorNodeId;
    this.lockOwner = null;
    this.lockAcquiredAt = null;
  }

  public void pause() {
    this.state = WorkflowState.PAUSED;
    this.lockOwner = null;
    this.lockAcquiredAt = null;
  }

  public void markCancelled() {
    this.state = WorkflowState.CANCELLED;
    this.completedAt = Instant.now();
    this.lockOwner = null;
    this.lockAcquiredAt = null;
  }

  public void updateVariables(String variablesJson) {
    this.variablesJson = variablesJson;
  }

  public String getCurrentVariables() {
    return this.variablesJson;
  }

  public boolean tryAcquireLock(String owner) {
    if (this.lockOwner == null || isLockExpired()) {
      this.lockOwner = owner;
      this.lockAcquiredAt = Instant.now();
      return true;
    }
    return false;
  }

  public void releaseLock() {
    this.lockOwner = null;
    this.lockAcquiredAt = null;
  }

  public void incrementRetryCount() {
    this.retryCount++;
  }

  private boolean isLockExpired() {
    if (lockAcquiredAt == null) {
      return true;
    }
    // Lock expires after 5 minutes
    return lockAcquiredAt.plusSeconds(300).isBefore(Instant.now());
  }

  public boolean isTerminalState() {
    return state == WorkflowState.COMPLETED
        || state == WorkflowState.FAILED
        || state == WorkflowState.CANCELLED;
  }
}
