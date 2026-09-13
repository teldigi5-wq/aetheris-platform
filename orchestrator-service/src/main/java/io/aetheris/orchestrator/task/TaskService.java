package io.aetheris.orchestrator.task;

import io.aetheris.orchestrator.policy.OperationMode;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.UUID;

@Service
public class TaskService {

    private static final Map<TaskState, Set<TaskState>> ALLOWED_TRANSITIONS = allowedTransitions();

    private final TaskRepository repository;
    private final TaskEventStreamService eventStream;

    public TaskService(TaskRepository repository, TaskEventStreamService eventStream) {
        this.repository = repository;
        this.eventStream = eventStream;
    }

    @Transactional
    public TaskEntity create(CreateTaskRequest request) {
        OperationMode mode = request.mode() == null ? OperationMode.BALANCED : request.mode();
        TaskEntity task = repository.save(new TaskEntity(UUID.randomUUID(), request.title(), request.command(), mode));
        eventStream.publish(event(task, null, "Task queued", Map.of("mode", mode.name())));
        return task;
    }

    public List<TaskEntity> recent() {
        return repository.findTop50ByOrderByUpdatedAtDesc();
    }

    public TaskEntity getRequired(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Unknown task: " + id));
    }

    @Transactional
    public TaskEntity transition(UUID id, TaskTransitionRequest request) {
        TaskEntity task = getRequired(id);
        TaskState current = task.getState();
        TaskState next = request.state();

        if (current == next) {
            throw new IllegalStateException("Task is already in state " + current);
        }

        Set<TaskState> allowed = ALLOWED_TRANSITIONS.getOrDefault(current, Set.of());
        if (!allowed.contains(next)) {
            throw new IllegalStateException("Invalid task transition: " + current + " -> " + next);
        }

        task.transitionTo(next, request.agentId());
        TaskEntity saved = repository.save(task);
        String message = request.message() == null || request.message().isBlank()
                ? "Task moved to " + next
                : request.message();
        eventStream.publish(event(saved, request.agentId(), message, Map.of("previousState", current.name())));
        return saved;
    }

    public TaskEvent snapshotEvent(UUID id) {
        TaskEntity task = getRequired(id);
        return event(task, task.getActiveAgentId(), "Current task snapshot", Map.of("mode", task.getMode().name()));
    }

    private TaskEvent event(TaskEntity task, String agentId, String message, Map<String, Object> metadata) {
        return new TaskEvent(task.getId(), Instant.now(), task.getState(), agentId, message, metadata);
    }

    private static Map<TaskState, Set<TaskState>> allowedTransitions() {
        EnumMap<TaskState, Set<TaskState>> map = new EnumMap<>(TaskState.class);
        map.put(TaskState.QUEUED, Set.of(TaskState.PLANNING, TaskState.CANCELLED));
        map.put(TaskState.PLANNING, Set.of(TaskState.AWAITING_APPROVAL, TaskState.RUNNING, TaskState.FAILED, TaskState.CANCELLED));
        map.put(TaskState.AWAITING_APPROVAL, Set.of(TaskState.RUNNING, TaskState.FAILED, TaskState.CANCELLED));
        map.put(TaskState.RUNNING, Set.of(TaskState.PAUSED, TaskState.VERIFYING, TaskState.FAILED, TaskState.CANCELLED, TaskState.ROLLING_BACK));
        map.put(TaskState.PAUSED, Set.of(TaskState.RUNNING, TaskState.CANCELLED, TaskState.ROLLING_BACK));
        map.put(TaskState.VERIFYING, Set.of(TaskState.COMPLETED, TaskState.FAILED, TaskState.ROLLING_BACK));
        map.put(TaskState.FAILED, Set.of(TaskState.ROLLING_BACK));
        map.put(TaskState.ROLLING_BACK, Set.of(TaskState.FAILED, TaskState.CANCELLED));
        map.put(TaskState.COMPLETED, Set.of());
        map.put(TaskState.CANCELLED, Set.of());
        return Map.copyOf(map);
    }
}
