package workflow.core.engine.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for WorkflowController
 */
@SpringBootTest
@AutoConfigureMockMvc
class WorkflowControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void testGetAllWorkflows() throws Exception {
        mockMvc.perform(get("/api/workflows"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }

    @Test
    void testDeployWorkflow() throws Exception {
        String workflowJson = """
                {
                    "workflowId": "test-workflow-001",
                    "version": "1.0.0",
                    "name": "Test Workflow",
                    "description": "A simple test workflow",
                    "nodes": [
                        {
                            "id": "start-1",
                            "type": "START_EVENT",
                            "name": "Start"
                        },
                        {
                            "id": "task-1",
                            "type": "TASK",
                            "name": "Process Task"
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
                    ],
                    "metadata": {
                        "author": "Test User",
                        "createdAt": "2026-01-11T15:00:00Z"
                    }
                }
                """;

        mockMvc.perform(post("/api/workflows/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(workflowJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowId").value("test-workflow-001"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testDeployInvalidWorkflow_MissingStartEvent() throws Exception {
        String invalidWorkflowJson = """
                {
                    "workflowId": "invalid-workflow",
                    "version": "1.0.0",
                    "name": "Invalid Workflow",
                    "nodes": [
                        {
                            "id": "task-1",
                            "type": "TASK",
                            "name": "Task Without Start"
                        }
                    ],
                    "edges": []
                }
                """;

        mockMvc.perform(post("/api/workflows/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidWorkflowJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void testExecuteWorkflow() throws Exception {
        // First deploy a workflow
        String workflowJson = """
                {
                    "workflowId": "execution-test-workflow",
                    "version": "1.0.0",
                    "name": "Execution Test Workflow",
                    "nodes": [
                        {
                            "id": "start-1",
                            "type": "START_EVENT",
                            "name": "Start"
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
                            "target": "end-1",
                            "pathType": "default"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/workflows/deploy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(workflowJson))
                .andExpect(status().isOk());

        // Then execute it
        String executionInput = """
                {
                    "testData": "value"
                }
                """;

        mockMvc.perform(post("/api/workflows/execution-test-workflow/execute")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(executionInput))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workflowId").value("execution-test-workflow"))
                .andExpect(jsonPath("$.executionId").exists())
                .andExpect(jsonPath("$.state").exists());
    }

    @Test
    void testUndeployWorkflow() throws Exception {
        // First deploy a workflow
        String workflowJson = """
                {
                    "workflowId": "undeploy-test-workflow",
                    "version": "1.0.0",
                    "name": "Undeploy Test Workflow",
                    "nodes": [
                        {
                            "id": "start-1",
                            "type": "START_EVENT",
                            "name": "Start"
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
                            "target": "end-1",
                            "pathType": "default"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/workflows/deploy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(workflowJson))
                .andExpect(status().isOk());

        // Then undeploy it
        mockMvc.perform(delete("/api/workflows/undeploy-test-workflow?version=1.0.0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void testGetWorkflowById() throws Exception {
        // First deploy a workflow
        String workflowJson = """
                {
                    "workflowId": "get-test-workflow",
                    "version": "1.0.0",
                    "name": "Get Test Workflow",
                    "nodes": [
                        {
                            "id": "start-1",
                            "type": "START_EVENT",
                            "name": "Start"
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
                            "target": "end-1",
                            "pathType": "default"
                        }
                    ]
                }
                """;

        mockMvc.perform(post("/api/workflows/deploy")
                .contentType(MediaType.APPLICATION_JSON)
                .content(workflowJson))
                .andExpect(status().isOk());

        // Then retrieve it
        mockMvc.perform(get("/api/workflows/get-test-workflow"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.workflow.workflowId").value("get-test-workflow"))
                .andExpect(jsonPath("$.workflow.name").value("Get Test Workflow"));
    }
}

