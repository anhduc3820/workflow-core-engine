package workflow.core.engine.api;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import workflow.core.engine.model.WorkflowContext;
import workflow.core.engine.service.WorkflowOrchestrationService;
import workflow.core.engine.validator.ValidationResult;

import java.util.Map;

/**
 * REST API for workflow operations
 */
@Slf4j
@RestController
@RequestMapping("/api/workflows")
@RequiredArgsConstructor
public class WorkflowController {

    private final WorkflowOrchestrationService orchestrationService;

    /**
     * Deploy workflow from JSON
     * POST /api/workflows/deploy
     */
    @PostMapping("/deploy")
    public ResponseEntity<DeployResponse> deploy(@RequestBody String workflowJson) {
        log.info("Received workflow deployment request");

        try {
            WorkflowOrchestrationService.DeploymentResult result =
                    orchestrationService.deploy(workflowJson);

            if (result.isSuccess()) {
                DeployResponse response = new DeployResponse();
                response.setSuccess(true);
                response.setWorkflowId(result.getWorkflowId());
                response.setVersion(result.getVersion());
                response.setMessage("Workflow deployed successfully");
                response.setValidationResult(result.getValidationResult());

                return ResponseEntity.ok(response);
            } else {
                DeployResponse response = new DeployResponse();
                response.setSuccess(false);
                response.setMessage("Workflow validation failed");
                response.setValidationResult(result.getValidationResult());
                response.setError(result.getErrorMessage());

                return ResponseEntity.badRequest().body(response);
            }

        } catch (Exception e) {
            log.error("Failed to deploy workflow", e);

            DeployResponse response = new DeployResponse();
            response.setSuccess(false);
            response.setMessage("Deployment failed");
            response.setError(e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Validate workflow JSON without deploying
     * POST /api/workflows/validate
     */
    @PostMapping("/validate")
    public ResponseEntity<ValidationResponse> validate(@RequestBody String workflowJson) {
        log.info("Received workflow validation request");

        try {
            ValidationResult result = orchestrationService.validate(workflowJson);

            ValidationResponse response = new ValidationResponse();
            response.setValid(result.isValid());
            response.setValidationResult(result);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to validate workflow", e);

            ValidationResponse response = new ValidationResponse();
            response.setValid(false);
            response.setError(e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Execute workflow
     * POST /api/workflows/{workflowId}/execute
     */
    @PostMapping("/{workflowId}/execute")
    public ResponseEntity<ExecutionResponse> execute(
            @PathVariable String workflowId,
            @RequestParam(required = false) String version,
            @RequestBody(required = false) Map<String, Object> variables) {

        log.info("Received workflow execution request: {} (v{})", workflowId, version);

        try {
            WorkflowContext context;

            if (version != null) {
                context = orchestrationService.execute(workflowId, version, variables);
            } else {
                context = orchestrationService.executeLatest(workflowId, variables);
            }

            ExecutionResponse response = new ExecutionResponse();
            response.setSuccess(context.getState() == workflow.core.engine.model.ExecutionState.COMPLETED);
            response.setExecutionId(context.getExecutionId());
            response.setWorkflowId(context.getWorkflowId());
            response.setVersion(context.getVersion());
            response.setState(context.getState().toString());
            response.setVariables(context.getVariables());
            response.setExecutionHistory(context.getExecutionHistory());
            response.setErrorMessage(context.getErrorMessage());

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.error("Workflow not found: {}", workflowId, e);

            ExecutionResponse response = new ExecutionResponse();
            response.setSuccess(false);
            response.setErrorMessage("Workflow not found: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);

        } catch (Exception e) {
            log.error("Failed to execute workflow: {}", workflowId, e);

            ExecutionResponse response = new ExecutionResponse();
            response.setSuccess(false);
            response.setErrorMessage("Execution failed: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    /**
     * Undeploy workflow
     * DELETE /api/workflows/{workflowId}
     */
    @DeleteMapping("/{workflowId}")
    public ResponseEntity<UndeployResponse> undeploy(
            @PathVariable String workflowId,
            @RequestParam String version) {

        log.info("Received workflow undeploy request: {} (v{})", workflowId, version);

        try {
            orchestrationService.undeploy(workflowId, version);

            UndeployResponse response = new UndeployResponse();
            response.setSuccess(true);
            response.setMessage("Workflow undeployed successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to undeploy workflow: {}", workflowId, e);

            UndeployResponse response = new UndeployResponse();
            response.setSuccess(false);
            response.setMessage("Undeploy failed: " + e.getMessage());

            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    // Response DTOs

    @Data
    public static class DeployResponse {
        private boolean success;
        private String workflowId;
        private String version;
        private String message;
        private ValidationResult validationResult;
        private String error;
    }

    @Data
    public static class ValidationResponse {
        private boolean valid;
        private ValidationResult validationResult;
        private String error;
    }

    @Data
    public static class ExecutionResponse {
        private boolean success;
        private String executionId;
        private String workflowId;
        private String version;
        private String state;
        private Map<String, Object> variables;
        private Object executionHistory;
        private String errorMessage;
    }

    @Data
    public static class UndeployResponse {
        private boolean success;
        private String message;
    }
}

