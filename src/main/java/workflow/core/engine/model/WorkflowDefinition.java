package workflow.core.engine.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import lombok.Data;

/**
 * Root workflow definition matching frontend export schema Compatible with
 * example-backend-workflow.json
 *
 * <p>Supports both v1 (nodes/edges at root) and v2 (nodes/edges in execution) formats
 */
@Data
public class WorkflowDefinition {

  @JsonProperty("workflowId")
  private String workflowId;

  @JsonProperty("version")
  private String version;

  @JsonProperty("name")
  private String name;

  @JsonProperty("description")
  private String description;

  @JsonProperty("execution")
  private ExecutionDefinition execution;

  @JsonProperty("layout")
  private LayoutDefinition layout;

  @JsonProperty("metadata")
  private MetadataDefinition metadata;

  // V1 backward compatibility - nodes at root level
  @JsonProperty("nodes")
  private List<NodeConfig> nodes;

  // V1 backward compatibility - edges at root level
  @JsonProperty("edges")
  private List<EdgeConfig> edges;

  /**
   * Get execution definition with backward compatibility support. If execution is null but
   * nodes/edges are present at root (v1 format), creates an execution wrapper automatically.
   */
  public ExecutionDefinition getExecution() {
    if (execution == null && (nodes != null || edges != null)) {
      // V1 format - auto-wrap into execution object
      execution = new ExecutionDefinition();
      execution.setNodes(nodes);
      execution.setEdges(edges);
    }
    return execution;
  }

  /** Execution definition containing nodes and edges */
  @Data
  public static class ExecutionDefinition {
    @JsonProperty("nodes")
    private List<NodeConfig> nodes;

    @JsonProperty("edges")
    private List<EdgeConfig> edges;
  }

  /** Layout information (optional, for UI reconstruction) */
  @Data
  public static class LayoutDefinition {
    @JsonProperty("nodes")
    private List<NodePosition> nodes;

    @JsonProperty("viewport")
    private Viewport viewport;

    @Data
    public static class NodePosition {
      @JsonProperty("id")
      private String id;

      @JsonProperty("x")
      private double x;

      @JsonProperty("y")
      private double y;
    }

    @Data
    public static class Viewport {
      @JsonProperty("x")
      private double x;

      @JsonProperty("y")
      private double y;

      @JsonProperty("zoom")
      private double zoom;
    }
  }

  /** Workflow metadata */
  @Data
  public static class MetadataDefinition {
    @JsonProperty("schemaVersion")
    private String schemaVersion;

    @JsonProperty("createdAt")
    private String createdAt;

    @JsonProperty("updatedAt")
    private String updatedAt;

    @JsonProperty("author")
    private String author;

    @JsonProperty("tags")
    private List<String> tags;
  }
}
