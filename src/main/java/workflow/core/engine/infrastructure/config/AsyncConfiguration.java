package workflow.core.engine.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration: Async Execution
 * Configures async execution for scalable workflow processing
 */
@Configuration
@EnableAsync
public class AsyncConfiguration {

    /**
     * Thread pool for async workflow execution
     * Configured for high throughput and scalability
     */
    @Bean(name = "workflowAsyncExecutor")
    public Executor workflowAsyncExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        // Core pool size - minimum threads always alive
        executor.setCorePoolSize(10);

        // Max pool size - maximum threads that can be created
        executor.setMaxPoolSize(50);

        // Queue capacity - tasks queued before creating new threads
        executor.setQueueCapacity(500);

        // Thread name prefix
        executor.setThreadNamePrefix("workflow-exec-");

        // Rejection policy - caller runs if queue is full
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());

        // Wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);

        executor.initialize();
        return executor;
    }
}

