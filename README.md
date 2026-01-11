# Workflow Core Engine

A production-ready workflow execution engine for Spring Boot, compatible with React Flow frontend exports.

## 🎯 Overview

This engine consumes workflow JSON definitions from the frontend (React Flow) and executes them as type-safe, deterministic workflows.

### Key Features

- ✅ **JSON-Driven**: Parse workflow definitions from frontend exports
- ✅ **BPMN Validation**: Comprehensive validation rules (start/end events, gateway semantics, reachability)
- ✅ **Node-Driven Execution**: State machine-based execution model
- ✅ **Multiple Node Types**: 
  - Events: START_EVENT, END_EVENT
  - Tasks: SERVICE_TASK, BUSINESS_RULE_TASK, USER_TASK, SCRIPT_TASK
  - Gateways: EXCLUSIVE_GATEWAY (XOR), PARALLEL_GATEWAY (AND), INCLUSIVE_GATEWAY (OR)
  - Subprocesses: SUBPROCESS, CALL_ACTIVITY
- ✅ **Conditional Branching**: JavaScript expression evaluation on edges
- ✅ **Drools Integration**: Execute business rules with ruleflow-groups
- ✅ **Service Integration**: Invoke Spring beans by name
- ✅ **Variable Mapping**: Input/output mappings between nodes
- ✅ **Execution Context**: State tracking, variable management, execution history
- ✅ **REST API**: Deploy, validate, and execute workflows via HTTP

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────┐
│                    REST API Layer                       │
│              (WorkflowController)                       │
└────────────────────┬────────────────────────────────────┘
                     │
┌────────────────────▼────────────────────────────────────┐
│            Orchestration Service                        │
│       (WorkflowOrchestrationService)                    │
└─────┬──────────────┬──────────────┬─────────────────────┘
      │              │              │
┌─────▼─────┐  ┌────▼─────┐  ┌────▼─────────┐
│  Parser   │  │Validator │  │  Executor    │
│   (JSON   │  │ (BPMN    │  │(State-driven)│
│   → Graph)│  │ Rules)   │  │              │
└───────────┘  └──────────┘  └──────┬───────┘
                                     │
          ┌──────────────────────────┼──────────────┐
          │                          │              │
     ┌────▼────┐              ┌──────▼─────┐  ┌────▼────────┐
     │Handlers │              │  Services  │  │ Condition   │
     │(Node-   │              │ (Drools,   │  │ Evaluator   │
     │specific)│              │  Spring)   │  │             │
     └─────────┘              └────────────┘  └─────────────┘
```

---

## 📦 Components

### 1. **Model Layer** (`model/`)
- `WorkflowDefinition`: Root JSON structure
- `WorkflowGraph`: Internal graph representation
- `NodeConfig`, `EdgeConfig`: Node/edge configurations
- `WorkflowContext`: Execution state and variables
- `NodeType`, `GatewayType`, `PathType`: Enums

### 2. **Parser** (`parser/`)
- `WorkflowParser`: JSON → WorkflowGraph conversion

### 3. **Validator** (`validator/`)
- `WorkflowValidator`: BPMN validation rules
- Checks: start/end events, gateway semantics, reachability, edge connectivity

### 4. **Executor** (`executor/`)
- `WorkflowExecutor`: Core execution engine (node-driven traversal)
- `ConditionEvaluator`: JavaScript expression evaluation

### 5. **Handlers** (`handler/`)
- `StartEventHandler`, `EndEventHandler`
- `ServiceTaskHandler`: Invoke Spring beans
- `BusinessRuleTaskHandler`: Execute Drools rules
- `UserTaskHandler`: Pause for manual tasks
- `GatewayHandler`: XOR/AND/OR logic

### 6. **Services** (`service/`)
- `WorkflowOrchestrationService`: High-level workflow operations
- `WorkflowRegistry`: Store deployed workflows
- `DroolsService`: Load and execute .drl files

### 7. **API** (`api/`)
- `WorkflowController`: REST endpoints

---

## 🚀 Quick Start

### 1. Build the Project

```bash
cd workflow-core-engine/workflow-core-engine
mvn clean install
```

### 2. Run the Application

```bash
mvn spring-boot:run
```

The engine will start on `http://localhost:8080`

### 3. Deploy a Workflow

```bash
curl -X POST http://localhost:8080/api/workflows/deploy \
  -H "Content-Type: application/json" \
  -d @example-backend-workflow.json
```

### 4. Execute the Workflow

```bash
curl -X POST http://localhost:8080/api/workflows/loan_approval_workflow/execute?version=1.0.0 \
  -H "Content-Type: application/json" \
  -d '{
    "application": {
      "applicantName": "John Doe",
      "loanAmount": 50000,
      "creditScore": 750,
      "income": 80000
    }
  }'
```

---

## 🔌 API Endpoints

### Deploy Workflow
```
POST /api/workflows/deploy
Body: Workflow JSON
Response: { "success": true, "workflowId": "...", "version": "..." }
```

### Validate Workflow
```
POST /api/workflows/validate
Body: Workflow JSON
Response: { "valid": true, "validationResult": {...} }
```

### Execute Workflow
```
POST /api/workflows/{workflowId}/execute?version={version}
Body: { "variable1": "value1", ... }
Response: { 
  "success": true, 
  "executionId": "...", 
  "state": "COMPLETED",
  "variables": {...},
  "executionHistory": [...]
}
```

### Undeploy Workflow
```
DELETE /api/workflows/{workflowId}?version={version}
Response: { "success": true, "message": "..." }
```

---

## 📝 Workflow JSON Format

```json
{
  "workflowId": "my_workflow",
  "version": "1.0.0",
  "name": "My Workflow",
  "execution": {
    "nodes": [
      {
        "id": "start_1",
        "type": "START_EVENT",
        "name": "Start"
      },
      {
        "id": "task_1",
        "type": "SERVICE_TASK",
        "name": "Process Data",
        "serviceName": "myService",
        "inputMappings": [
          { "source": "input", "target": "data" }
        ],
        "outputMappings": [
          { "source": "result", "target": "output" }
        ]
      },
      {
        "id": "end_1",
        "type": "END_EVENT",
        "name": "End"
      }
    ],
    "edges": [
      { "id": "e1", "source": "start_1", "target": "task_1", "pathType": "success" },
      { "id": "e2", "source": "task_1", "target": "end_1", "pathType": "success" }
    ]
  }
}
```

---

## 🎓 Node Types

### Events
- **START_EVENT**: Workflow entry point (required, exactly one)
- **END_EVENT**: Workflow exit point (required, at least one)

### Tasks
- **SERVICE_TASK**: Invoke Spring bean method
- **BUSINESS_RULE_TASK**: Execute Drools rules
- **USER_TASK**: Pause for manual action
- **SCRIPT_TASK**: Execute script (JavaScript)

### Gateways
- **EXCLUSIVE_GATEWAY (XOR)**: Take one path based on conditions
- **PARALLEL_GATEWAY (AND)**: Take all paths in parallel
- **INCLUSIVE_GATEWAY (OR)**: Take one or more paths based on conditions

---

## 🔧 Gateway Logic

### XOR Gateway (Exclusive)
```json
{
  "id": "gateway_1",
  "type": "EXCLUSIVE_GATEWAY",
  "gatewayType": "XOR"
}
```
Edges:
```json
{ "source": "gateway_1", "target": "task_a", "condition": "score > 700", "priority": 1 },
{ "source": "gateway_1", "target": "task_b", "condition": "score <= 700", "priority": 2 }
```

### AND Gateway (Parallel)
```json
{
  "id": "gateway_2",
  "type": "PARALLEL_GATEWAY",
  "gatewayType": "AND"
}
```
All outgoing edges are taken simultaneously.

### OR Gateway (Inclusive)
```json
{
  "id": "gateway_3",
  "type": "INCLUSIVE_GATEWAY",
  "gatewayType": "OR"
}
```
All edges with matching conditions are taken.

---

## 🛠️ Service Task Integration

Create a Spring bean:

```java
@Service("myService")
public class MyService {
    public Map<String, Object> execute(Map<String, Object> input) {
        // Business logic here
        Map<String, Object> output = new HashMap<>();
        output.put("result", processedData);
        return output;
    }
}
```

Reference in workflow:
```json
{
  "type": "SERVICE_TASK",
  "serviceName": "myService",
  "inputMappings": [...],
  "outputMappings": [...]
}
```

---

## 📚 Drools Integration

Place `.drl` files in `src/main/resources/rules/`:

```drools
package com.example.rules

rule "High Credit Score"
    ruleflow-group "decision"
    when
        $loan : LoanApplication(creditScore > 700)
    then
        $loan.setApproved(true);
        $loan.setDecisionReason("High credit score");
end
```

Reference in workflow:
```json
{
  "type": "BUSINESS_RULE_TASK",
  "ruleFile": "rules/loan-decision.drl",
  "ruleflowGroup": "decision",
  "inputMappings": [...],
  "outputMappings": [...]
}
```

---

## ✅ Validation Rules

The engine validates:
1. ✅ Exactly one START_EVENT
2. ✅ At least one END_EVENT
3. ✅ No dangling edges (all edges connect to valid nodes)
4. ✅ No self-loops
5. ✅ Gateway semantics (proper input/output counts)
6. ✅ Reachability (all nodes reachable from start, at least one end reachable)
7. ✅ Business rule task configuration (ruleFile, ruleflowGroup)
8. ✅ Service task configuration (serviceName)

---

## 📊 Execution Model

1. **Parse**: JSON → Internal Graph
2. **Validate**: Check BPMN rules
3. **Deploy**: Register in registry
4. **Execute**:
   - Create `WorkflowContext`
   - Start from START_EVENT
   - For each node:
     - Mark as RUNNING
     - Execute handler
     - Mark as COMPLETED
     - Select next edges (based on gateway logic)
     - Continue to next nodes
   - Reach END_EVENT → COMPLETED

---

## 🎯 Design Principles

- **Deterministic**: Same input → same output
- **Type-Safe**: Full Java type checking
- **Node-Driven**: Execution follows graph structure
- **Stateful**: Context tracks all variables and history
- **Extensible**: Add custom handlers for new node types
- **Observable**: Full execution history and logging

---

## 🧪 Testing

Place `example-backend-workflow.json` in `src/main/resources/` and test:

```bash
# Deploy
curl -X POST http://localhost:8080/api/workflows/deploy \
  -H "Content-Type: application/json" \
  -d @src/main/resources/example-backend-workflow.json

# Execute
curl -X POST http://localhost:8080/api/workflows/loan_approval_workflow/execute?version=1.0.0 \
  -H "Content-Type: application/json" \
  -d '{ "application": { "creditScore": 750 } }'
```

---

## 📖 Frontend Integration

This engine is designed to work with the React Flow frontend in `tech-portfolio/`.

1. Export workflow from frontend using `workflowExporter.ts`
2. POST to `/api/workflows/deploy`
3. Execute via `/api/workflows/{id}/execute`

---

## 🚀 Production Readiness

- ✅ Clean, maintainable code
- ✅ Comprehensive validation
- ✅ Error handling and logging
- ✅ Thread-safe execution context
- ✅ Deterministic execution
- ✅ RESTful API
- ✅ No reflection-heavy operations
- ✅ Compatible with future jBPM/Kogito alignment

---

## 📄 License

This is a portfolio/demonstration project.

---

## 👨‍💻 Author

Backend Architect specializing in Spring Boot & Workflow Engines

---

**Workflow Core Engine** - Transforming React Flow designs into executable workflows.

