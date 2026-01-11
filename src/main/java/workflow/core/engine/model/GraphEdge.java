package workflow.core.engine.model;

import lombok.Data;

/** Graph edge wrapping EdgeConfig with execution metadata */
@Data
public class GraphEdge {

  private String id;
  private String source;
  private String target;
  private PathType pathType;
  private String condition;
  private Integer priority;
  private String name;

  // Original configuration
  private EdgeConfig config;

  // Execution metadata
  private boolean traversed;

  public GraphEdge(EdgeConfig config) {
    this.id = config.getId();
    this.source = config.getSource();
    this.target = config.getTarget();
    this.pathType = config.getPathType();
    this.condition = config.getCondition();
    this.priority = config.getPriority() != null ? config.getPriority() : 0;
    this.name = config.getName();
    this.config = config;
    this.traversed = false;
  }

  /** Mark edge as traversed */
  public void markTraversed() {
    this.traversed = true;
  }

  /** Reset edge state */
  public void reset() {
    this.traversed = false;
  }

  /** Check if edge has condition */
  public boolean hasCondition() {
    return condition != null && !condition.trim().isEmpty();
  }
}
