package workflow.core.engine.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Edge configuration matching frontend BackendEdgeConfig
 */
@Data
public class EdgeConfig {

    @JsonProperty("id")
    private String id;

    @JsonProperty("source")
    private String source;

    @JsonProperty("target")
    private String target;

    @JsonProperty("pathType")
    private PathType pathType;

    @JsonProperty("condition")
    private String condition;

    @JsonProperty("priority")
    private Integer priority;

    @JsonProperty("name")
    private String name;
}

