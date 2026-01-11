package workflow.core.engine.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import workflow.core.engine.model.*;

/**
 * Parse workflow JSON into internal graph model
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WorkflowParser {

    private final ObjectMapper objectMapper;

    /**
     * Parse workflow JSON string into WorkflowGraph
     */
    public WorkflowGraph parse(String json) throws WorkflowParseException {
        try {
            log.debug("Parsing workflow JSON...");

            // Parse JSON to WorkflowDefinition
            WorkflowDefinition definition = objectMapper.readValue(json, WorkflowDefinition.class);

            // Convert to graph
            return buildGraph(definition);

        } catch (Exception e) {
            log.error("Failed to parse workflow JSON", e);
            throw new WorkflowParseException("Failed to parse workflow: " + e.getMessage(), e);
        }
    }

    /**
     * Parse workflow JSON string into WorkflowGraph (alias for parse)
     */
    public WorkflowGraph parseToGraph(String json) throws WorkflowParseException {
        return parse(json);
    }

    /**
     * Build internal graph from WorkflowDefinition
     */
    private WorkflowGraph buildGraph(WorkflowDefinition definition) {
        WorkflowGraph graph = new WorkflowGraph();

        // Set metadata
        graph.setWorkflowId(definition.getWorkflowId());
        graph.setVersion(definition.getVersion());
        graph.setName(definition.getName());
        graph.setDescription(definition.getDescription());

        WorkflowDefinition.ExecutionDefinition execution = definition.getExecution();

        if (execution == null) {
            throw new WorkflowParseException("Execution definition is missing");
        }

        // Add nodes
        if (execution.getNodes() != null) {
            for (NodeConfig nodeConfig : execution.getNodes()) {
                GraphNode node = new GraphNode(nodeConfig);
                graph.addNode(node);
                log.debug("Added node: {} ({})", node.getId(), node.getType());
            }
        }

        // Add edges
        if (execution.getEdges() != null) {
            for (EdgeConfig edgeConfig : execution.getEdges()) {
                GraphEdge edge = new GraphEdge(edgeConfig);
                graph.addEdge(edge);
                log.debug("Added edge: {} -> {}", edge.getSource(), edge.getTarget());
            }
        }

        log.info("Parsed workflow '{}' with {} nodes and {} edges",
                graph.getName(),
                graph.getNodes().size(),
                execution.getEdges() != null ? execution.getEdges().size() : 0);

        return graph;
    }
}

