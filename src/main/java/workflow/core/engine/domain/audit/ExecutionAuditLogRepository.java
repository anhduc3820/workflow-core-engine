package workflow.core.engine.domain.audit;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/** Repository: Execution Audit Log */
@Repository
public interface ExecutionAuditLogRepository extends JpaRepository<ExecutionAuditLogEntity, Long> {

  List<ExecutionAuditLogEntity> findByExecutionIdOrderByTimestampAsc(String executionId);

  List<ExecutionAuditLogEntity> findByTenantIdAndTimestampBetween(
      String tenantId, Instant start, Instant end);

  long countByTenantIdAndEventType(
      String tenantId, ExecutionAuditLogEntity.AuditEventType eventType);
}
