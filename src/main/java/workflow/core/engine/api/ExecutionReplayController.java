package workflow.core.engine.api;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import workflow.core.engine.application.replay.VisualExecutionReplayService;

import java.util.List;
import java.util.Map;

/**
 * Execution Replay REST API
 * Provides endpoints for visual execution replay and analysis
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/replay")
@RequiredArgsConstructor
public class ExecutionReplayController {

    private final VisualExecutionReplayService replayService;

    /**
     * Get full execution replay
     * GET /api/v1/replay/{executionId}
     */
    @GetMapping("/{executionId}")
    public ResponseEntity<VisualExecutionReplayService.ExecutionReplayData> getExecutionReplay(
            @PathVariable String executionId) {
        log.info("Fetching execution replay for: {}", executionId);

        VisualExecutionReplayService.ExecutionReplayData replay = replayService.getExecutionReplay(executionId);
        return ResponseEntity.ok(replay);
    }

    /**
     * Get execution replay for specific sequence range
     * GET /api/v1/replay/{executionId}/range?start={start}&end={end}
     */
    @GetMapping("/{executionId}/range")
    public ResponseEntity<VisualExecutionReplayService.ExecutionReplayData> getExecutionReplayRange(
            @PathVariable String executionId,
            @RequestParam Long start,
            @RequestParam Long end) {
        log.info("Fetching execution replay for: {} (seq {} to {})", executionId, start, end);

        VisualExecutionReplayService.ExecutionReplayData replay =
                replayService.getExecutionReplay(executionId, start, end);
        return ResponseEntity.ok(replay);
    }

    /**
     * Get node execution states
     * GET /api/v1/replay/{executionId}/nodes
     */
    @GetMapping("/{executionId}/nodes")
    public ResponseEntity<Map<String, VisualExecutionReplayService.NodeExecutionState>> getNodeStates(
            @PathVariable String executionId) {
        log.info("Fetching node states for: {}", executionId);

        Map<String, VisualExecutionReplayService.NodeExecutionState> nodeStates =
                replayService.getNodeStates(executionId);
        return ResponseEntity.ok(nodeStates);
    }

    /**
     * Get edge traversals
     * GET /api/v1/replay/{executionId}/edges
     */
    @GetMapping("/{executionId}/edges")
    public ResponseEntity<List<VisualExecutionReplayService.EdgeTraversal>> getEdgeTraversals(
            @PathVariable String executionId) {
        log.info("Fetching edge traversals for: {}", executionId);

        List<VisualExecutionReplayService.EdgeTraversal> traversals =
                replayService.getEdgeTraversals(executionId);
        return ResponseEntity.ok(traversals);
    }
}

