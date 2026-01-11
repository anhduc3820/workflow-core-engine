package workflow.core.engine.validator;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/** Workflow validation result */
@Data
public class ValidationResult {

  private List<ValidationIssue> errors;
  private List<ValidationIssue> warnings;

  public ValidationResult() {
    this.errors = new ArrayList<>();
    this.warnings = new ArrayList<>();
  }

  /** Add error */
  public void addError(String code, String message) {
    errors.add(new ValidationIssue(ValidationSeverity.ERROR, code, message));
  }

  /** Add warning */
  public void addWarning(String code, String message) {
    warnings.add(new ValidationIssue(ValidationSeverity.WARNING, code, message));
  }

  /** Check if validation passed (no errors) */
  public boolean isValid() {
    return errors.isEmpty();
  }

  /** Check if there are any issues (errors or warnings) */
  public boolean hasIssues() {
    return !errors.isEmpty() || !warnings.isEmpty();
  }

  /** Get all issues */
  public List<ValidationIssue> getAllIssues() {
    List<ValidationIssue> allIssues = new ArrayList<>();
    allIssues.addAll(errors);
    allIssues.addAll(warnings);
    return allIssues;
  }

  /** Validation issue */
  @Data
  public static class ValidationIssue {
    private ValidationSeverity severity;
    private String code;
    private String message;

    public ValidationIssue(ValidationSeverity severity, String code, String message) {
      this.severity = severity;
      this.code = code;
      this.message = message;
    }
  }

  /** Validation severity */
  public enum ValidationSeverity {
    ERROR,
    WARNING
  }
}
