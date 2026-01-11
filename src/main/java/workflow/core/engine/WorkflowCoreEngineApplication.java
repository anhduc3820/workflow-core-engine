package workflow.core.engine;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Workflow Core Engine Application
 *
 * <p>A production-ready workflow engine compatible with frontend React Flow exports
 *
 * <p>Features: - Parse workflow JSON from frontend - Validate workflow integrity (BPMN semantics) -
 * Execute workflows with node-driven state machine - Support for Service Tasks, Business Rule Tasks
 * (Drools), User Tasks, Gateways - Conditional branching (XOR, OR, AND gateways) - Variable mapping
 * and context management - REST API for deployment and execution
 *
 * <p>Architecture: - Parser: JSON → Internal Graph Model - Validator: BPMN validation rules -
 * Executor: Node-driven execution engine - Handlers: Node-specific execution logic - Services:
 * Drools, Service invocation - API: REST endpoints
 */
@Slf4j
@SpringBootApplication
public class WorkflowCoreEngineApplication {

  public static void main(String[] args) {
    log.info("╔═══════════════════════════════════════════════════════╗");
    log.info("║       Workflow Core Engine - Starting...             ║");
    log.info("╚═══════════════════════════════════════════════════════╝");

    ConfigurableApplicationContext context =
        SpringApplication.run(WorkflowCoreEngineApplication.class, args);

    log.info("╔═══════════════════════════════════════════════════════╗");
    log.info("║       Workflow Core Engine - Ready                    ║");
    log.info("║                                                       ║");
    log.info("║  API Endpoints:                                       ║");
    log.info("║  - POST /api/workflows/deploy                         ║");
    log.info("║  - POST /api/workflows/validate                       ║");
    log.info("║  - POST /api/workflows/{id}/execute                   ║");
    log.info("║  - DELETE /api/workflows/{id}?version=X               ║");
    log.info("╚═══════════════════════════════════════════════════════╝");
  }
}
