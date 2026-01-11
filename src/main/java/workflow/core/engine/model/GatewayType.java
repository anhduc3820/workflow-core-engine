package workflow.core.engine.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Gateway types for decision logic */
public enum GatewayType {
  XOR("XOR"), // Exclusive OR - exactly one path
  AND("AND"), // Parallel AND - all paths
  OR("OR"); // Inclusive OR - one or more paths

  private final String value;

  GatewayType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static GatewayType fromValue(String value) {
    for (GatewayType type : values()) {
      if (type.value.equalsIgnoreCase(value)) {
        return type;
      }
    }
    throw new IllegalArgumentException("Unknown gateway type: " + value);
  }
}
