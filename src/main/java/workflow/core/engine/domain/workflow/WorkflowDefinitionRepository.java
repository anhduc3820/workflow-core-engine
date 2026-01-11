package workflow.core.engine.domain.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository: Workflow Definition (v2)
 * Provides persistence operations for workflow definitions
 * Multi-tenant aware with versioning support
 */
@Repository
public interface WorkflowDefinitionRepository extends JpaRepository<WorkflowDefinitionEntity, Long> {

    /**
     * Find active workflows
     */
    List<WorkflowDefinitionEntity> findByActiveTrue();

    /**
     * Find active workflows by tenant (v2)
     */
    List<WorkflowDefinitionEntity> findByActiveTrueAndTenantId(String tenantId);

    /**
     * Find by workflow ID and version
     */
    Optional<WorkflowDefinitionEntity> findByWorkflowIdAndVersion(String workflowId, String version);

    /**
     * Find by workflow ID, version and tenant (v2)
     */
    Optional<WorkflowDefinitionEntity> findByWorkflowIdAndVersionAndTenantId(String workflowId, String version, String tenantId);

    /**
     * Find all versions of a workflow (v2)
     */
    List<WorkflowDefinitionEntity> findByWorkflowIdAndTenantIdOrderByDeployedAtDesc(String workflowId, String tenantId);

    /**
     * Check if workflow exists
     */
    boolean existsByWorkflowId(String workflowId);

    /**
     * Check if workflow exists for tenant (v2)
     */
    boolean existsByWorkflowIdAndTenantId(String workflowId, String tenantId);
}

