package workflow.core.engine.infrastructure.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import workflow.core.engine.infrastructure.tenant.TenantContext;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Workflow Metrics Service (v2)
 * Exposes Prometheus-compatible metrics for observability
 */
@Slf4j
@Service
public class WorkflowMetricsService {

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, AtomicInteger> activeWorkflowsByTenant = new ConcurrentHashMap<>();

    public WorkflowMetricsService(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    // ==================== Workflow Lifecycle Metrics ====================

    public void recordWorkflowStarted(String tenantId, String workflowId) {
        Counter.builder("workflow.started.total")
                .tag("tenant", tenantId)
                .tag("workflow_id", workflowId)
                .description("Total number of workflows started")
                .register(meterRegistry)
                .increment();

        activeWorkflowsByTenant.computeIfAbsent(tenantId, k ->
                meterRegistry.gauge("workflow.active.count",
                        new AtomicInteger(0),
                        AtomicInteger::get))
                .incrementAndGet();
    }

    public void recordWorkflowCompleted(String tenantId, String workflowId, Instant startTime) {
        Counter.builder("workflow.completed.total")
                .tag("tenant", tenantId)
                .tag("workflow_id", workflowId)
                .description("Total number of workflows completed")
                .register(meterRegistry)
                .increment();

        Duration duration = Duration.between(startTime, Instant.now());
        Timer.builder("workflow.execution.duration")
                .tag("tenant", tenantId)
                .tag("workflow_id", workflowId)
                .description("Workflow execution duration")
                .register(meterRegistry)
                .record(duration);

        decrementActiveWorkflows(tenantId);
    }

    public void recordWorkflowFailed(String tenantId, String workflowId, String errorType) {
        Counter.builder("workflow.failed.total")
                .tag("tenant", tenantId)
                .tag("workflow_id", workflowId)
                .tag("error_type", errorType)
                .description("Total number of workflows failed")
                .register(meterRegistry)
                .increment();

        decrementActiveWorkflows(tenantId);
    }

    // ==================== Node Execution Metrics ====================

    public void recordNodeExecution(String tenantId, String nodeType, long durationMs) {
        Timer.builder("workflow.node.execution.duration")
                .tag("tenant", tenantId)
                .tag("node_type", nodeType)
                .description("Node execution duration")
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMs));
    }

    public void recordNodeFailure(String tenantId, String nodeType, String errorType) {
        Counter.builder("workflow.node.failed.total")
                .tag("tenant", tenantId)
                .tag("node_type", nodeType)
                .tag("error_type", errorType)
                .description("Total number of node execution failures")
                .register(meterRegistry)
                .increment();
    }

    // ==================== Gateway Metrics ====================

    public void recordGatewayEvaluation(String tenantId, String gatewayType, int pathsSelected) {
        Counter.builder("workflow.gateway.evaluated.total")
                .tag("tenant", tenantId)
                .tag("gateway_type", gatewayType)
                .description("Total number of gateway evaluations")
                .register(meterRegistry)
                .increment();

        DistributionSummary.builder("workflow.gateway.paths.selected")
                .tag("tenant", tenantId)
                .tag("gateway_type", gatewayType)
                .register(meterRegistry)
                .record(pathsSelected);
    }

    // ==================== Rule Execution Metrics ====================

    public void recordRuleExecution(String tenantId, String ruleFlowGroup, int rulesFired, long durationMs) {
        Counter.builder("workflow.rule.execution.total")
                .tag("tenant", tenantId)
                .tag("rule_flow_group", ruleFlowGroup)
                .description("Total number of rule executions")
                .register(meterRegistry)
                .increment();

        DistributionSummary.builder("workflow.rule.fired.count")
                .tag("tenant", tenantId)
                .tag("rule_flow_group", ruleFlowGroup)
                .register(meterRegistry)
                .record(rulesFired);

        Timer.builder("workflow.rule.execution.duration")
                .tag("tenant", tenantId)
                .tag("rule_flow_group", ruleFlowGroup)
                .description("Rule execution duration")
                .register(meterRegistry)
                .record(Duration.ofMillis(durationMs));
    }

    // ==================== Lock & Retry Metrics ====================

    public void recordLockAcquired(String tenantId) {
        Counter.builder("workflow.lock.acquired.total")
                .tag("tenant", tenantId)
                .description("Total number of locks acquired")
                .register(meterRegistry)
                .increment();
    }

    public void recordLockContention(String tenantId) {
        Counter.builder("workflow.lock.contention.total")
                .tag("tenant", tenantId)
                .description("Total number of lock contentions")
                .register(meterRegistry)
                .increment();
    }

    public void recordRetry(String tenantId, String workflowId, int attemptNumber) {
        Counter.builder("workflow.retry.total")
                .tag("tenant", tenantId)
                .tag("workflow_id", workflowId)
                .description("Total number of workflow retries")
                .register(meterRegistry)
                .increment();

        DistributionSummary.builder("workflow.retry.attempt.number")
                .tag("tenant", tenantId)
                .register(meterRegistry)
                .record(attemptNumber);
    }

    // ==================== Helper Methods ====================

    private void decrementActiveWorkflows(String tenantId) {
        AtomicInteger counter = activeWorkflowsByTenant.get(tenantId);
        if (counter != null) {
            counter.decrementAndGet();
        }
    }

    public int getActiveWorkflowCount(String tenantId) {
        AtomicInteger counter = activeWorkflowsByTenant.get(tenantId);
        return counter != null ? counter.get() : 0;
    }
}

