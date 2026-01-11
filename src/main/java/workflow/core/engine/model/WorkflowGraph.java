package workflow.core.engine.model;

import java.util.*;
import lombok.Data;

/** Internal workflow graph representation Optimized for execution and traversal */
@Data
public class WorkflowGraph {

  private String workflowId;
  private String version;
  private String name;
  private String description;

  // Nodes indexed by ID
  private Map<String, GraphNode> nodes;

  // Adjacency list: nodeId -> list of outgoing edges
  private Map<String, List<GraphEdge>> adjacencyList;

  // Reverse adjacency list: nodeId -> list of incoming edges
  private Map<String, List<GraphEdge>> reverseAdjacencyList;

  // Start and end events
  private GraphNode startEvent;
  private List<GraphNode> endEvents;

  public WorkflowGraph() {
    this.nodes = new LinkedHashMap<>();
    this.adjacencyList = new LinkedHashMap<>();
    this.reverseAdjacencyList = new LinkedHashMap<>();
    this.endEvents = new ArrayList<>();
  }

  /** Add node to graph */
  public void addNode(GraphNode node) {
    nodes.put(node.getId(), node);
    adjacencyList.putIfAbsent(node.getId(), new ArrayList<>());
    reverseAdjacencyList.putIfAbsent(node.getId(), new ArrayList<>());

    // Track start/end events
    if (node.getType() == NodeType.START_EVENT) {
      startEvent = node;
    } else if (node.getType() == NodeType.END_EVENT) {
      endEvents.add(node);
    }
  }

  /** Add edge to graph */
  public void addEdge(GraphEdge edge) {
    adjacencyList.get(edge.getSource()).add(edge);
    reverseAdjacencyList.get(edge.getTarget()).add(edge);
  }

  /** Get outgoing edges from a node */
  public List<GraphEdge> getOutgoingEdges(String nodeId) {
    return adjacencyList.getOrDefault(nodeId, Collections.emptyList());
  }

  /** Get incoming edges to a node */
  public List<GraphEdge> getIncomingEdges(String nodeId) {
    return reverseAdjacencyList.getOrDefault(nodeId, Collections.emptyList());
  }

  /** Get node by ID */
  public GraphNode getNode(String nodeId) {
    return nodes.get(nodeId);
  }

  /** Get all nodes */
  public Collection<GraphNode> getAllNodes() {
    return nodes.values();
  }
}
