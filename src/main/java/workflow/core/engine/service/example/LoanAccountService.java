package workflow.core.engine.service.example;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Example service bean for loan account creation
 * This demonstrates how service tasks invoke Spring beans
 */
@Slf4j
@Service("loanAccountService")
public class LoanAccountService {

    /**
     * Create loan account
     * This method is invoked by SERVICE_TASK nodes
     */
    public Map<String, Object> execute(Map<String, Object> input) {
        log.info("Creating loan account with input: {}", input);

        // Extract input parameters
        Object loanData = input.get("loanData");
        Object interestRate = input.get("interestRate");
        Object monthlyPayment = input.get("monthlyPayment");

        // Simulate account creation
        String accountNumber = generateAccountNumber();
        String accountId = UUID.randomUUID().toString();

        log.info("Created loan account: {} (ID: {})", accountNumber, accountId);

        // Return output
        Map<String, Object> output = new HashMap<>();
        output.put("accountNumber", accountNumber);
        output.put("accountId", accountId);
        output.put("status", "ACTIVE");
        output.put("createdAt", System.currentTimeMillis());

        return output;
    }

    /**
     * Generate account number
     */
    private String generateAccountNumber() {
        return "LA" + System.currentTimeMillis() % 1000000000;
    }
}

