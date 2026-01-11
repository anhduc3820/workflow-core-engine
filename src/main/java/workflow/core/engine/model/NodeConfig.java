package workflow.core.engine.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Node configuration matching frontend BackendNodeConfig
 */
@Data
public class NodeConfig {

    @JsonProperty("id")
    private String id;

    @JsonProperty("type")
    private NodeType type;

    @JsonProperty("name")
    private String name;

    // Event properties
    @JsonProperty("terminate")
    private Boolean terminate;

    // Gateway properties
    @JsonProperty("gatewayType")
    private GatewayType gatewayType;

    // Script Task properties
    @JsonProperty("scriptLanguage")
    private String scriptLanguage;

    @JsonProperty("scriptContent")
    private String scriptContent;

    // Service Task properties
    @JsonProperty("serviceName")
    private String serviceName;

    @JsonProperty("serviceMethod")
    private String serviceMethod;

    @JsonProperty("retryPolicy")
    private RetryPolicy retryPolicy;

    @JsonProperty("timeout")
    private Long timeout;

    // User Task properties
    @JsonProperty("taskName")
    private String taskName;

    @JsonProperty("actorId")
    private String actorId;

    @JsonProperty("groupId")
    private String groupId;

    @JsonProperty("priority")
    private Integer priority;

    @JsonProperty("skippable")
    private Boolean skippable;

    @JsonProperty("swimlane")
    private String swimlane;

    // Business Rule Task properties
    @JsonProperty("ruleExecutionType")
    private String ruleExecutionType;

    @JsonProperty("ruleFile")
    private String ruleFile;

    @JsonProperty("ruleflowGroup")
    private String ruleflowGroup;

    @JsonProperty("decisionModelRef")
    private String decisionModelRef;

    // Subprocess properties
    @JsonProperty("subprocessType")
    private String subprocessType;

    @JsonProperty("subprocessName")
    private String subprocessName;

    @JsonProperty("calledWorkflowId")
    private String calledWorkflowId;

    @JsonProperty("executionMode")
    private String executionMode;

    @JsonProperty("collectionExpression")
    private String collectionExpression;

    @JsonProperty("elementVariable")
    private String elementVariable;

    @JsonProperty("completionCondition")
    private String completionCondition;

    // Variable mappings
    @JsonProperty("inputMappings")
    private List<VariableMapping> inputMappings;

    @JsonProperty("outputMappings")
    private List<VariableMapping> outputMappings;

    /**
     * Variable mapping for input/output
     */
    @Data
    public static class VariableMapping {
        @JsonProperty("source")
        private String source;

        @JsonProperty("target")
        private String target;
    }

    /**
     * Retry policy for service tasks
     */
    @Data
    public static class RetryPolicy {
        @JsonProperty("maxAttempts")
        private Integer maxAttempts;

        @JsonProperty("backoffStrategy")
        private String backoffStrategy;

        @JsonProperty("delayMs")
        private Long delayMs;
    }
}

