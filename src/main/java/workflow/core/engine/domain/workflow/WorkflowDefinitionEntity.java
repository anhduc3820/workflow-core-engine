package workflow.core.engine.domain.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Domain Entity: Workflow Definition (v2) Represents a deployed workflow template that can be
 * instantiated Supports multi-tenancy and versioning
 */
@Entity
@Table(
    name = "workflow_definitions",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_workflow_definition_id_version",
            columnNames = {"workflow_id", "version", "tenant_id"}),
    indexes = {
      @Index(name = "idx_workflow_definition_tenant_id", columnList = "tenant_id"),
      @Index(name = "idx_workflow_definition_workflow_id", columnList = "workflow_id")
    })
@Data
@NoArgsConstructor
public class WorkflowDefinitionEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "workflow_id", nullable = false, length = 255)
  private String workflowId;

  @Column(name = "version", nullable = false, length = 50)
  private String version;

  @Column(name = "tenant_id", nullable = false, length = 100)
  private String tenantId = "default";

  @Column(name = "name", nullable = false, length = 500)
  private String name;

  @Column(name = "description", columnDefinition = "TEXT")
  private String description;

  @Column(name = "definition_json", nullable = false, columnDefinition = "TEXT")
  private String definitionJson;

  @Column(name = "previous_version", length = 50)
  private String previousVersion;

  @Column(name = "migration_strategy", length = 50)
  private String migrationStrategy = "NONE";

  @Column(name = "changelog", columnDefinition = "TEXT")
  private String changelog;

  @Column(name = "deployed_at", nullable = false)
  private Instant deployedAt;

  @Column(name = "deployed_by", length = 255)
  private String deployedBy;

  @Column(name = "is_active", nullable = false)
  private boolean active = true;

  @Column(name = "created_at")
  private Instant createdAt;

  @Column(name = "updated_at")
  private Instant updatedAt;

  @Version
  @Column(name = "version_lock")
  private Long versionLock;

  public WorkflowDefinitionEntity(
      String workflowId, String version, String name, String definitionJson) {
    this.workflowId = workflowId;
    this.version = version;
    this.name = name;
    this.definitionJson = definitionJson;
    this.tenantId = "default";
    this.deployedAt = Instant.now();
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
    this.active = true;
  }

  public WorkflowDefinitionEntity(
      String workflowId, String version, String name, String definitionJson, String tenantId) {
    this.workflowId = workflowId;
    this.version = version;
    this.name = name;
    this.definitionJson = definitionJson;
    this.tenantId = tenantId;
    this.deployedAt = Instant.now();
    this.createdAt = Instant.now();
    this.updatedAt = Instant.now();
    this.active = true;
  }

  @PreUpdate
  protected void onUpdate() {
    this.updatedAt = Instant.now();
  }
}
