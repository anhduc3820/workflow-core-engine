# Financial-Grade Workflow Engine - Quick Start

## 🚀 What's New in v3.0

This workflow engine has been upgraded to **financial-grade standards** with:

### ✅ Visual Execution Replay
- Deterministic replay from persisted events
- Crash recovery (works after pod restart, node crash, deployment upgrade)
- Timeline-based visualization for React Flow
- Step-by-step debugging capability

### ✅ Financial-Grade Data Integrity
- **ACID guarantees** (Atomicity, Consistency, Isolation, Durability)
- **SERIALIZABLE** transaction isolation by default
- **Two-phase commit** for compensatable operations
- **Idempotency** keys prevent duplicate executions
- Pre-commit validation hooks

### ✅ Multi-Level Rollback
- **Node-level**: Rollback single node execution
- **Step-level**: Rollback to checkpoints
- **Workflow-level**: Rollback entire workflow
- Compensation handlers for safe rollback
- Immutable audit trail of rollback operations

### ✅ Complete Audit & Traceability
- Every execution step auditable
- Immutable event log
- Before/after state snapshots
- Queryable by execution/tenant/time
- Correlation IDs for distributed tracing

### ✅ Code Hygiene Enforcement
- **Spotless** - Automatic code formatting (Google Java Format)
- **PMD** - Static code analysis
- **Dependency Analysis** - Detect unused dependencies
- **Build fails on violations** - No exceptions

### ✅ Comprehensive Testing
- Replay correctness tests
- Crash recovery tests
- Rollback scenario tests
- ACID property tests
- Two-phase commit tests
- 30+ test cases ensuring financial-grade quality

---

## 📋 Prerequisites

- **Java 17+** (Required - currently Java 8 is installed)
- **Maven 3.8+**
- **PostgreSQL 12+** or **MySQL 8+** for production
- **Redis** (optional, for distributed state)

---

## 🔧 Quick Setup

### 1. Install Java 17

```bash
# Windows (using Chocolatey)
choco install openjdk17

# Or download from https://adoptium.net/
# Set JAVA_HOME environment variable
```

### 2. Clone and Build

```bash
cd D:\progaram-language\inform\workflow-core-engine

# Compile (with auto-formatting)
mvn clean compile

# Run tests
mvn test

# Full build with quality checks
mvn clean verify
```

### 3. Run the Application

```bash
# Development mode (H2 database)
mvn spring-boot:run

# Production mode (PostgreSQL)
mvn spring-boot:run -Dspring.profiles.active=postgres
```

---

## 🎯 Usage Examples

### Execute with Financial-Grade Transaction

```java
@Autowired
private FinancialTransactionManager transactionManager;

// Create transaction context
TransactionContext context = TransactionContext.builder()
    .executionId(executionId)
    .nodeId("payment-node")
    .nodeType("payment")
    .tenantId("tenant-123")
    .isolationLevel(TransactionDefinition.ISOLATION_SERIALIZABLE)
    .timeoutSeconds(30)
    .preCommitValidator(ctx -> {
        // Validate before commit
        if (!isValid(ctx)) {
            throw new TransactionValidationException("Invalid");
        }
    })
    .build();

// Execute in transaction
PaymentResult result = transactionManager.executeInTransaction(context, ctx -> {
    return paymentService.processPayment(ctx.getInput("amount"));
});
```

### Implement Two-Phase Commit

```java
TwoPhaseOperation<PaymentResult> operation = new TwoPhaseOperation<>() {
    @Override
    public PaymentResult prepare(TransactionContext ctx) {
        // Phase 1: Reserve and validate
        return paymentService.reserve(ctx.getInput("amount"));
    }

    @Override
    public void commit(TransactionContext ctx, PaymentResult prepared) {
        // Phase 2: Finalize
        paymentService.commit(prepared.getTransactionId());
    }

    @Override
    public boolean hasCompensation() {
        return true;
    }

    @Override
    public CompensationHandler getCompensationHandler() {
        return ctx -> paymentService.refund(ctx.getOriginalOutputSnapshot());
    }
};

PaymentResult result = transactionManager.executeWithTwoPhaseCommit(context, operation);
```

### Register Compensation Handlers

```java
@Component
public class PaymentCompensationConfig {
    
    @Autowired
    private CompensationService compensationService;
    
    @PostConstruct
    public void registerHandlers() {
        // Register payment compensation
        compensationService.registerHandler("payment", context -> {
            String paymentId = extractPaymentId(context.getOriginalOutputSnapshot());
            paymentService.refund(paymentId);
            log.info("Payment {} refunded", paymentId);
        });
        
        // Register inventory compensation
        compensationService.registerHandler("inventory-reserve", context -> {
            String reservationId = extractId(context.getOriginalOutputSnapshot());
            inventoryService.release(reservationId);
            log.info("Inventory {} released", reservationId);
        });
    }
}
```

### Rollback Operations

```java
@Autowired
private RollbackCoordinator rollbackCoordinator;

// Node-level rollback
RollbackResult result = rollbackCoordinator.rollbackNode(
    executionId,
    "payment-node",
    RollbackReason.userRequested("Customer requested refund")
);

// Step-level rollback to checkpoint
Long checkpointSeq = rollbackCoordinator.createCheckpoint(executionId, "after-payment");
RollbackResult result = rollbackCoordinator.rollbackToCheckpoint(
    executionId,
    checkpointSeq,
    RollbackReason.executionFailed("Downstream validation failed")
);

// Workflow-level rollback
RollbackResult result = rollbackCoordinator.rollbackWorkflow(
    executionId,
    RollbackReason.timeoutExceeded("SLA breach")
);
```

### Visual Execution Replay

```java
@Autowired
private VisualExecutionReplayService replayService;

@Autowired
private ReplayEngine replayEngine;

// Get full replay data for UI
ExecutionReplayData replay = replayService.getExecutionReplay(executionId);

// Get node states
Map<String, NodeExecutionState> nodeStates = replayService.getNodeStates(executionId);

// Get edge traversals
List<EdgeTraversal> edges = replayService.getEdgeTraversals(executionId);

// Check if can resume after crash
boolean canResume = replayEngine.canResume(executionId);
if (canResume) {
    ResumePoint resumePoint = replayEngine.getResumePoint(executionId);
    // Resume from resumePoint.resumeNodeId()
}

// Reconstruct state at specific point
ReconstructedState state = replayEngine.reconstructState(executionId, sequenceNumber);
```

---

## 🧪 Running Tests

```bash
# Run all tests
mvn test

# Run specific test suite
mvn test -Dtest=ReplayIntegrationTest
mvn test -Dtest=RollbackScenarioTest
mvn test -Dtest=FinancialTransactionTest

# Run with coverage
mvn test jacoco:report
```

---

## 📚 Documentation

- **[FINANCIAL_GRADE_GUIDE.md](FINANCIAL_GRADE_GUIDE.md)** - Complete implementation guide
- **[IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md)** - Implementation summary and checklist
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - System architecture (v2.0 baseline)

---

## 🔍 Code Quality

### Format Code

```bash
# Auto-format all code
mvn spotless:apply

# Check formatting
mvn spotless:check
```

### Static Analysis

```bash
# Run PMD
mvn pmd:check

# Analyze dependencies
mvn dependency:analyze
```

### Full Quality Check

```bash
# Runs: compile, test, format check, PMD, dependency analysis
mvn clean verify
```

---

## 🚨 Important Notes

### Data Correctness > Performance

This is a **financial-grade system**. We prioritize:
1. Correctness over speed
2. ACID guarantees over throughput
3. Traceability over convenience
4. Explicit over implicit

### No Silent Failures

All failures are:
- Logged with full context
- Recorded in audit trail
- Propagated with clear messages
- Recoverable or compensatable

### Every Step is Traceable

All operations create immutable audit events:
- Who executed it
- When it was executed
- What changed (before/after)
- Why it changed
- Correlation IDs for tracing

---

## 🏗️ Architecture

### Transaction Boundaries

```
┌─────────────────────────────────────────────┐
│       FinancialTransactionManager           │
│  ┌────────────────────────────────────────┐ │
│  │  SERIALIZABLE Transaction Boundary     │ │
│  │  ┌──────────────────────────────────┐  │ │
│  │  │  Node Execution                  │  │ │
│  │  │  - Pre-commit validation         │  │ │
│  │  │  - Idempotency check             │  │ │
│  │  │  - Business logic                │  │ │
│  │  │  - Event recording               │  │ │
│  │  └──────────────────────────────────┘  │ │
│  │  Commit or Rollback                    │ │
│  └────────────────────────────────────────┘ │
│  Compensation if needed                     │
└─────────────────────────────────────────────┘
```

### Replay Architecture

```
┌──────────────────────────────────────────┐
│         Execution Events                 │
│  (Immutable, Ordered, Persisted)         │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│          ReplayEngine                    │
│  - Reconstruct state from events         │
│  - Deterministic replay                  │
│  - Crash recovery                        │
└──────────────┬───────────────────────────┘
               │
               ▼
┌──────────────────────────────────────────┐
│       ReconstructedState                 │
│  - Current workflow state                │
│  - Completed nodes                       │
│  - Variable values                       │
│  - Resume point                          │
└──────────────────────────────────────────┘
```

---

## 📊 Monitoring

The system exposes metrics via Spring Actuator and Prometheus:

```
# Health check
GET /actuator/health

# Metrics
GET /actuator/metrics
GET /actuator/prometheus

# Active transactions
GET /actuator/metrics/workflow.transactions.active
```

---

## 🔐 Security Considerations

1. **Transaction Isolation**: SERIALIZABLE prevents dirty reads
2. **Audit Trail**: Immutable, tamper-evident event log
3. **Idempotency**: Prevents accidental duplicate operations
4. **Compensation**: Safe rollback without data corruption
5. **Validation**: Pre-commit hooks prevent invalid states

---

## 📞 Support

For issues or questions:
- Check [FINANCIAL_GRADE_GUIDE.md](FINANCIAL_GRADE_GUIDE.md) troubleshooting section
- Review [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md) for setup checklist
- Open GitHub issue with logs and context

---

## 🎓 Learning Path

1. Read [FINANCIAL_GRADE_GUIDE.md](FINANCIAL_GRADE_GUIDE.md) - Complete guide
2. Review test cases in `src/test/java/workflow/core/engine/integration/`
3. Examine `FinancialTransactionManager.java` for transaction patterns
4. Study `RollbackCoordinator.java` for rollback strategies
5. Explore `ReplayEngine.java` for replay mechanics

---

**Remember: This is no longer a simple workflow engine. This is a core transactional platform for financial-grade systems.**

---

## License

Copyright © 2026 Workflow Core Engine Team

