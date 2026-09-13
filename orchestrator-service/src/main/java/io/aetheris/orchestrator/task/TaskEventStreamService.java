package io.aetheris.orchestrator.task;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class TaskEventStreamService {

    private static final long TIMEOUT_MS = Duration.ofMinutes(30).toMillis();
    private final Map<UUID, CopyOnWriteArrayList<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(UUID taskId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MS);
        emitters.computeIfAbsent(taskId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(taskId, emitter));
        emitter.onTimeout(() -> remove(taskId, emitter));
        emitter.onError(error -> remove(taskId, emitter));
        return emitter;
    }

    public void publish(TaskEvent event) {
        List<SseEmitter> subscribers = emitters.getOrDefault(event.taskId(), new CopyOnWriteArrayList<>());
        for (SseEmitter emitter : subscribers) {
            try {
                emitter.send(SseEmitter.event()
                        .name("task-event")
                        .id(event.timestamp().toString())
                        .data(event));
            } catch (IOException | IllegalStateException exception) {
                remove(event.taskId(), emitter);
            }
        }
    }

    private void remove(UUID taskId, SseEmitter emitter) {
        CopyOnWriteArrayList<SseEmitter> subscribers = emitters.get(taskId);
        if (subscribers == null) return;
        subscribers.remove(emitter);
        if (subscribers.isEmpty()) emitters.remove(taskId);
    }
}
