package workflow.core.engine.application.workflow;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import workflow.core.engine.domain.workflow.WorkflowDefinitionEntity;
import workflow.core.engine.domain.workflow.WorkflowDefinitionRepository;
import workflow.core.engine.model.WorkflowDefinition;
import workflow.core.engine.parser.WorkflowParser;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Use Case: Deploy Workflow
 * Handles workflow definition deployment
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeployWorkflowUseCase {

    private final WorkflowDefinitionRepository definitionRepository;
    private final WorkflowParser workflowParser;
    private final ObjectMapper objectMapper;

    /**
     * Deploy a new workflow definition
     *
     * @param workflowJson The workflow JSON definition
     * @return Deployed workflow definition entity
     */
    @Transactional
    public WorkflowDefinitionEntity execute(String workflowJson) {
        log.info("Deploying workflow definition");

        // Parse and validate workflow (parse to graph for validation)
        workflowParser.parse(workflowJson);  // This validates the JSON

        // Parse to definition to get metadata
        WorkflowDefinition definition;
        try {
            definition = objectMapper.readValue(workflowJson, WorkflowDefinition.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid workflow JSON: " + e.getMessage(), e);
        }

        // Check if workflow already exists
        Optional<WorkflowDefinitionEntity> existing = definitionRepository
                .findByWorkflowIdAndVersion(definition.getWorkflowId(), definition.getVersion());

        if (existing.isPresent()) {
            log.warn("Workflow {} version {} already exists, updating",
                    definition.getWorkflowId(), definition.getVersion());
            WorkflowDefinitionEntity entity = existing.get();
            entity.setDefinitionJson(workflowJson);
            entity.setName(definition.getName());
            entity.setDescription(definition.getDescription());
            entity.setDeployedAt(Instant.now());
            return definitionRepository.save(entity);
        }

        // Create new definition
        WorkflowDefinitionEntity entity = new WorkflowDefinitionEntity(
                definition.getWorkflowId(),
                definition.getVersion(),
                definition.getName(),
                workflowJson
        );
        entity.setDescription(definition.getDescription());

        WorkflowDefinitionEntity saved = definitionRepository.save(entity);
        log.info("Workflow {} version {} deployed successfully",
                definition.getWorkflowId(), definition.getVersion());

        return saved;
    }

    /**
     * Get all active workflow definitions
     */
    @Transactional(readOnly = true)
    public List<WorkflowDefinitionEntity> getAllActive() {
        return definitionRepository.findByActiveTrue();
    }

    /**
     * Get workflow definition by ID (latest active version)
     */
    @Transactional(readOnly = true)
    public Optional<WorkflowDefinitionEntity> getById(String workflowId) {
        // Get all versions for this workflow ID, ordered by deployment date desc
        List<WorkflowDefinitionEntity> versions = definitionRepository.findByWorkflowIdAndTenantIdOrderByDeployedAtDesc(workflowId, "default");
        // Return the first active one (most recent)
        return versions.stream().filter(WorkflowDefinitionEntity::isActive).findFirst();
    }

    /**
     * Undeploy workflow (mark as inactive)
     */
    @Transactional
    public void undeploy(String workflowId) {
        log.info("Undeploying workflow: {}", workflowId);
        List<WorkflowDefinitionEntity> entities = definitionRepository.findByWorkflowIdAndTenantIdOrderByDeployedAtDesc(workflowId, "default");

        if (!entities.isEmpty()) {
            // Mark all versions as inactive
            for (WorkflowDefinitionEntity definition : entities) {
                definition.setActive(false);
                definitionRepository.save(definition);
            }
            log.info("Workflow {} undeployed successfully ({} versions)", workflowId, entities.size());
        } else {
            throw new IllegalArgumentException("Workflow not found: " + workflowId);
        }
    }
}

