package workflow.core.engine.service;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.Message;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * Drools rule execution service
 * Loads and executes .drl files
 */
@Slf4j
@Service
public class DroolsService {

    private KieServices kieServices;
    private Map<String, KieContainer> containerCache;

    @PostConstruct
    public void init() {
        this.kieServices = KieServices.Factory.get();
        this.containerCache = new HashMap<>();
        log.info("Drools service initialized");
    }

    /**
     * Execute rules from a .drl file
     *
     * @param ruleFile Path to .drl file (e.g., "rules/loan-validation.drl")
     * @param ruleflowGroup The ruleflow-group to execute
     * @param input Input variables
     * @return Output variables after rule execution
     */
    public Map<String, Object> executeRules(String ruleFile, String ruleflowGroup,
                                            Map<String, Object> input) {
        try {
            log.debug("Executing rules from file: {} with ruleflow-group: {}", ruleFile, ruleflowGroup);

            // Get or create KieContainer for rule file
            KieContainer kieContainer = getOrCreateContainer(ruleFile);

            // Create session
            KieSession kieSession = kieContainer.newKieSession();

            try {
                // Insert input facts
                for (Map.Entry<String, Object> entry : input.entrySet()) {
                    kieSession.insert(entry.getValue());
                    log.debug("Inserted fact: {} = {}", entry.getKey(), entry.getValue());
                }

                // Set focus on ruleflow-group
                if (ruleflowGroup != null && !ruleflowGroup.isEmpty()) {
                    kieSession.getAgenda().getAgendaGroup(ruleflowGroup).setFocus();
                    log.debug("Set focus on ruleflow-group: {}", ruleflowGroup);
                }

                // Fire all rules
                int rulesFired = kieSession.fireAllRules();
                log.debug("Fired {} rules", rulesFired);

                // Collect output (modified facts)
                Map<String, Object> output = new HashMap<>(input);

                // In a real implementation, you would collect specific output objects
                // For now, we return the modified input

                return output;

            } finally {
                kieSession.dispose();
            }

        } catch (Exception e) {
            log.error("Failed to execute rules from file: {}", ruleFile, e);
            throw new RuntimeException("Rule execution failed: " + e.getMessage(), e);
        }
    }

    /**
     * Get or create KieContainer for rule file
     */
    private KieContainer getOrCreateContainer(String ruleFile) {
        if (containerCache.containsKey(ruleFile)) {
            return containerCache.get(ruleFile);
        }

        synchronized (this) {
            if (containerCache.containsKey(ruleFile)) {
                return containerCache.get(ruleFile);
            }

            log.info("Loading rule file: {}", ruleFile);

            try {
                // Load .drl file
                String drlContent = loadRuleFile(ruleFile);

                // Create KieFileSystem
                KieFileSystem kieFileSystem = kieServices.newKieFileSystem();

                // Add .drl file to filesystem
                String resourcePath = "src/main/resources/" + ruleFile;
                kieFileSystem.write(resourcePath, drlContent);

                // Build
                KieBuilder kieBuilder = kieServices.newKieBuilder(kieFileSystem);
                kieBuilder.buildAll();

                // Check for errors
                if (kieBuilder.getResults().hasMessages(Message.Level.ERROR)) {
                    StringBuilder errorMsg = new StringBuilder("Failed to compile rules:\n");
                    for (Message message : kieBuilder.getResults().getMessages()) {
                        errorMsg.append(message.toString()).append("\n");
                    }
                    throw new RuntimeException(errorMsg.toString());
                }

                // Create container
                KieContainer kieContainer = kieServices.newKieContainer(
                        kieServices.getRepository().getDefaultReleaseId());

                containerCache.put(ruleFile, kieContainer);

                log.info("Rule file loaded successfully: {}", ruleFile);

                return kieContainer;

            } catch (IOException e) {
                throw new RuntimeException("Failed to load rule file: " + ruleFile, e);
            }
        }
    }

    /**
     * Load rule file from classpath
     */
    private String loadRuleFile(String ruleFile) throws IOException {
        try {
            ClassPathResource resource = new ClassPathResource(ruleFile);

            if (!resource.exists()) {
                // Try without leading slash
                resource = new ClassPathResource("/" + ruleFile);
            }

            if (!resource.exists()) {
                throw new IOException("Rule file not found: " + ruleFile);
            }

            try (InputStream inputStream = resource.getInputStream()) {
                return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            }

        } catch (IOException e) {
            log.error("Failed to load rule file: {}", ruleFile, e);
            throw e;
        }
    }

    /**
     * Clear container cache (for testing or reloading)
     */
    public void clearCache() {
        containerCache.clear();
        log.info("Cleared Drools container cache");
    }
}

