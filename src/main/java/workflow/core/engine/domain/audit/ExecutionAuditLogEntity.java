package workflow.core.engine.domain.audit;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Domain Entity: Execution Audit Log (v2)
 * Records all significant events in workflow execution for compliance and debugging
 */
@Entity
@Table(name = "execution_audit_log", indexes = {
        @Index(name = "idx_audit_log_execution_id", columnList = "execution_id"),
        @Index(name = "idx_audit_log_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_audit_log_timestamp", columnList = "timestamp")
})
@Data
@NoArgsConstructor
public class ExecutionAuditLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "execution_id", nullable = false, length = 255)
    private String executionId;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 100)
    private AuditEventType eventType;

    @Column(name = "event_data", columnDefinition = "TEXT")
    private String eventData;

    @Column(name = "actor", length = 255)
    private String actor;

    @Column(name = "timestamp", nullable = false)
    private Instant timestamp;

    @Column(name = "created_at")
    private Instant createdAt;

    public ExecutionAuditLogEntity(String executionId, String tenantId, AuditEventType eventType, String eventData, String actor) {
        this.executionId = executionId;
        this.tenantId = tenantId;
        this.eventType = eventType;
        this.eventData = eventData;
        this.actor = actor;
        this.timestamp = Instant.now();
        this.createdAt = Instant.now();
    }

    public enum AuditEventType {
        WORKFLOW_STARTED,
        WORKFLOW_COMPLETED,
        WORKFLOW_FAILED,
        WORKFLOW_PAUSED,
        WORKFLOW_RESUMED,
        NODE_EXECUTED,
        NODE_FAILED,
        LOCK_ACQUIRED,
        LOCK_RELEASED,
        VARIABLE_UPDATED,
        GATEWAY_EVALUATED,
        RULE_EXECUTED
    }
}

