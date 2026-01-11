package workflow.core.engine.domain.version;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Domain Entity: Version Migration Log (v2)
 * Tracks workflow version migrations for audit and rollback purposes
 */
@Entity
@Table(name = "version_migration_log", indexes = {
        @Index(name = "idx_version_migration_workflow_id", columnList = "workflow_id")
})
@Data
@NoArgsConstructor
public class VersionMigrationLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "workflow_id", nullable = false, length = 255)
    private String workflowId;

    @Column(name = "from_version", nullable = false, length = 50)
    private String fromVersion;

    @Column(name = "to_version", nullable = false, length = 50)
    private String toVersion;

    @Column(name = "instances_migrated", nullable = false)
    private Integer instancesMigrated = 0;

    @Enumerated(EnumType.STRING)
    @Column(name = "migration_status", nullable = false, length = 50)
    private MigrationStatus migrationStatus;

    @Column(name = "migration_started_at", nullable = false)
    private Instant migrationStartedAt;

    @Column(name = "migration_completed_at")
    private Instant migrationCompletedAt;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at")
    private Instant createdAt;

    public VersionMigrationLogEntity(String workflowId, String fromVersion, String toVersion) {
        this.workflowId = workflowId;
        this.fromVersion = fromVersion;
        this.toVersion = toVersion;
        this.migrationStatus = MigrationStatus.IN_PROGRESS;
        this.migrationStartedAt = Instant.now();
        this.createdAt = Instant.now();
        this.instancesMigrated = 0;
    }

    public void complete() {
        this.migrationStatus = MigrationStatus.COMPLETED;
        this.migrationCompletedAt = Instant.now();
    }

    public void fail(String errorMessage) {
        this.migrationStatus = MigrationStatus.FAILED;
        this.migrationCompletedAt = Instant.now();
        this.errorMessage = errorMessage;
    }

    public enum MigrationStatus {
        IN_PROGRESS, COMPLETED, FAILED, ROLLED_BACK
    }
}

