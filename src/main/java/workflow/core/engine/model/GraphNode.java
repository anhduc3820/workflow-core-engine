package workflow.core.engine.model;

import lombok.Data;

/**
 * Graph node wrapping NodeConfig with execution metadata
 */
@Data
public class GraphNode {

    private String id;
    private NodeType type;
    private String name;

    // Original configuration
    private NodeConfig config;

    // Execution metadata
    private NodeState state;
    private int executionCount;

    public GraphNode(NodeConfig config) {
        this.id = config.getId();
        this.type = config.getType();
        this.name = config.getName();
        this.config = config;
        this.state = NodeState.PENDING;
        this.executionCount = 0;
    }

    /**
     * Mark node as running
     */
    public void markRunning() {
        this.state = NodeState.RUNNING;
        this.executionCount++;
    }

    /**
     * Mark node as completed
     */
    public void markCompleted() {
        this.state = NodeState.COMPLETED;
    }

    /**
     * Mark node as failed
     */
    public void markFailed() {
        this.state = NodeState.FAILED;
    }

    /**
     * Mark node as skipped
     */
    public void markSkipped() {
        this.state = NodeState.SKIPPED;
    }

    /**
     * Reset node state
     */
    public void reset() {
        this.state = NodeState.PENDING;
        this.executionCount = 0;
    }
}

