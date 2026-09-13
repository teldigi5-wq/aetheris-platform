package io.aetheris.orchestrator.task;

import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class TaskControlService {

    private static final Set<TaskState> ACTIVE_STATES = EnumSet.of(
            TaskState.QUEUED,
            TaskState.PLANNING,
            TaskState.AWAITING_APPROVAL,
            TaskState.RUNNING,
            TaskState.PAUSED,
            TaskState.VERIFYING,
            TaskState.ROLLING_BACK);

    private final TaskRepository repository;
    private final TaskService tasks;
    private final AtomicBoolean emergencyStop = new AtomicBoolean(false);
    private volatile Instant changedAt = Instant.now();
    private volatile String reason = "Not engaged";
    private volatile int cancelledTasks = 0;

    public TaskControlService(TaskRepository repository, TaskService tasks) {
        this.repository = repository;
        this.tasks = tasks;
    }

    public boolean isEmergencyStopActive() {
        return emergencyStop.get();
    }

    public EmergencyStopStatus status() {
        return new EmergencyStopStatus(emergencyStop.get(), changedAt, reason, cancelledTasks);
    }

    @Transactional
    public EmergencyStopStatus engage(String requestedReason) {
        emergencyStop.set(true);
        changedAt = Instant.now();
        reason = requestedReason == null || requestedReason.isBlank() ? "Owner emergency stop" : requestedReason.trim();
        int cancelled = 0;
        for (TaskEntity task : repository.findByStateIn(ACTIVE_STATES)) {
            try {
                tasks.transition(task.getId(), new TaskTransitionRequest(
                        TaskState.CANCELLED,
                        "emergency-stop",
                        "Emergency stop: " + reason));
                cancelled++;
            } catch (IllegalStateException ignored) {
                // A concurrent transition may already have moved the task into a terminal state.
            }
        }
        cancelledTasks = cancelled;
        return status();
    }

    public EmergencyStopStatus release(String requestedReason) {
        emergencyStop.set(false);
        changedAt = Instant.now();
        reason = requestedReason == null || requestedReason.isBlank() ? "Owner released emergency stop" : requestedReason.trim();
        cancelledTasks = 0;
        return status();
    }

    @Transactional
    public TaskEntity cancelTask(UUID taskId, String requestedReason) {
        TaskEntity task = tasks.getRequired(taskId);
        if (task.getState() == TaskState.COMPLETED || task.getState() == TaskState.CANCELLED || task.getState() == TaskState.FAILED) {
            return task;
        }
        String message = requestedReason == null || requestedReason.isBlank()
                ? "Task cancelled by owner"
                : "Task cancelled by owner: " + requestedReason.trim();
        return tasks.transition(taskId, new TaskTransitionRequest(TaskState.CANCELLED, "owner-control", message));
    }
}
