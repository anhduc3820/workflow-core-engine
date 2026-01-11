package workflow.core.engine.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Node types matching frontend export */
public enum NodeType {
  START_EVENT("START_EVENT"),
  END_EVENT("END_EVENT"),
  INTERMEDIATE_EVENT("INTERMEDIATE_EVENT"),
  TASK("TASK"), // Generic task type for backward compatibility
  SCRIPT_TASK("SCRIPT_TASK"),
  SERVICE_TASK("SERVICE_TASK"),
  USER_TASK("USER_TASK"),
  BUSINESS_RULE_TASK("BUSINESS_RULE_TASK"),
  MANUAL_TASK("MANUAL_TASK"),
  SUBPROCESS("SUBPROCESS"),
  CALL_ACTIVITY("CALL_ACTIVITY"),
  EXCLUSIVE_GATEWAY("EXCLUSIVE_GATEWAY"),
  PARALLEL_GATEWAY("PARALLEL_GATEWAY"),
  INCLUSIVE_GATEWAY("INCLUSIVE_GATEWAY"),
  EVENT_BASED_GATEWAY("EVENT_BASED_GATEWAY");

  private final String value;

  NodeType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static NodeType fromValue(String value) {
    for (NodeType type : values()) {
      if (type.value.equalsIgnoreCase(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown node type: " + value);
  }

  public boolean isEvent() {
    return this == START_EVENT || this == END_EVENT || this == INTERMEDIATE_EVENT;
  }

  public boolean isTask() {
    return this == TASK
        || this == SCRIPT_TASK
        || this == SERVICE_TASK
        || this == USER_TASK
        || this == BUSINESS_RULE_TASK
        || this == MANUAL_TASK;
  }

  public boolean isGateway() {
    return this == EXCLUSIVE_GATEWAY
        || this == PARALLEL_GATEWAY
        || this == INCLUSIVE_GATEWAY
        || this == EVENT_BASED_GATEWAY;
  }

  public boolean isSubprocess() {
    return this == SUBPROCESS || this == CALL_ACTIVITY;
  }
}
