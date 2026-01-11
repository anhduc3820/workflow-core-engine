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
 * Repository: Workflow Instance
 * Provides persistence operations for workflow instances
 * Supports optimistic locking for concurrent updates
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
     * Find instances in specific state
     */
    List<WorkflowInstanceEntity> findByState(WorkflowState state);

    /**
     * Find running instances that may need recovery (locked but owner is stale)
     */
    @Query("SELECT w FROM WorkflowInstanceEntity w WHERE w.state = 'RUNNING' " +
           "AND w.lockOwner IS NOT NULL " +
           "AND w.lockAcquiredAt < :staleThreshold")
    List<WorkflowInstanceEntity> findStaleRunningInstances(@Param("staleThreshold") Instant staleThreshold);

    /**
     * Count instances by state
     */
    long countByState(WorkflowState state);

    /**
     * Find instances created after a specific time
     */
    List<WorkflowInstanceEntity> findByCreatedAtAfter(Instant createdAt);
}

