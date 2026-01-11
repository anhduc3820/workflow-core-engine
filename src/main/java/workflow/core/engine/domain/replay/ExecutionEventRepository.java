package workflow.core.engine.domain.replay;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Execution Event Repository Provides access to execution events for replay and audit */
@Repository
public interface ExecutionEventRepository extends JpaRepository<ExecutionEventEntity, Long> {

  /** Find all events for a workflow instance, ordered by sequence */
  List<ExecutionEventEntity> findByExecutionIdOrderBySequenceNumberAsc(String executionId);

  /** Find all events for a workflow instance within a sequence range */
  @Query(
      "SELECT e FROM ExecutionEventEntity e WHERE e.executionId = :executionId "
          + "AND e.sequenceNumber BETWEEN :startSeq AND :endSeq ORDER BY e.sequenceNumber ASC")
  List<ExecutionEventEntity> findByExecutionIdAndSequenceRange(
      @Param("executionId") String executionId,
      @Param("startSeq") Long startSequence,
      @Param("endSeq") Long endSequence);

  /** Find the last event for a workflow instance */
  @Query(
      "SELECT e FROM ExecutionEventEntity e WHERE e.executionId = :executionId "
          + "ORDER BY e.sequenceNumber DESC LIMIT 1")
  Optional<ExecutionEventEntity> findLastEventByExecutionId(
      @Param("executionId") String executionId);

  /** Find event by idempotency key (for idempotent execution) */
  Optional<ExecutionEventEntity> findByIdempotencyKey(String idempotencyKey);

  /** Count total events for execution */
  long countByExecutionId(String executionId);

  /** Find all failed events */
  List<ExecutionEventEntity> findByExecutionIdAndStatus(
      String executionId, ExecutionEventStatus status);

  /** Find all events for a specific node */
  List<ExecutionEventEntity> findByExecutionIdAndNodeIdOrderBySequenceNumberAsc(
      String executionId, String nodeId);

  /** Check if event already exists (idempotency check) */
  boolean existsByIdempotencyKey(String idempotencyKey);
}
