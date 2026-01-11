package workflow.core.engine.domain.workflow;

import static org.assertj.core.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Unit Tests: WorkflowInstanceEntity */
@DisplayName("WorkflowInstance Domain Entity Tests")
class WorkflowInstanceEntityTest {

  @Test
  @DisplayName("Should create workflow instance with initial state")
  void shouldCreateWorkflowInstanceWithInitialState() {
    // When
    WorkflowInstanceEntity instance = new WorkflowInstanceEntity("exec-1", "workflow-1", "1.0.0");

    // Then
    assertThat(instance.getExecutionId()).isEqualTo("exec-1");
    assertThat(instance.getWorkflowId()).isEqualTo("workflow-1");
    assertThat(instance.getVersion()).isEqualTo("1.0.0");
    assertThat(instance.getState()).isEqualTo(WorkflowState.PENDING);
    assertThat(instance.getCreatedAt()).isNotNull();
    assertThat(instance.isTerminalState()).isFalse();
  }

  @Test
  @DisplayName("Should transition to running state")
  void shouldTransitionToRunningState() {
    // Given
    WorkflowInstanceEntity instance = new WorkflowInstanceEntity("exec-1", "workflow-1", "1.0.0");

    // When
    instance.start();

    // Then
    assertThat(instance.getState()).isEqualTo(WorkflowState.RUNNING);
    assertThat(instance.getStartedAt()).isNotNull();
  }

  @Test
  @DisplayName("Should complete successfully")
  void shouldCompleteSuccessfully() {
    // Given
    WorkflowInstanceEntity instance = new WorkflowInstanceEntity("exec-1", "workflow-1", "1.0.0");
    instance.start();

    // When
    instance.complete();

    // Then
    assertThat(instance.getState()).isEqualTo(WorkflowState.COMPLETED);
    assertThat(instance.getCompletedAt()).isNotNull();
    assertThat(instance.isTerminalState()).isTrue();
    assertThat(instance.getLockOwner()).isNull();
  }

  @Test
  @DisplayName("Should fail with error message")
  void shouldFailWithErrorMessage() {
    // Given
    WorkflowInstanceEntity instance = new WorkflowInstanceEntity("exec-1", "workflow-1", "1.0.0");
    instance.start();

    // When
    instance.fail("Error occurred", "node-1");

    // Then
    assertThat(instance.getState()).isEqualTo(WorkflowState.FAILED);
    assertThat(instance.getErrorMessage()).isEqualTo("Error occurred");
    assertThat(instance.getErrorNodeId()).isEqualTo("node-1");
    assertThat(instance.getCompletedAt()).isNotNull();
    assertThat(instance.isTerminalState()).isTrue();
  }

  @Test
  @DisplayName("Should pause execution")
  void shouldPauseExecution() {
    // Given
    WorkflowInstanceEntity instance = new WorkflowInstanceEntity("exec-1", "workflow-1", "1.0.0");
    instance.start();

    // When
    instance.pause();

    // Then
    assertThat(instance.getState()).isEqualTo(WorkflowState.PAUSED);
    assertThat(instance.isTerminalState()).isFalse();
    assertThat(instance.getLockOwner()).isNull();
  }

  @Test
  @DisplayName("Should acquire lock when no lock exists")
  void shouldAcquireLockWhenNoLockExists() {
    // Given
    WorkflowInstanceEntity instance = new WorkflowInstanceEntity("exec-1", "workflow-1", "1.0.0");

    // When
    boolean acquired = instance.tryAcquireLock("instance-1");

    // Then
    assertThat(acquired).isTrue();
    assertThat(instance.getLockOwner()).isEqualTo("instance-1");
    assertThat(instance.getLockAcquiredAt()).isNotNull();
  }

  @Test
  @DisplayName("Should not acquire lock when already locked")
  void shouldNotAcquireLockWhenAlreadyLocked() {
    // Given
    WorkflowInstanceEntity instance = new WorkflowInstanceEntity("exec-1", "workflow-1", "1.0.0");
    instance.tryAcquireLock("instance-1");

    // When
    boolean acquired = instance.tryAcquireLock("instance-2");

    // Then
    assertThat(acquired).isFalse();
    assertThat(instance.getLockOwner()).isEqualTo("instance-1");
  }

  @Test
  @DisplayName("Should release lock")
  void shouldReleaseLock() {
    // Given
    WorkflowInstanceEntity instance = new WorkflowInstanceEntity("exec-1", "workflow-1", "1.0.0");
    instance.tryAcquireLock("instance-1");

    // When
    instance.releaseLock();

    // Then
    assertThat(instance.getLockOwner()).isNull();
    assertThat(instance.getLockAcquiredAt()).isNull();
  }

  @Test
  @DisplayName("Should identify terminal states correctly")
  void shouldIdentifyTerminalStatesCorrectly() {
    // Given
    WorkflowInstanceEntity instance = new WorkflowInstanceEntity("exec-1", "workflow-1", "1.0.0");

    // Pending - not terminal
    assertThat(instance.isTerminalState()).isFalse();

    // Running - not terminal
    instance.start();
    assertThat(instance.isTerminalState()).isFalse();

    // Paused - not terminal
    instance.pause();
    assertThat(instance.isTerminalState()).isFalse();

    // Completed - terminal
    instance.complete();
    assertThat(instance.isTerminalState()).isTrue();
  }
}
