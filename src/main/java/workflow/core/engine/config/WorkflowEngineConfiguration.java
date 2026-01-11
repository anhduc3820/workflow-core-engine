package workflow.core.engine.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Spring configuration for workflow engine */
@Configuration
public class WorkflowEngineConfiguration {

  /** ObjectMapper bean for JSON serialization/deserialization */
  @Bean
  public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();

    // Register Java 8 date/time module
    mapper.registerModule(new JavaTimeModule());

    // Disable writing dates as timestamps
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    // Pretty print JSON
    mapper.enable(SerializationFeature.INDENT_OUTPUT);

    return mapper;
  }
}
