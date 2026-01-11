package workflow.core.engine.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Root workflow definition matching frontend export schema
 * Compatible with example-backend-workflow.json
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

    /**
     * Execution definition containing nodes and edges
     */
    @Data
    public static class ExecutionDefinition {
        @JsonProperty("nodes")
        private List<NodeConfig> nodes;

        @JsonProperty("edges")
        private List<EdgeConfig> edges;
    }

    /**
     * Layout information (optional, for UI reconstruction)
     */
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

    /**
     * Workflow metadata
     */
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

