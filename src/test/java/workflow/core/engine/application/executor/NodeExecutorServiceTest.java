package workflow.core.engine.application.executor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import workflow.core.engine.executor.ConditionEvaluator;
import workflow.core.engine.handler.NodeHandler;
import workflow.core.engine.model.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit Tests: NodeExecutorService
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("NodeExecutorService Unit Tests")
class NodeExecutorServiceTest {

    @Mock
    private NodeHandler mockHandler;

    @Mock
    private ConditionEvaluator conditionEvaluator;

    private NodeExecutorService nodeExecutorService;

    @BeforeEach
    void setUp() {
        when(mockHandler.supports(any())).thenReturn(true);
        nodeExecutorService = new NodeExecutorService(List.of(mockHandler), conditionEvaluator);
    }

    @Test
    @DisplayName("Should execute node using appropriate handler")
    void shouldExecuteNodeUsingAppropriateHandler() throws Exception {
        // Given
        NodeConfig config = new NodeConfig();
        config.setId("task-1");
        config.setType(NodeType.SERVICE_TASK);
        config.setName("Test Task");

        GraphNode node = new GraphNode(config);
        Map<String, Object> variables = new HashMap<>();
        variables.put("key", "value");

        // When
        nodeExecutorService.execute(node, null, variables);

        // Then
        verify(mockHandler).execute(eq(node), any());
    }

    @Test
    @DisplayName("Should select single edge when only one exists")
    void shouldSelectSingleEdgeWhenOnlyOneExists() {
        // Given
        GraphNode node = createNode("node-1", NodeType.SERVICE_TASK);
        GraphEdge edge = createEdge("edge-1", "node-1", "node-2");
        Map<String, Object> variables = new HashMap<>();

        // When
        List<GraphEdge> selected = nodeExecutorService.selectEdges(node, List.of(edge), variables);

        // Then
        assertThat(selected).hasSize(1);
        assertThat(selected.get(0)).isEqualTo(edge);
    }

    @Test
    @DisplayName("Should select first matching edge for XOR gateway")
    void shouldSelectFirstMatchingEdgeForXORGateway() {
        // Given
        GraphNode gateway = createNode("gw-1", NodeType.EXCLUSIVE_GATEWAY);
        GraphEdge edge1 = createEdge("edge-1", "gw-1", "node-1");
        edge1.setCondition("result == true");
        GraphEdge edge2 = createEdge("edge-2", "gw-1", "node-2");
        edge2.setCondition("result == false");

        Map<String, Object> variables = new HashMap<>();
        variables.put("result", true);

        when(conditionEvaluator.evaluate("result == true", variables)).thenReturn(true);
        when(conditionEvaluator.evaluate("result == false", variables)).thenReturn(false);

        // When
        List<GraphEdge> selected = nodeExecutorService.selectEdges(
                gateway, List.of(edge1, edge2), variables);

        // Then
        assertThat(selected).hasSize(1);
        assertThat(selected.get(0).getId()).isEqualTo("edge-1");
    }

    @Test
    @DisplayName("Should select all edges for parallel gateway")
    void shouldSelectAllEdgesForParallelGateway() {
        // Given
        GraphNode gateway = createNode("gw-1", NodeType.PARALLEL_GATEWAY);
        GraphEdge edge1 = createEdge("edge-1", "gw-1", "node-1");
        GraphEdge edge2 = createEdge("edge-2", "gw-1", "node-2");
        GraphEdge edge3 = createEdge("edge-3", "gw-1", "node-3");

        Map<String, Object> variables = new HashMap<>();

        // When
        List<GraphEdge> selected = nodeExecutorService.selectEdges(
                gateway, List.of(edge1, edge2, edge3), variables);

        // Then
        assertThat(selected).hasSize(3);
    }

    @Test
    @DisplayName("Should select default edge when no XOR conditions match")
    void shouldSelectDefaultEdgeWhenNoXORConditionsMatch() {
        // Given
        GraphNode gateway = createNode("gw-1", NodeType.EXCLUSIVE_GATEWAY);
        GraphEdge edge1 = createEdge("edge-1", "gw-1", "node-1");
        edge1.setCondition("result == true");
        GraphEdge defaultEdge = createEdge("edge-default", "gw-1", "node-2");
        defaultEdge.setPathType(PathType.DEFAULT);

        Map<String, Object> variables = new HashMap<>();
        variables.put("result", false);

        when(conditionEvaluator.evaluate("result == true", variables)).thenReturn(false);

        // When
        List<GraphEdge> selected = nodeExecutorService.selectEdges(
                gateway, List.of(edge1, defaultEdge), variables);

        // Then
        assertThat(selected).hasSize(1);
        assertThat(selected.get(0).getId()).isEqualTo("edge-default");
    }

    @Test
    @DisplayName("Should select matching edges for inclusive gateway")
    void shouldSelectMatchingEdgesForInclusiveGateway() {
        // Given
        GraphNode gateway = createNode("gw-1", NodeType.INCLUSIVE_GATEWAY);
        GraphEdge edge1 = createEdge("edge-1", "gw-1", "node-1");
        edge1.setCondition("score > 50");
        GraphEdge edge2 = createEdge("edge-2", "gw-1", "node-2");
        edge2.setCondition("score > 70");

        Map<String, Object> variables = new HashMap<>();
        variables.put("score", 80);

        when(conditionEvaluator.evaluate("score > 50", variables)).thenReturn(true);
        when(conditionEvaluator.evaluate("score > 70", variables)).thenReturn(true);

        // When
        List<GraphEdge> selected = nodeExecutorService.selectEdges(
                gateway, List.of(edge1, edge2), variables);

        // Then
        assertThat(selected).hasSize(2);
    }

    // Helper methods

    private GraphNode createNode(String id, NodeType type) {
        NodeConfig config = new NodeConfig();
        config.setId(id);
        config.setType(type);
        return new GraphNode(config);
    }

    private GraphEdge createEdge(String id, String source, String target) {
        EdgeConfig config = new EdgeConfig();
        config.setId(id);
        config.setSource(source);
        config.setTarget(target);
        config.setPathType(PathType.DEFAULT);
        return new GraphEdge(config);
    }
}

