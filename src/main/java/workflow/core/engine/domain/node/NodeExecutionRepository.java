package workflow.core.engine.domain.node;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository: Node Execution
 * Provides persistence operations for node execution records
 */
@Repository
public interface NodeExecutionRepository extends JpaRepository<NodeExecutionEntity, Long> {

    /**
     * Find all node executions for a workflow instance
     */
    @Query("SELECT n FROM NodeExecutionEntity n WHERE n.workflowInstance.executionId = :executionId ORDER BY n.executedAt ASC")
    List<NodeExecutionEntity> findByExecutionId(@Param("executionId") String executionId);

    /**
     * Find the latest execution of a specific node in a workflow instance
     */
    @Query("SELECT n FROM NodeExecutionEntity n WHERE n.workflowInstance.executionId = :executionId " +
           "AND n.nodeId = :nodeId ORDER BY n.attemptNumber DESC LIMIT 1")
    Optional<NodeExecutionEntity> findLatestByExecutionIdAndNodeId(
            @Param("executionId") String executionId,
            @Param("nodeId") String nodeId);

    /**
     * Check if a node has been executed successfully
     */
    @Query("SELECT CASE WHEN COUNT(n) > 0 THEN TRUE ELSE FALSE END FROM NodeExecutionEntity n " +
           "WHERE n.workflowInstance.executionId = :executionId AND n.nodeId = :nodeId AND n.state = 'COMPLETED'")
    boolean hasNodeBeenExecutedSuccessfully(
            @Param("executionId") String executionId,
            @Param("nodeId") String nodeId);

    /**
     * Count failed attempts for a node
     */
    @Query("SELECT COUNT(n) FROM NodeExecutionEntity n WHERE n.workflowInstance.executionId = :executionId " +
           "AND n.nodeId = :nodeId AND n.state = 'FAILED'")
    long countFailedAttempts(@Param("executionId") String executionId, @Param("nodeId") String nodeId);
}

