package workflow.core.engine.api.rest;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import workflow.core.engine.application.executor.ExecutionStateManager;
import workflow.core.engine.application.executor.StatelessWorkflowExecutor;
import workflow.core.engine.application.workflow.DeployWorkflowUseCase;
import workflow.core.engine.domain.node.NodeExecutionEntity;
import workflow.core.engine.domain.workflow.WorkflowDefinitionEntity;
import workflow.core.engine.domain.workflow.WorkflowInstanceEntity;
import workflow.core.engine.domain.workflow.WorkflowState;
import workflow.core.engine.model.WorkflowGraph;
import workflow.core.engine.parser.WorkflowParser;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * REST API: Workflow Operations (V2 - HA Ready)
 * Clean Architecture - Uses application layer use cases
 */
@Slf4j
@RestController
@RequestMapping("/api/v2/workflows")
@RequiredArgsConstructor
public class WorkflowControllerV2 {

    private final DeployWorkflowUseCase deployWorkflowUseCase;
    private final StatelessWorkflowExecutor workflowExecutor;
    private final ExecutionStateManager stateManager;
    private final WorkflowParser workflowParser;

    /**
     * Deploy workflow definition
     * POST /api/v2/workflows/deploy
     */
    @PostMapping("/deploy")
    public ResponseEntity<DeployResponseDTO> deploy(@RequestBody String workflowJson) {
        log.info("Deploying workflow");

        try {
            WorkflowDefinitionEntity deployed = deployWorkflowUseCase.execute(workflowJson);

            DeployResponseDTO response = new DeployResponseDTO();
            response.setSuccess(true);
            response.setWorkflowId(deployed.getWorkflowId());
            response.setVersion(deployed.getVersion());
            response.setMessage("Workflow deployed successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Deployment failed", e);

            DeployResponseDTO response = new DeployResponseDTO();
            response.setSuccess(false);
            response.setMessage("Deployment failed");
            response.setError(e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get all active workflows
     * GET /api/v2/workflows
     */
    @GetMapping
    public ResponseEntity<List<WorkflowSummaryDTO>> getAllWorkflows() {
        log.info("Fetching all active workflows");

        List<WorkflowDefinitionEntity> workflows = deployWorkflowUseCase.getAllActive();

        List<WorkflowSummaryDTO> response = workflows.stream()
                .map(this::toSummaryDTO)
                .collect(Collectors.toList());

        return ResponseEntity.ok(response);
    }

    /**
     * Get workflow definition by ID
     * GET /api/v2/workflows/{workflowId}
     */
    @GetMapping("/{workflowId}")
    public ResponseEntity<WorkflowDefinitionDTO> getWorkflow(@PathVariable String workflowId) {
        log.info("Fetching workflow: {}", workflowId);

        return deployWorkflowUseCase.getById(workflowId)
                .map(this::toDefinitionDTO)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Execute workflow (async)
     * POST /api/v2/workflows/{workflowId}/execute
     */
    @PostMapping("/{workflowId}/execute")
    public ResponseEntity<ExecutionResponseDTO> executeWorkflow(
            @PathVariable String workflowId,
            @RequestBody(required = false) Map<String, Object> variables) {

        log.info("Executing workflow: {}", workflowId);

        try {
            // Get workflow definition
            WorkflowDefinitionEntity definition = deployWorkflowUseCase.getById(workflowId)
                    .orElseThrow(() -> new IllegalArgumentException("Workflow not found: " + workflowId));

            // Parse workflow graph
            WorkflowGraph graph = workflowParser.parseToGraph(definition.getDefinitionJson());

            // Execute asynchronously
            String executionId = workflowExecutor.executeAsync(graph, variables);

            ExecutionResponseDTO response = new ExecutionResponseDTO();
            response.setSuccess(true);
            response.setExecutionId(executionId);
            response.setWorkflowId(workflowId);
            response.setState(WorkflowState.PENDING.name());
            response.setMessage("Workflow execution started");

            return ResponseEntity.accepted().body(response);

        } catch (Exception e) {
            log.error("Execution failed for workflow: {}", workflowId, e);

            ExecutionResponseDTO response = new ExecutionResponseDTO();
            response.setSuccess(false);
            response.setWorkflowId(workflowId);
            response.setMessage("Execution failed");
            response.setError(e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Get execution status
     * GET /api/v2/workflows/executions/{executionId}
     */
    @GetMapping("/executions/{executionId}")
    public ResponseEntity<ExecutionStatusDTO> getExecutionStatus(@PathVariable String executionId) {
        log.info("Fetching execution status: {}", executionId);

        try {
            WorkflowInstanceEntity instance = stateManager.getInstance(executionId);
            List<NodeExecutionEntity> history = stateManager.getExecutionHistory(executionId);

            ExecutionStatusDTO response = new ExecutionStatusDTO();
            response.setExecutionId(instance.getExecutionId());
            response.setWorkflowId(instance.getWorkflowId());
            response.setVersion(instance.getVersion());
            response.setState(instance.getState().name());
            response.setCurrentNodeId(instance.getCurrentNodeId());
            response.setCreatedAt(instance.getCreatedAt().toString());
            response.setStartedAt(instance.getStartedAt() != null ? instance.getStartedAt().toString() : null);
            response.setCompletedAt(instance.getCompletedAt() != null ? instance.getCompletedAt().toString() : null);
            response.setErrorMessage(instance.getErrorMessage());
            response.setErrorNodeId(instance.getErrorNodeId());
            response.setExecutionHistory(history.stream()
                    .map(this::toNodeExecutionDTO)
                    .collect(Collectors.toList()));

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Execution not found: {}", executionId);
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Resume paused execution
     * POST /api/v2/workflows/executions/{executionId}/resume
     */
    @PostMapping("/executions/{executionId}/resume")
    public ResponseEntity<ExecutionResponseDTO> resumeExecution(@PathVariable String executionId) {
        log.info("Resuming execution: {}", executionId);

        try {
            WorkflowInstanceEntity instance = stateManager.getInstance(executionId);

            if (instance.getState() != WorkflowState.PAUSED) {
                ExecutionResponseDTO response = new ExecutionResponseDTO();
                response.setSuccess(false);
                response.setExecutionId(executionId);
                response.setMessage("Cannot resume - workflow is not paused");
                return ResponseEntity.badRequest().body(response);
            }

            // Parse workflow graph
            WorkflowDefinitionEntity definition = deployWorkflowUseCase.getById(instance.getWorkflowId())
                    .orElseThrow(() -> new IllegalArgumentException("Workflow definition not found"));

            WorkflowGraph graph = workflowParser.parseToGraph(definition.getDefinitionJson());

            // Resume execution
            workflowExecutor.resumeExecution(executionId, graph);

            ExecutionResponseDTO response = new ExecutionResponseDTO();
            response.setSuccess(true);
            response.setExecutionId(executionId);
            response.setWorkflowId(instance.getWorkflowId());
            response.setState(WorkflowState.RUNNING.name());
            response.setMessage("Workflow execution resumed");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Resume failed for execution: {}", executionId, e);

            ExecutionResponseDTO response = new ExecutionResponseDTO();
            response.setSuccess(false);
            response.setExecutionId(executionId);
            response.setMessage("Resume failed");
            response.setError(e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Undeploy workflow
     * DELETE /api/v2/workflows/{workflowId}
     */
    @DeleteMapping("/{workflowId}")
    public ResponseEntity<UndeployResponseDTO> undeploy(@PathVariable String workflowId) {
        log.info("Undeploying workflow: {}", workflowId);

        try {
            deployWorkflowUseCase.undeploy(workflowId);

            UndeployResponseDTO response = new UndeployResponseDTO();
            response.setSuccess(true);
            response.setWorkflowId(workflowId);
            response.setMessage("Workflow undeployed successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Undeploy failed for workflow: {}", workflowId, e);

            UndeployResponseDTO response = new UndeployResponseDTO();
            response.setSuccess(false);
            response.setWorkflowId(workflowId);
            response.setMessage("Undeploy failed");
            response.setError(e.getMessage());

            return ResponseEntity.badRequest().body(response);
        }
    }

    // DTO Mappers

    private WorkflowSummaryDTO toSummaryDTO(WorkflowDefinitionEntity entity) {
        WorkflowSummaryDTO dto = new WorkflowSummaryDTO();
        dto.setWorkflowId(entity.getWorkflowId());
        dto.setVersion(entity.getVersion());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setDeployedAt(entity.getDeployedAt().toString());
        dto.setActive(entity.isActive());
        return dto;
    }

    private WorkflowDefinitionDTO toDefinitionDTO(WorkflowDefinitionEntity entity) {
        WorkflowDefinitionDTO dto = new WorkflowDefinitionDTO();
        dto.setWorkflowId(entity.getWorkflowId());
        dto.setVersion(entity.getVersion());
        dto.setName(entity.getName());
        dto.setDescription(entity.getDescription());
        dto.setDefinitionJson(entity.getDefinitionJson());
        dto.setDeployedAt(entity.getDeployedAt().toString());
        dto.setActive(entity.isActive());
        return dto;
    }

    private NodeExecutionDTO toNodeExecutionDTO(NodeExecutionEntity entity) {
        NodeExecutionDTO dto = new NodeExecutionDTO();
        dto.setNodeId(entity.getNodeId());
        dto.setNodeType(entity.getNodeType());
        dto.setState(entity.getState().name());
        dto.setAttemptNumber(entity.getAttemptNumber());
        dto.setExecutedAt(entity.getExecutedAt().toString());
        dto.setCompletedAt(entity.getCompletedAt() != null ? entity.getCompletedAt().toString() : null);
        dto.setDurationMs(entity.getDurationMs());
        dto.setErrorMessage(entity.getErrorMessage());
        return dto;
    }

    // DTOs

    @Data
    public static class DeployResponseDTO {
        private boolean success;
        private String workflowId;
        private String version;
        private String message;
        private String error;
    }

    @Data
    public static class ExecutionResponseDTO {
        private boolean success;
        private String executionId;
        private String workflowId;
        private String state;
        private String message;
        private String error;
    }

    @Data
    public static class ExecutionStatusDTO {
        private String executionId;
        private String workflowId;
        private String version;
        private String state;
        private String currentNodeId;
        private String createdAt;
        private String startedAt;
        private String completedAt;
        private String errorMessage;
        private String errorNodeId;
        private List<NodeExecutionDTO> executionHistory;
    }

    @Data
    public static class NodeExecutionDTO {
        private String nodeId;
        private String nodeType;
        private String state;
        private int attemptNumber;
        private String executedAt;
        private String completedAt;
        private Long durationMs;
        private String errorMessage;
    }

    @Data
    public static class WorkflowSummaryDTO {
        private String workflowId;
        private String version;
        private String name;
        private String description;
        private String deployedAt;
        private boolean active;
    }

    @Data
    public static class WorkflowDefinitionDTO {
        private String workflowId;
        private String version;
        private String name;
        private String description;
        private String definitionJson;
        private String deployedAt;
        private boolean active;
    }

    @Data
    public static class UndeployResponseDTO {
        private boolean success;
        private String workflowId;
        private String message;
        private String error;
    }
}

