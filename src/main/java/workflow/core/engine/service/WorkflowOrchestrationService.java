package workflow.core.engine.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import workflow.core.engine.executor.WorkflowExecutor;
import workflow.core.engine.model.WorkflowContext;
import workflow.core.engine.model.WorkflowGraph;
import workflow.core.engine.parser.WorkflowParser;
import workflow.core.engine.validator.ValidationResult;
import workflow.core.engine.validator.WorkflowValidator;

import java.util.Map;

/**
 * Main workflow orchestration service
 * Provides high-level workflow operations
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowOrchestrationService {

    private final WorkflowParser parser;
    private final WorkflowValidator validator;
    private final WorkflowExecutor executor;
    private final WorkflowRegistry registry;

    /**
     * Deploy workflow from JSON
     *
     * @param json Workflow JSON
     * @return Deployment result
     */
    public DeploymentResult deploy(String json) {
        log.info("Deploying workflow...");

        try {
            // Parse JSON
            WorkflowGraph graph = parser.parse(json);

            // Validate
            ValidationResult validationResult = validator.validate(graph);

            if (!validationResult.isValid()) {
                log.error("Workflow validation failed with {} errors",
                        validationResult.getErrors().size());
                return DeploymentResult.failed(validationResult);
            }

            // Register workflow
            registry.register(graph);

            log.info("Workflow deployed successfully: {} (v{})",
                    graph.getWorkflowId(), graph.getVersion());

            return DeploymentResult.success(graph.getWorkflowId(), graph.getVersion(), validationResult);

        } catch (Exception e) {
            log.error("Failed to deploy workflow", e);
            return DeploymentResult.error(e.getMessage());
        }
    }

    /**
     * Execute workflow by ID and version
     *
     * @param workflowId Workflow ID
     * @param version Version
     * @param variables Initial variables
     * @return Execution context
     */
    public WorkflowContext execute(String workflowId, String version, Map<String, Object> variables) {
        log.info("Executing workflow: {} (v{})", workflowId, version);

        // Get workflow from registry
        WorkflowGraph graph = registry.getWorkflow(workflowId, version);

        if (graph == null) {
            throw new IllegalArgumentException(
                    "Workflow not found: " + workflowId + " (v" + version + ")");
        }

        // Execute
        return executor.execute(graph, variables);
    }

    /**
     * Execute latest version of workflow
     */
    public WorkflowContext executeLatest(String workflowId, Map<String, Object> variables) {
        log.info("Executing latest workflow: {}", workflowId);

        WorkflowGraph graph = registry.getLatestWorkflow(workflowId);

        if (graph == null) {
            throw new IllegalArgumentException("Workflow not found: " + workflowId);
        }

        log.info("Executing version: {}", graph.getVersion());
        return executor.execute(graph, variables);
    }

    /**
     * Validate workflow JSON without deploying
     */
    public ValidationResult validate(String json) {
        try {
            WorkflowGraph graph = parser.parse(json);
            return validator.validate(graph);
        } catch (Exception e) {
            ValidationResult result = new ValidationResult();
            result.addError("PARSE_ERROR", "Failed to parse workflow: " + e.getMessage());
            return result;
        }
    }

    /**
     * Undeploy workflow
     */
    public void undeploy(String workflowId, String version) {
        registry.unregister(workflowId, version);
        log.info("Workflow undeployed: {} (v{})", workflowId, version);
    }

    /**
     * Deployment result
     */
    public static class DeploymentResult {
        private boolean success;
        private String workflowId;
        private String version;
        private ValidationResult validationResult;
        private String errorMessage;

        public static DeploymentResult success(String workflowId, String version,
                                               ValidationResult validationResult) {
            DeploymentResult result = new DeploymentResult();
            result.success = true;
            result.workflowId = workflowId;
            result.version = version;
            result.validationResult = validationResult;
            return result;
        }

        public static DeploymentResult failed(ValidationResult validationResult) {
            DeploymentResult result = new DeploymentResult();
            result.success = false;
            result.validationResult = validationResult;
            return result;
        }

        public static DeploymentResult error(String errorMessage) {
            DeploymentResult result = new DeploymentResult();
            result.success = false;
            result.errorMessage = errorMessage;
            return result;
        }

        // Getters
        public boolean isSuccess() { return success; }
        public String getWorkflowId() { return workflowId; }
        public String getVersion() { return version; }
        public ValidationResult getValidationResult() { return validationResult; }
        public String getErrorMessage() { return errorMessage; }
    }
}

