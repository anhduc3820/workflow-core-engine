package workflow.core.engine.domain.node;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import workflow.core.engine.domain.workflow.WorkflowInstanceEntity;

import java.time.Instant;

/**
 * Domain Entity: Node Execution Record
 * Tracks individual node execution within a workflow instance
 * Supports idempotency and replay
 */
@Entity
@Table(name = "node_executions", indexes = {
        @Index(name = "idx_execution_id", columnList = "execution_id"),
        @Index(name = "idx_node_id", columnList = "node_id"),
        @Index(name = "idx_state", columnList = "state")
})
@Data
@NoArgsConstructor
public class NodeExecutionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "execution_id", nullable = false)
    private WorkflowInstanceEntity workflowInstance;

    @Column(name = "node_id", nullable = false, length = 100)
    private String nodeId;

    @Column(name = "node_type", nullable = false, length = 50)
    private String nodeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 20)
    private NodeExecutionState state;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber = 1;

    @Column(name = "executed_at", nullable = false)
    private Instant executedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "input_variables", columnDefinition = "TEXT")
    private String inputVariables;

    @Column(name = "output_variables", columnDefinition = "TEXT")
    private String outputVariables;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "executed_by", length = 100)
    private String executedBy;

    public NodeExecutionEntity(WorkflowInstanceEntity workflowInstance, String nodeId, String nodeType) {
        this.workflowInstance = workflowInstance;
        this.nodeId = nodeId;
        this.nodeType = nodeType;
        this.state = NodeExecutionState.PENDING;
        this.executedAt = Instant.now();
    }

    public void markStarted() {
        this.state = NodeExecutionState.RUNNING;
        this.executedAt = Instant.now();
    }

    public void markCompleted(String outputVariables) {
        this.state = NodeExecutionState.COMPLETED;
        this.completedAt = Instant.now();
        this.outputVariables = outputVariables;
        if (executedAt != null && completedAt != null) {
            this.durationMs = completedAt.toEpochMilli() - executedAt.toEpochMilli();
        }
    }

    public void markFailed(String errorMessage) {
        this.state = NodeExecutionState.FAILED;
        this.completedAt = Instant.now();
        this.errorMessage = errorMessage;
        if (executedAt != null && completedAt != null) {
            this.durationMs = completedAt.toEpochMilli() - executedAt.toEpochMilli();
        }
    }

    public void incrementAttempt() {
        this.attemptNumber++;
    }
}

