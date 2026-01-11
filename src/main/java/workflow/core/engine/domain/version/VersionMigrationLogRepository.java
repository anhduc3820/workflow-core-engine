package workflow.core.engine.domain.version;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository: Version Migration Log */
@Repository
public interface VersionMigrationLogRepository
    extends JpaRepository<VersionMigrationLogEntity, Long> {

  List<VersionMigrationLogEntity> findByWorkflowIdOrderByMigrationStartedAtDesc(String workflowId);

  List<VersionMigrationLogEntity> findByMigrationStatus(
      VersionMigrationLogEntity.MigrationStatus status);
}
