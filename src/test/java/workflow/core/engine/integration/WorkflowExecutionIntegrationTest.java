package workflow.core.engine.integration;

import static org.assertj.core.api.Assertions.*;
import static org.awaitility.Awaitility.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import workflow.core.engine.application.executor.ExecutionStateManager;
import workflow.core.engine.application.executor.StatelessWorkflowExecutor;
import workflow.core.engine.application.workflow.DeployWorkflowUseCase;
import workflow.core.engine.domain.workflow.WorkflowDefinitionEntity;
import workflow.core.engine.domain.workflow.WorkflowInstanceEntity;
import workflow.core.engine.domain.workflow.WorkflowState;
import workflow.core.engine.model.WorkflowGraph;
import workflow.core.engine.parser.WorkflowParser;

/** Integration Tests: End-to-End Workflow Execution */
@SpringBootTest
@ActiveProfiles("test")
@DisplayName("E2E Workflow Execution Integration Tests")
class WorkflowExecutionIntegrationTest {

  @Autowired private DeployWorkflowUseCase deployWorkflowUseCase;

  @Autowired private StatelessWorkflowExecutor workflowExecutor;

  @Autowired private ExecutionStateManager stateManager;

  @Autowired private WorkflowParser workflowParser;

  @Autowired private ObjectMapper objectMapper;

  private String simpleWorkflowJson;

  @BeforeEach
  void setUp() {
    // Create a simple workflow JSON for testing
    simpleWorkflowJson =
        """
                {
                  "workflowId": "simple-workflow",
                  "version": "1.0.0",
                  "name": "Simple Test Workflow",
                  "description": "A simple workflow for testing",
                  "execution": {
                    "nodes": [
                      {
                        "id": "start-1",
                        "type": "START_EVENT",
                        "name": "Start"
                      },
                      {
                        "id": "task-1",
                        "type": "TASK",
                        "name": "Simple Task"
                      },
                      {
                        "id": "end-1",
                        "type": "END_EVENT",
                        "name": "End",
                        "terminate": true
                      }
                    ],
                    "edges": [
                      {
                        "id": "edge-1",
                        "source": "start-1",
                        "target": "task-1",
                        "pathType": "default"
                      },
                      {
                        "id": "edge-2",
                        "source": "task-1",
                        "target": "end-1",
                        "pathType": "success"
                      }
                    ]
                  },
                  "metadata": {
                    "author": "Test",
                    "createdAt": "2026-01-11T00:00:00Z"
                  }
                }
                """;
  }

  @Test
  @DisplayName("Should deploy and execute simple workflow successfully")
  @Transactional
  void shouldDeployAndExecuteSimpleWorkflow() throws Exception {
    // Deploy workflow
    WorkflowDefinitionEntity definition = deployWorkflowUseCase.execute(simpleWorkflowJson);
    assertThat(definition).isNotNull();
    assertThat(definition.getWorkflowId()).isEqualTo("simple-workflow");

    // Parse workflow graph
    WorkflowGraph graph = workflowParser.parseToGraph(simpleWorkflowJson);

    // Execute workflow
    Map<String, Object> variables = new HashMap<>();
    variables.put("testInput", "testValue");

    WorkflowInstanceEntity result = workflowExecutor.executeSync(graph, variables);

    // Verify execution
    assertThat(result).isNotNull();
    assertThat(result.getState()).isEqualTo(WorkflowState.COMPLETED);
    assertThat(result.getStartedAt()).isNotNull();
    assertThat(result.getCompletedAt()).isNotNull();
    assertThat(result.getErrorMessage()).isNull();
  }

  @Test
  @DisplayName("Should execute workflow asynchronously")
  // NOTE: No @Transactional here - async execution needs committed data
  void shouldExecuteWorkflowAsynchronously() throws Exception {
    // Deploy workflow
    deployWorkflowUseCase.execute(simpleWorkflowJson);

    // Parse workflow graph
    WorkflowGraph graph = workflowParser.parseToGraph(simpleWorkflowJson);

    // Execute async
    Map<String, Object> variables = new HashMap<>();
    String executionId = workflowExecutor.executeAsync(graph, variables);

    assertThat(executionId).isNotNull();

    // Wait for completion
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () -> {
              WorkflowInstanceEntity instance = stateManager.getInstance(executionId);
              return instance.isTerminalState();
            });

    // Verify final state
    WorkflowInstanceEntity instance = stateManager.getInstance(executionId);
    assertThat(instance.getState()).isEqualTo(WorkflowState.COMPLETED);
  }

  @Test
  @DisplayName("Should handle XOR gateway with conditions")
  @Transactional
  void shouldHandleXORGatewayWithConditions() throws Exception {
    String xorWorkflowJson =
        """
                {
                  "workflowId": "xor-workflow",
                  "version": "1.0.0",
                  "name": "XOR Gateway Workflow",
                  "execution": {
                    "nodes": [
                      {
                        "id": "start-1",
                        "type": "START_EVENT",
                        "name": "Start"
                      },
                      {
                        "id": "gateway-1",
                        "type": "EXCLUSIVE_GATEWAY",
                        "name": "Decision Gateway",
                        "gatewayType": "XOR"
                      },
                      {
                        "id": "task-approved",
                        "type": "SERVICE_TASK",
                        "name": "Approved Task"
                      },
                      {
                        "id": "task-rejected",
                        "type": "SERVICE_TASK",
                        "name": "Rejected Task"
                      },
                      {
                        "id": "end-1",
                        "type": "END_EVENT",
                        "name": "End",
                        "terminate": true
                      }
                    ],
                    "edges": [
                      {
                        "id": "edge-1",
                        "source": "start-1",
                        "target": "gateway-1",
                        "pathType": "default"
                      },
                      {
                        "id": "edge-approved",
                        "source": "gateway-1",
                        "target": "task-approved",
                        "pathType": "conditional",
                        "condition": "approved == true"
                      },
                      {
                        "id": "edge-rejected",
                        "source": "gateway-1",
                        "target": "task-rejected",
                        "pathType": "default"
                      },
                      {
                        "id": "edge-3",
                        "source": "task-approved",
                        "target": "end-1",
                        "pathType": "success"
                      },
                      {
                        "id": "edge-4",
                        "source": "task-rejected",
                        "target": "end-1",
                        "pathType": "success"
                      }
                    ]
                  }
                }
                """;

    // Deploy workflow
    deployWorkflowUseCase.execute(xorWorkflowJson);

    // Parse workflow graph
    WorkflowGraph graph = workflowParser.parseToGraph(xorWorkflowJson);

    // Execute with approved condition
    Map<String, Object> variables = new HashMap<>();
    variables.put("approved", true);

    WorkflowInstanceEntity result = workflowExecutor.executeSync(graph, variables);

    // Verify execution took approved path
    assertThat(result.getState()).isEqualTo(WorkflowState.COMPLETED);

    // Check that approved task was executed
    var history = stateManager.getExecutionHistory(result.getExecutionId());
    assertThat(history).anyMatch(h -> h.getNodeId().equals("task-approved"));
    assertThat(history).noneMatch(h -> h.getNodeId().equals("task-rejected"));
  }

  @Test
  @DisplayName("Should maintain state across multiple instances (HA test)")
  @Transactional
  void shouldMaintainStateAcrossMultipleInstances() throws Exception {
    // Deploy workflow
    deployWorkflowUseCase.execute(simpleWorkflowJson);

    // Parse workflow graph
    WorkflowGraph graph = workflowParser.parseToGraph(simpleWorkflowJson);

    // Create instance
    Map<String, Object> variables = new HashMap<>();
    variables.put("counter", 0);

    WorkflowInstanceEntity instance =
        stateManager.createInstance("simple-workflow", "1.0.0", variables);

    String executionId = instance.getExecutionId();

    // Simulate first instance acquiring lock
    boolean locked = stateManager.acquireLock(executionId);
    assertThat(locked).isTrue();

    // Simulate second instance trying to acquire lock (should fail)
    boolean secondLock = stateManager.acquireLock(executionId);
    assertThat(secondLock).isFalse();

    // First instance releases lock
    stateManager.releaseLock(executionId);

    // Second instance can now acquire lock
    boolean thirdLock = stateManager.acquireLock(executionId);
    assertThat(thirdLock).isTrue();

    stateManager.releaseLock(executionId);
  }

  @Test
  @DisplayName("Should persist execution history")
  @Transactional
  void shouldPersistExecutionHistory() throws Exception {
    // Deploy and execute
    deployWorkflowUseCase.execute(simpleWorkflowJson);
    WorkflowGraph graph = workflowParser.parseToGraph(simpleWorkflowJson);

    Map<String, Object> variables = new HashMap<>();
    WorkflowInstanceEntity result = workflowExecutor.executeSync(graph, variables);

    // Get execution history
    var history = stateManager.getExecutionHistory(result.getExecutionId());

    // Verify history contains all nodes
    assertThat(history).hasSizeGreaterThanOrEqualTo(3); // start, task, end
    assertThat(history).anyMatch(h -> h.getNodeId().equals("start-1"));
    assertThat(history).anyMatch(h -> h.getNodeId().equals("task-1"));
    assertThat(history).anyMatch(h -> h.getNodeId().equals("end-1"));

    // Verify history is ordered by execution time
    for (int i = 1; i < history.size(); i++) {
      assertThat(history.get(i).getExecutedAt())
          .isAfterOrEqualTo(history.get(i - 1).getExecutedAt());
    }
  }
}
