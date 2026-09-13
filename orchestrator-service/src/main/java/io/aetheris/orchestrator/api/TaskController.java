package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.task.CreateTaskRequest;
import io.aetheris.orchestrator.task.TaskEntity;
import io.aetheris.orchestrator.task.TaskEvent;
import io.aetheris.orchestrator.task.TaskEventStreamService;
import io.aetheris.orchestrator.task.TaskService;
import io.aetheris.orchestrator.task.TaskTransitionRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orchestrator/tasks")
public class TaskController {

    private final TaskService tasks;
    private final TaskEventStreamService streams;

    public TaskController(TaskService tasks, TaskEventStreamService streams) {
        this.tasks = tasks;
        this.streams = streams;
    }

    @PostMapping
    public TaskEntity create(@Valid @RequestBody CreateTaskRequest request) {
        return tasks.create(request);
    }

    @GetMapping
    public List<TaskEntity> recent() {
        return tasks.recent();
    }

    @GetMapping("/{id}")
    public TaskEntity get(@PathVariable UUID id) {
        return tasks.getRequired(id);
    }

    @PostMapping("/{id}/transition")
    public TaskEntity transition(@PathVariable UUID id, @Valid @RequestBody TaskTransitionRequest request) {
        return tasks.transition(id, request);
    }

    @GetMapping(value = "/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter events(@PathVariable UUID id) {
        tasks.getRequired(id);
        SseEmitter emitter = streams.subscribe(id);
        TaskEvent snapshot = tasks.snapshotEvent(id);
        try {
            emitter.send(SseEmitter.event().name("task-event").id(snapshot.timestamp().toString()).data(snapshot));
        } catch (IOException exception) {
            emitter.completeWithError(exception);
        }
        return emitter;
    }
}
