# 📖 User Guide - Workflow Core Engine

> **Workflow Core Engine v2.0** - Step-by-Step Guide  
> Last Updated: January 11, 2026

---

## 📚 Table of Contents

1. [Preparation & Setup](#1-preparation--setup)
2. [Step 1: Deploy Workflow](#2-step-1-deploy-workflow)
3. [Step 2: Execute Workflow (Synchronous)](#3-step-2-execute-workflow-synchronous)
4. [Step 3: Execute Workflow (Asynchronous)](#4-step-3-execute-workflow-asynchronous)
5. [Step 4: Query Execution Status](#5-step-4-query-execution-status)
6. [Step 5: Replay Timeline](#6-step-5-replay-timeline)
7. [Step 6: Rollback Execution](#7-step-6-rollback-execution)
8. [Real-World Examples](#8-real-world-examples)
9. [Troubleshooting](#9-troubleshooting)

---

## 1. Preparation & Setup

### 1.1 System Requirements

```bash
# Check Java version
java -version

# Expected result
java version "17.0.x"
```

### 1.2 Start Application

```bash
# Method 1: Maven
cd workflow-core-engine
mvn spring-boot:run

# Method 2: JAR file
java -jar target/workflow-core-engine-0.0.1-SNAPSHOT.jar

# Method 3: Docker
docker run -p 8080:8080 workflow-engine:2.0
```

### 1.3 Verify Application is Running

```bash
# Health check
curl http://localhost:8080/actuator/health

# Expected result
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "ping": { "status": "UP" }
  }
}
```

### 1.4 Prepare Tools

```bash
# Install curl (if not present)
# macOS
brew install curl

# Ubuntu/Debian
sudo apt-get install curl

# Windows PowerShell (built-in)
curl --version
```

---

## 2. Step 1: Deploy Workflow

### Purpose
Upload workflow definition from frontend (React Flow JSON) to the system for execution.

### Step-by-Step Actions

**Step 2.1: Prepare Workflow JSON**

Create file `order-workflow.json`:

```json
{
  "workflowId": "order-processing",
  "version": "1.0.0",
  "name": "Order Processing Workflow",
  "description": "Process customer orders",
  "nodes": [
    { "id": "start", "type": "START_EVENT", "name": "Start Order" },
    {
      "id": "validate-order",
      "type": "SERVICE_TASK",
      "name": "Validate Order",
      "serviceTaskImplementation": {
        "beanName": "orderValidationService",
        "methodName": "validateOrder"
      }
    },
    {
      "id": "process-payment",
      "type": "SERVICE_TASK",
      "name": "Process Payment",
      "serviceTaskImplementation": {
        "beanName": "paymentService",
        "methodName": "processPayment"
      }
    },
    {
      "id": "send-notification",
      "type": "SERVICE_TASK",
      "name": "Send Notification",
      "serviceTaskImplementation": {
        "beanName": "notificationService",
        "methodName": "sendConfirmation"
      }
    },
    { "id": "end", "type": "END_EVENT", "name": "Order Complete" }
  ],
  "edges": [
    { "source": "start", "target": "validate-order" },
    { "source": "validate-order", "target": "process-payment" },
    { "source": "process-payment", "target": "send-notification" },
    { "source": "send-notification", "target": "end" }
  ]
}
```

**Step 2.2: Call Deploy API**

```bash
# Linux/macOS
curl -X POST http://localhost:8080/api/workflows/deploy \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant-1" \
  -d @order-workflow.json

# Windows PowerShell
$json = Get-Content order-workflow.json
curl.exe -X POST "http://localhost:8080/api/workflows/deploy" `
  -H "Content-Type: application/json" `
  -H "X-Tenant-Id: tenant-1" `
  -d $json
```

**Step 2.3: Verify Result**

```json
{
  "workflowId": "order-processing",
  "version": "1.0.0",
  "name": "Order Processing Workflow",
  "status": "DEPLOYED",
  "deployedAt": "2026-01-11T10:00:00Z",
  "deployedBy": "system"
}
```

✅ **Workflow deployed successfully!**

---

## 3. Step 2: Execute Workflow (Synchronous)

### Purpose
Execute workflow and wait for result (blocking call). Suitable for short workflows.

### Step-by-Step Actions

**Step 3.1: Prepare Input Variables**

Create file `execute-sync-request.json`:

```json
{
  "version": "1.0.0",
  "variables": {
    "orderId": "ORD-2026-001",
    "customerId": "CUST-12345",
    "amount": 1500.00,
    "currency": "USD",
    "items": [
      { "productId": "PROD-001", "quantity": 2, "price": 750.00 }
    ],
    "shippingAddress": "123 Main Street, City, State 12345",
    "paymentMethod": "credit-card"
  }
}
```

**Step 3.2: Call Execute API (Sync)**

```bash
# Linux/macOS
curl -X POST http://localhost:8080/api/workflows/order-processing/execute \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant-1" \
  -d @execute-sync-request.json

# Windows PowerShell
$json = Get-Content execute-sync-request.json
curl.exe -X POST "http://localhost:8080/api/workflows/order-processing/execute" `
  -H "Content-Type: application/json" `
  -H "X-Tenant-Id: tenant-1" `
  -d $json
```

**Step 3.3: Wait for Result (3-5 seconds)**

Successful response:

```json
{
  "executionId": "exec-2026-001-sync",
  "workflowId": "order-processing",
  "version": "1.0.0",
  "state": "COMPLETED",
  "currentNodeId": "end",
  "variables": {
    "orderId": "ORD-2026-001",
    "customerId": "CUST-12345",
    "amount": 1500.00,
    "transactionId": "TXN-PAY-123456",
    "notificationId": "NOTIF-9876543",
    "status": "PROCESSED"
  },
  "startedAt": "2026-01-11T10:00:00.100Z",
  "completedAt": "2026-01-11T10:00:04.500Z",
  "duration": 4400
}
```

✅ **Workflow completed synchronously!**

---

## 4. Step 3: Execute Workflow (Asynchronous)

### Purpose
Submit workflow request and receive `executionId` immediately (non-blocking). Suitable for long workflows or batch processing.

### Step-by-Step Actions

**Step 4.1: Call Execute API (Async)**

```bash
# Linux/macOS
curl -X POST "http://localhost:8080/api/workflows/order-processing/execute?async=true" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant-1" \
  -d @execute-sync-request.json

# Windows PowerShell
$json = Get-Content execute-sync-request.json
curl.exe -X POST "http://localhost:8080/api/workflows/order-processing/execute?async=true" `
  -H "Content-Type: application/json" `
  -H "X-Tenant-Id: tenant-1" `
  -d $json
```

**Step 4.2: Receive Execution ID Immediately**

Instant response (< 100ms):

```json
{
  "executionId": "exec-2026-002-async",
  "workflowId": "order-processing",
  "status": "SUBMITTED",
  "submittedAt": "2026-01-11T10:05:00.200Z"
}
```

✅ **Workflow submitted! Execution ID: `exec-2026-002-async`**

---

## 5. Step 4: Query Execution Status

### Purpose
Check current status of async execution (from step 4).

### Step-by-Step Actions

**Step 5.1: Poll Status (Polling Pattern)**

```bash
# Repeat every 1 second until COMPLETED
for i in {1..30}; do
  echo "Polling attempt $i..."
  curl -s http://localhost:8080/api/workflows/executions/exec-2026-002-async \
    -H "X-Tenant-Id: tenant-1" | jq '.state'
  sleep 1
done

# Windows PowerShell
for ($i = 1; $i -le 30; $i++) {
  Write-Host "Polling attempt $i..."
  $response = curl.exe -s "http://localhost:8080/api/workflows/executions/exec-2026-002-async" `
    -H "X-Tenant-Id: tenant-1"
  $response | ConvertFrom-Json | Select-Object -ExpandProperty state
  Start-Sleep -Seconds 1
}
```

**Step 5.2: Result When Execution Completes**

```json
{
  "executionId": "exec-2026-002-async",
  "workflowId": "order-processing",
  "version": "1.0.0",
  "state": "COMPLETED",
  "currentNodeId": "end",
  "variables": {
    "orderId": "ORD-2026-001",
    "customerId": "CUST-12345",
    "amount": 1500.00,
    "transactionId": "TXN-PAY-123457",
    "notificationId": "NOTIF-9876544",
    "status": "PROCESSED"
  },
  "startedAt": "2026-01-11T10:05:00.300Z",
  "completedAt": "2026-01-11T10:05:05.800Z"
}
```

✅ **Async workflow completed!**

---

## 6. Step 5: Replay Timeline

### Purpose
View complete execution with detailed timeline per node (timeline visualization).

### Step-by-Step Actions

**Step 6.1: Call Replay API**

```bash
# Linux/macOS
curl -s http://localhost:8080/api/replay/exec-2026-001-sync/timeline \
  -H "X-Tenant-Id: tenant-1" | jq .

# Windows PowerShell
$response = curl.exe -s "http://localhost:8080/api/replay/exec-2026-001-sync/timeline" `
  -H "X-Tenant-Id: tenant-1"
$response | ConvertFrom-Json | ConvertTo-Json -Depth 10
```

**Step 6.2: Analyze Timeline Response**

| Node | Duration | Status | Notes |
|------|----------|--------|-------|
| Start Order | 50ms | ✅ | Initialization |
| Validate Order | 950ms | ✅ | Validation logic |
| Process Payment | 1850ms | ✅ | Payment gateway call |
| Send Notification | 1150ms | ✅ | Email/SMS sent |
| End | 150ms | ✅ | Finalization |
| **Total** | **4.4s** | ✅ | **Success** |

✅ **Timeline displays detailed execution flow!**

---

## 7. Step 6: Rollback Execution

### Purpose
Revert to previous state if needed (e.g., user requests order cancellation).

### Step-by-Step Actions

**Step 7.1: Create Rollback Request**

Create file `rollback-request.json`:

```json
{
  "reason": "Customer requested order cancellation",
  "cancellationReason": "Changed mind about purchase",
  "refundAmount": 1500.00,
  "notes": "Full refund to original payment method"
}
```

**Step 7.2: Call Rollback API**

```bash
# Linux/macOS
curl -X POST http://localhost:8080/api/workflows/executions/exec-2026-001-sync/rollback \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant-1" \
  -d @rollback-request.json

# Windows PowerShell
$json = Get-Content rollback-request.json
curl.exe -X POST "http://localhost:8080/api/workflows/executions/exec-2026-001-sync/rollback" `
  -H "Content-Type: application/json" `
  -H "X-Tenant-Id: tenant-1" `
  -d $json
```

**Step 7.3: Rollback Result**

```json
{
  "executionId": "exec-2026-001-sync",
  "workflowId": "order-processing",
  "previousState": "COMPLETED",
  "newState": "CANCELLED",
  "rollbackReason": "Customer requested order cancellation",
  "rolledBackAt": "2026-01-11T10:10:00.000Z",
  "compensationEvents": [
    {
      "sequence": 1,
      "nodeId": "process-payment",
      "compensationAction": "REVERSE_PAYMENT",
      "compensationStatus": "SUCCESS",
      "details": {
        "refundTransactionId": "TXN-REF-654321",
        "refundAmount": 1500.00,
        "refundedAt": "2026-01-11T10:10:00.500Z"
      }
    }
  ]
}
```

✅ **Rollback completed - Order cancelled with refund!**

---

## 8. Real-World Examples

### Example 1: Loan Approval Process

**Step 1: Deploy Loan Workflow**

```bash
curl -X POST http://localhost:8080/api/workflows/deploy \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: bank-tenant" \
  -d '{
    "workflowId": "loan-approval",
    "version": "1.0.0",
    "name": "Loan Approval Process",
    "nodes": [
      { "id": "start", "type": "START_EVENT", "name": "Start" },
      { "id": "check-credit", "type": "SERVICE_TASK", "name": "Check Credit Score",
        "serviceTaskImplementation": { "beanName": "creditService", "methodName": "checkScore" }
      },
      { "id": "verify-income", "type": "SERVICE_TASK", "name": "Verify Income",
        "serviceTaskImplementation": { "beanName": "incomeService", "methodName": "verify" }
      },
      { "id": "approve-loan", "type": "SERVICE_TASK", "name": "Approve Loan",
        "serviceTaskImplementation": { "beanName": "loanService", "methodName": "approveLoan" }
      },
      { "id": "end", "type": "END_EVENT", "name": "Complete" }
    ],
    "edges": [
      { "source": "start", "target": "check-credit" },
      { "source": "check-credit", "target": "verify-income" },
      { "source": "verify-income", "target": "approve-loan" },
      { "source": "approve-loan", "target": "end" }
    ]
  }'
```

**Step 2: Execute Loan Workflow**

```bash
curl -X POST http://localhost:8080/api/workflows/loan-approval/execute \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: bank-tenant" \
  -d '{
    "version": "1.0.0",
    "variables": {
      "applicantId": "APP-001",
      "applicantName": "John Doe",
      "loanAmount": 50000,
      "loanTerm": 60,
      "creditScore": 750,
      "annualIncome": 100000
    }
  }'
```

**Step 3: Replay for Details**

```bash
curl http://localhost:8080/api/replay/exec-loan-001/timeline \
  -H "X-Tenant-Id: bank-tenant" | jq '.events[] | {nodeId, status, duration}'
```

---

### Example 2: Order Processing with Error & Rollback

**Scenario**: Order processed but payment error detected

**Step 1: Execute Order**

```bash
EXECUTION_ID=$(curl -s -X POST http://localhost:8080/api/workflows/order-processing/execute \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant-1" \
  -d '{"version": "1.0.0", "variables": {"orderId": "ORD-ERROR-001", "amount": 2000.00}}' \
  | jq -r '.executionId')

echo "Execution ID: $EXECUTION_ID"
```

**Step 2: Detect Error and Rollback**

```bash
curl -X POST http://localhost:8080/api/workflows/executions/$EXECUTION_ID/rollback \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant-1" \
  -d '{"reason": "Detected duplicate charge in payment system"}'
```

**Step 3: Check Status After Rollback**

```bash
curl http://localhost:8080/api/workflows/executions/$EXECUTION_ID \
  -H "X-Tenant-Id: tenant-1" | jq '{state, rollbackReason, completedAt}'
```

---

## 9. Troubleshooting

### Error 1: Connection Refused

```
curl: (7) Failed to connect to localhost:8080: Connection refused
```

**Solution:**
```bash
# Check if application is running
curl http://localhost:8080/actuator/health

# If not running, restart
mvn spring-boot:run
```

---

### Error 2: Workflow Not Found

```json
{
  "error": "Workflow not found: unknown-workflow"
}
```

**Solution:**
```bash
# Deploy workflow first
curl -X POST http://localhost:8080/api/workflows/deploy \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant-1" \
  -d @workflow.json
```

---

### Error 3: Execution ID Not Found

```json
{
  "error": "Execution not found: wrong-exec-id"
}
```

**Solution:**
- Ensure executionId is correct (from deployment response)
- If async, wait for workflow to complete before replay

---

### Error 4: Test Failures

If test errors occur:

```bash
# Re-run tests with details
mvn test -Dtest=*IntegrationTest -X

# View surefire report
cat target/surefire-reports/TEST-*.txt
```

---

## 📋 Usage Checklist

- [ ] Application started (port 8080)
- [ ] Health check OK
- [ ] Workflow deployed
- [ ] Workflow executed (sync or async)
- [ ] Status queried
- [ ] Timeline replayed
- [ ] (Optional) Rollback executed

---

## 🎓 Cheat Sheet - Quick Commands

```bash
# 1. Health Check
curl http://localhost:8080/actuator/health

# 2. Deploy
curl -X POST http://localhost:8080/api/workflows/deploy \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant-1" \
  -d @workflow.json

# 3. Execute (Sync)
curl -X POST http://localhost:8080/api/workflows/{workflowId}/execute \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant-1" \
  -d @input.json

# 4. Execute (Async)
curl -X POST "http://localhost:8080/api/workflows/{workflowId}/execute?async=true" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant-1" \
  -d @input.json

# 5. Get Status
curl http://localhost:8080/api/workflows/executions/{executionId} \
  -H "X-Tenant-Id: tenant-1"

# 6. Replay Timeline
curl http://localhost:8080/api/replay/{executionId}/timeline \
  -H "X-Tenant-Id: tenant-1"

# 7. Rollback
curl -X POST http://localhost:8080/api/workflows/executions/{executionId}/rollback \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: tenant-1" \
  -d '{"reason": "Cancel request"}'
```

---

## 📞 Support & Documentation

- **Architecture**: See `ARCHITECTURE.md`
- **Implementation**: See `IMPLEMENTATION.md`
- **README**: See `README.md`
- **API Docs**: Visit `http://localhost:8080/swagger-ui.html` (if enabled)

---

**Version**: 2.0.0  
**Status**: Production Ready ✅  
**Last Updated**: January 11, 2026

