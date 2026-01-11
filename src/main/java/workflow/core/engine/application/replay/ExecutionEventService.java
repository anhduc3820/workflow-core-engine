package workflow.core.engine.application.replay;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import workflow.core.engine.domain.replay.ExecutionEventEntity;
import workflow.core.engine.domain.replay.ExecutionEventRepository;
import workflow.core.engine.domain.replay.ExecutionEventStatus;
import workflow.core.engine.domain.replay.ExecutionEventType;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * Financial-Grade Execution Event Service
 * Manages immutable execution events for replay and audit
 *
 * Key Guarantees:
 * - Idempotent event recording
 * - Atomic event persistence
 * - Deterministic replay
 * - Immutable event history
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExecutionEventService {

    private final ExecutionEventRepository eventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Record an execution event (idempotent)
     * Returns the event if recorded, or existing event if already exists
     */
    @Transactional
    public ExecutionEventEntity recordEvent(String executionId, ExecutionEventType eventType,
                                            Map<String, Object> eventData) {
        Long sequenceNumber = getNextSequenceNumber(executionId);
        return recordEvent(executionId, sequenceNumber, eventType, eventData);
    }

    /**
     * Record an execution event with specific sequence number (idempotent)
     */
    @Transactional
    public ExecutionEventEntity recordEvent(String executionId, Long sequenceNumber,
                                            ExecutionEventType eventType, Map<String, Object> eventData) {
        String idempotencyKey = generateIdempotencyKey(executionId, sequenceNumber, eventType);

        // Check if event already exists (idempotency)
        Optional<ExecutionEventEntity> existing = eventRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            log.debug("Event already exists (idempotent): {}", idempotencyKey);
            return existing.get();
        }

        // Create new event
        ExecutionEventEntity event = new ExecutionEventEntity(executionId, sequenceNumber, eventType);

        // Extract common fields from event data
        if (eventData != null) {
            event.setNodeId((String) eventData.get("nodeId"));
            event.setNodeType((String) eventData.get("nodeType"));
            event.setNodeName((String) eventData.get("nodeName"));
            event.setEdgeTaken((String) eventData.get("edgeTaken"));
            event.setTransactionId((String) eventData.get("transactionId"));

            if (eventData.containsKey("inputSnapshot")) {
                event.setInputSnapshot(serializeSnapshot(eventData.get("inputSnapshot")));
            }
            if (eventData.containsKey("outputSnapshot")) {
                event.setOutputSnapshot(serializeSnapshot(eventData.get("outputSnapshot")));
            }
            if (eventData.containsKey("variablesSnapshot")) {
                event.setVariablesSnapshot(serializeSnapshot(eventData.get("variablesSnapshot")));
            }
            if (eventData.containsKey("errorSnapshot")) {
                event.setErrorSnapshot(serializeSnapshot(eventData.get("errorSnapshot")));
            }
            if (eventData.containsKey("decisionResult")) {
                event.setDecisionResult(serializeSnapshot(eventData.get("decisionResult")));
            }
        }

        ExecutionEventEntity saved = eventRepository.save(event);
        log.debug("Recorded event: {} - seq:{} type:{}", executionId, sequenceNumber, eventType);
        return saved;
    }

    /**
     * Mark event as completed
     */
    @Transactional
    public void markEventCompleted(Long eventId, Long durationMs, Map<String, Object> outputSnapshot) {
        ExecutionEventEntity event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));

        event.markCompleted(durationMs);
        if (outputSnapshot != null) {
            event.setOutputSnapshot(serializeSnapshot(outputSnapshot));
        }
        eventRepository.save(event);
    }

    /**
     * Mark event as failed
     */
    @Transactional
    public void markEventFailed(Long eventId, String errorMessage, Map<String, Object> errorSnapshot) {
        ExecutionEventEntity event = eventRepository.findById(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));

        event.markFailed(serializeSnapshot(Map.of(
                "message", errorMessage,
                "snapshot", errorSnapshot != null ? errorSnapshot : Map.of()
        )));
        eventRepository.save(event);
    }

    /**
     * Get execution timeline for replay
     */
    @Transactional(readOnly = true)
    public List<ExecutionEventEntity> getExecutionTimeline(String executionId) {
        return eventRepository.findByExecutionIdOrderBySequenceNumberAsc(executionId);
    }

    /**
     * Get execution timeline within sequence range
     */
    @Transactional(readOnly = true)
    public List<ExecutionEventEntity> getExecutionTimeline(String executionId, Long startSeq, Long endSeq) {
        return eventRepository.findByExecutionIdAndSequenceRange(executionId, startSeq, endSeq);
    }

    /**
     * Get last event for execution
     */
    @Transactional(readOnly = true)
    public Optional<ExecutionEventEntity> getLastEvent(String executionId) {
        return eventRepository.findLastEventByExecutionId(executionId);
    }

    /**
     * Get failed events
     */
    @Transactional(readOnly = true)
    public List<ExecutionEventEntity> getFailedEvents(String executionId) {
        return eventRepository.findByExecutionIdAndStatus(executionId, ExecutionEventStatus.FAILED);
    }

    /**
     * Get events for specific node
     */
    @Transactional(readOnly = true)
    public List<ExecutionEventEntity> getNodeEvents(String executionId, String nodeId) {
        return eventRepository.findByExecutionIdAndNodeIdOrderBySequenceNumberAsc(executionId, nodeId);
    }

    /**
     * Check if execution can be resumed from last checkpoint
     */
    @Transactional(readOnly = true)
    public boolean canResumeExecution(String executionId) {
        Optional<ExecutionEventEntity> lastEvent = getLastEvent(executionId);
        return lastEvent.isPresent() &&
               lastEvent.get().getStatus() != ExecutionEventStatus.FAILED;
    }

    /**
     * Check if idempotency key exists
     */
    @Transactional(readOnly = true)
    public boolean existsByIdempotencyKey(String idempotencyKey) {
        return eventRepository.findByIdempotencyKey(idempotencyKey).isPresent();
    }

    /**
     * Get execution statistics
     */
    @Transactional(readOnly = true)
    public ExecutionStatistics getStatistics(String executionId) {
        List<ExecutionEventEntity> events = getExecutionTimeline(executionId);

        long totalEvents = events.size();
        long completedEvents = events.stream()
                .filter(e -> e.getStatus() == ExecutionEventStatus.COMPLETED)
                .count();
        long failedEvents = events.stream()
                .filter(e -> e.getStatus() == ExecutionEventStatus.FAILED)
                .count();
        long totalDuration = events.stream()
                .filter(e -> e.getDurationMs() != null)
                .mapToLong(ExecutionEventEntity::getDurationMs)
                .sum();

        return new ExecutionStatistics(totalEvents, completedEvents, failedEvents, totalDuration);
    }

    /**
     * Get next sequence number for execution
     */
    private Long getNextSequenceNumber(String executionId) {
        Optional<ExecutionEventEntity> lastEvent = eventRepository.findLastEventByExecutionId(executionId);
        return lastEvent.map(e -> e.getSequenceNumber() + 1).orElse(1L);
    }

    /**
     * Generate idempotency key
     */
    private String generateIdempotencyKey(String executionId, Long sequenceNumber, ExecutionEventType eventType) {
        return String.format("%s:%d:%s", executionId, sequenceNumber, eventType.name());
    }

    /**
     * Serialize snapshot to JSON
     */
    private String serializeSnapshot(Object snapshot) {
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize snapshot", e);
            return "{}";
        }
    }

    /**
     * Execution Statistics DTO
     */
    public record ExecutionStatistics(
            long totalEvents,
            long completedEvents,
            long failedEvents,
            long totalDurationMs
    ) {}
}

