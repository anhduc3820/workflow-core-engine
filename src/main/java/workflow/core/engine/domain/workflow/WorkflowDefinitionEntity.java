package workflow.core.engine.domain.workflow;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Domain Entity: Workflow Definition
 * Represents a deployed workflow template that can be instantiated
 */
@Entity
@Table(name = "workflow_definitions")
@Data
@NoArgsConstructor
public class WorkflowDefinitionEntity {

    @Id
    @Column(name = "workflow_id", nullable = false, length = 100)
    private String workflowId;

    @Column(name = "version", nullable = false, length = 20)
    private String version;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "definition_json", nullable = false, columnDefinition = "TEXT")
    private String definitionJson;

    @Column(name = "deployed_at", nullable = false)
    private Instant deployedAt;

    @Column(name = "deployed_by", length = 100)
    private String deployedBy;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Version
    @Column(name = "version_lock")
    private Long versionLock;

    public WorkflowDefinitionEntity(String workflowId, String version, String name, String definitionJson) {
        this.workflowId = workflowId;
        this.version = version;
        this.name = name;
        this.definitionJson = definitionJson;
        this.deployedAt = Instant.now();
        this.active = true;
    }
}

