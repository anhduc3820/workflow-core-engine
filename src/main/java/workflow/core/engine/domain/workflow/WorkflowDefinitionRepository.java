package workflow.core.engine.domain.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository: Workflow Definition
 * Provides persistence operations for workflow definitions
 */
@Repository
public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinitionEntity, String> {

    /**
     * Find active workflows
     */
    List<WorkflowDefinitionEntity> findByActiveTrue();

    /**
     * Find by workflow ID and version
     */
    Optional<WorkflowDefinitionEntity> findByWorkflowIdAndVersion(String workflowId, String version);

    /**
     * Check if workflow exists
     */
    boolean existsByWorkflowId(String workflowId);
}

