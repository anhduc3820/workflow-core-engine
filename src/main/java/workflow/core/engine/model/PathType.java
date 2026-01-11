package workflow.core.engine.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Edge path types */
public enum PathType {
  SUCCESS("success"),
  ERROR("error"),
  CONDITIONAL("conditional"),
  PARALLEL("parallel"),
  DEFAULT("default");

  private final String value;

  PathType(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @JsonCreator
  public static PathType fromValue(String value) {
    if (value == null) {
      return DEFAULT;
    }
    for (PathType type : values()) {
      if (type.value.equalsIgnoreCase(value)) {
        return type;
      }
    }
    return DEFAULT;
  }
}
