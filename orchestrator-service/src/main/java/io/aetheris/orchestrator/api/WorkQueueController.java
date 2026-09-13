package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.runtime.DurableWorkQueueService;
import io.aetheris.orchestrator.runtime.EnqueueWorkItemRequest;
import io.aetheris.orchestrator.runtime.WorkItemEntity;
import io.aetheris.orchestrator.runtime.WorkItemFailureRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orchestrator/work-queue")
public class WorkQueueController {

    private final DurableWorkQueueService queue;
    public WorkQueueController(DurableWorkQueueService queue) { this.queue = queue; }

    @PostMapping public WorkItemEntity enqueue(@Valid @RequestBody EnqueueWorkItemRequest request) { return queue.enqueue(request); }
    @GetMapping public List<WorkItemEntity> recent() { return queue.recent(); }
    @GetMapping("/ready") public List<WorkItemEntity> ready() { return queue.ready(); }
    @GetMapping("/task/{taskId}") public List<WorkItemEntity> forTask(@PathVariable UUID taskId) { return queue.forTask(taskId); }
    @PostMapping("/{id}/claim") public WorkItemEntity claim(@PathVariable UUID id) { return queue.claim(id); }
    @PostMapping("/{id}/succeed") public WorkItemEntity succeed(@PathVariable UUID id) { return queue.succeed(id); }
    @PostMapping("/{id}/fail") public WorkItemEntity fail(@PathVariable UUID id, @RequestBody(required=false) WorkItemFailureRequest request) { return queue.fail(id, request == null ? null : request.detail()); }
    @PostMapping("/{id}/pause") public WorkItemEntity pause(@PathVariable UUID id) { return queue.pause(id); }
    @PostMapping("/{id}/resume") public WorkItemEntity resume(@PathVariable UUID id) { return queue.resume(id); }
    @PostMapping("/{id}/cancel") public WorkItemEntity cancel(@PathVariable UUID id) { return queue.cancel(id); }
}
