package workflow.core.engine.domain.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository: Workflow Instance (v2)
 * Provides persistence operations for workflow instances
 * Supports optimistic locking for concurrent updates
 * Multi-tenant aware
 */
@Repository
public interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstanceEntity, String> {

    /**
     * Find workflow instance with pessimistic write lock
     * Used for acquiring exclusive access during execution
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM WorkflowInstanceEntity w WHERE w.executionId = :executionId")
    Optional<WorkflowInstanceEntity> findByIdWithLock(@Param("executionId") String executionId);

    /**
     * Find all instances by workflow ID
     */
    List<WorkflowInstanceEntity> findByWorkflowIdOrderByCreatedAtDesc(String workflowId);

    /**
     * Find all instances by workflow ID and tenant (v2)
     */
    List<WorkflowInstanceEntity> findByWorkflowIdAndTenantIdOrderByCreatedAtDesc(String workflowId, String tenantId);

    /**
     * Find instances in specific state
     */
    List<WorkflowInstanceEntity> findByState(WorkflowState state);

    /**
     * Find instances in specific state for tenant (v2)
     */
    List<WorkflowInstanceEntity> findByStateAndTenantId(WorkflowState state, String tenantId);

    /**
     * Find running instances that may need recovery (locked but owner is stale)
     */
    @Query("SELECT w FROM WorkflowInstanceEntity w WHERE w.state = 'RUNNING' " +
           "AND w.lockOwner IS NOT NULL " +
           "AND w.lockAcquiredAt < :staleThreshold")
    List<WorkflowInstanceEntity> findStaleRunningInstances(@Param("staleThreshold") Instant staleThreshold);

    /**
     * Find stale running instances for tenant (v2)
     */
    @Query("SELECT w FROM WorkflowInstanceEntity w WHERE w.state = 'RUNNING' " +
           "AND w.tenantId = :tenantId " +
           "AND w.lockOwner IS NOT NULL " +
           "AND w.lockAcquiredAt < :staleThreshold")
    List<WorkflowInstanceEntity> findStaleRunningInstancesByTenant(@Param("tenantId") String tenantId,
                                                                    @Param("staleThreshold") Instant staleThreshold);

    /**
     * Count instances by state
     */
    long countByState(WorkflowState state);

    /**
     * Count instances by state and tenant (v2)
     */
    long countByStateAndTenantId(WorkflowState state, String tenantId);

    /**
     * Find instances created after a specific time
     */
    List<WorkflowInstanceEntity> findByCreatedAtAfter(Instant createdAt);
}

