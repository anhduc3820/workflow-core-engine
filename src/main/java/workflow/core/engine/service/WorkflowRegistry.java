package workflow.core.engine.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import workflow.core.engine.model.WorkflowGraph;

/** Workflow registry Stores deployed workflow definitions */
@Slf4j
@Service
public class WorkflowRegistry {

  // workflowId -> version -> WorkflowGraph
  private final Map<String, Map<String, WorkflowGraph>> workflows;

  public WorkflowRegistry() {
    this.workflows = new ConcurrentHashMap<>();
  }

  /** Register workflow */
  public void register(WorkflowGraph workflow) {
    String workflowId = workflow.getWorkflowId();
    String version = workflow.getVersion();

    workflows.computeIfAbsent(workflowId, k -> new ConcurrentHashMap<>()).put(version, workflow);

    log.info("Registered workflow: {} (v{})", workflowId, version);
  }

  /** Get workflow by ID and version */
  public WorkflowGraph getWorkflow(String workflowId, String version) {
    Map<String, WorkflowGraph> versions = workflows.get(workflowId);
    if (versions == null) {
      return null;
    }
    return versions.get(version);
  }

  /** Get latest version of workflow */
  public WorkflowGraph getLatestWorkflow(String workflowId) {
    Map<String, WorkflowGraph> versions = workflows.get(workflowId);
    if (versions == null || versions.isEmpty()) {
      return null;
    }

    // Return the workflow with the highest version
    return versions.values().stream()
        .max((w1, w2) -> compareVersions(w1.getVersion(), w2.getVersion()))
        .orElse(null);
  }

  /** Unregister workflow */
  public void unregister(String workflowId, String version) {
    Map<String, WorkflowGraph> versions = workflows.get(workflowId);
    if (versions != null) {
      versions.remove(version);
      if (versions.isEmpty()) {
        workflows.remove(workflowId);
      }
      log.info("Unregistered workflow: {} (v{})", workflowId, version);
    }
  }

  /** Check if workflow exists */
  public boolean exists(String workflowId, String version) {
    return getWorkflow(workflowId, version) != null;
  }

  /** Get all workflow IDs */
  public java.util.Set<String> getAllWorkflowIds() {
    return workflows.keySet();
  }

  /** Get all active workflows (latest versions) */
  public java.util.List<Object> getAllActiveWorkflows() {
    java.util.List<Object> result = new java.util.ArrayList<>();

    for (String workflowId : workflows.keySet()) {
      WorkflowGraph latest = getLatestWorkflow(workflowId);
      if (latest != null) {
        result.add(
            java.util.Map.of(
                "workflowId", latest.getWorkflowId(),
                "version", latest.getVersion(),
                "name", latest.getName(),
                "description", latest.getDescription() != null ? latest.getDescription() : "",
                "nodeCount", latest.getNodes().size()));
      }
    }

    return result;
  }

  /** Compare semantic versions (simple implementation) */
  private int compareVersions(String v1, String v2) {
    try {
      String[] parts1 = v1.split("\\.");
      String[] parts2 = v2.split("\\.");

      int length = Math.max(parts1.length, parts2.length);
      for (int i = 0; i < length; i++) {
        int n1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
        int n2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;

        if (n1 != n2) {
          return Integer.compare(n1, n2);
        }
      }

      return 0;
    } catch (NumberFormatException e) {
      return v1.compareTo(v2);
    }
  }
}
