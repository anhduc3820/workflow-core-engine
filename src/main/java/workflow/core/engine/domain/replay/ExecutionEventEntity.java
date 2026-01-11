package workflow.core.engine.domain.replay;

import jakarta.persistence.*;
import java.time.OffsetDateTime;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Financial-Grade Execution Event Entity Immutable event log for deterministic replay
 *
 * <p>Every execution step is recorded as an event Events are ordered by sequence number No events
 * can be modified or deleted (immutability guaranteed by domain logic)
 */
@Entity
@Table(
    name = "execution_events",
    indexes = {
      @Index(name = "idx_exec_event_execution_id", columnList = "execution_id"),
      @Index(name = "idx_exec_event_sequence", columnList = "execution_id,sequence_number"),
      @Index(name = "idx_exec_event_timestamp", columnList = "timestamp")
    })
@Data
@NoArgsConstructor
public class ExecutionEventEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "execution_id", nullable = false, length = 100)
  private String executionId;

  @Column(name = "sequence_number", nullable = false)
  private Long sequenceNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "event_type", nullable = false, length = 50)
  private ExecutionEventType eventType;

  @Column(name = "node_id", length = 100)
  private String nodeId;

  @Column(name = "node_type", length = 50)
  private String nodeType;

  @Column(name = "node_name", length = 500)
  private String nodeName;

  @Column(name = "timestamp", nullable = false)
  @CreationTimestamp
  private OffsetDateTime timestamp;

  @Column(name = "duration_ms")
  private Long durationMs;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false, length = 20)
  private ExecutionEventStatus status;

  @Column(name = "input_snapshot", columnDefinition = "TEXT")
  private String inputSnapshot;

  @Column(name = "output_snapshot", columnDefinition = "TEXT")
  private String outputSnapshot;

  @Column(name = "error_snapshot", columnDefinition = "TEXT")
  private String errorSnapshot;

  @Column(name = "variables_snapshot", columnDefinition = "TEXT")
  private String variablesSnapshot;

  @Column(name = "edge_taken", length = 100)
  private String edgeTaken;

  @Column(name = "decision_result", columnDefinition = "TEXT")
  private String decisionResult;

  @Column(name = "idempotency_key", nullable = false, length = 100, unique = true)
  private String idempotencyKey;

  @Column(name = "transaction_id", length = 100)
  private String transactionId;

  @Column(name = "compensated", nullable = false)
  private boolean compensated = false;

  @Column(name = "compensation_event_id")
  private Long compensationEventId;

  public ExecutionEventEntity(
      String executionId, Long sequenceNumber, ExecutionEventType eventType) {
    this.executionId = executionId;
    this.sequenceNumber = sequenceNumber;
    this.eventType = eventType;
    this.status = ExecutionEventStatus.PENDING;
    this.idempotencyKey = generateIdempotencyKey(executionId, sequenceNumber, eventType);
  }

  private String generateIdempotencyKey(
      String executionId, Long sequenceNumber, ExecutionEventType eventType) {
    return String.format("%s:%d:%s", executionId, sequenceNumber, eventType.name());
  }

  public void markCompleted(Long durationMs) {
    this.status = ExecutionEventStatus.COMPLETED;
    this.durationMs = durationMs;
  }

  public void markFailed(String errorSnapshot) {
    this.status = ExecutionEventStatus.FAILED;
    this.errorSnapshot = errorSnapshot;
  }

  public void markCompensated(Long compensationEventId) {
    this.compensated = true;
    this.compensationEventId = compensationEventId;
  }
}
