package io.aetheris.orchestrator.approval;

import io.aetheris.orchestrator.task.TaskService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class ApprovalService {

    private final ApprovalRepository repository;
    private final TaskService tasks;

    public ApprovalService(ApprovalRepository repository, TaskService tasks) {
        this.repository = repository;
        this.tasks = tasks;
    }

    @Transactional
    public ApprovalEntity request(CreateApprovalRequest request) {
        tasks.getRequired(request.taskId());
        ApprovalEntity approval = new ApprovalEntity(
                UUID.randomUUID(),
                request.taskId(),
                request.actionType(),
                request.summary(),
                request.riskLevel());
        return repository.save(approval);
    }

    public List<ApprovalEntity> pending() {
        return repository.findTop100ByStatusOrderByCreatedAtDesc(ApprovalStatus.PENDING);
    }

    public List<ApprovalEntity> forTask(UUID taskId) {
        tasks.getRequired(taskId);
        return repository.findTop100ByTaskIdOrderByCreatedAtDesc(taskId);
    }

    public ApprovalEntity getRequired(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Unknown approval: " + id));
    }

    @Transactional
    public ApprovalEntity decide(UUID id, ApprovalDecisionRequest request) {
        ApprovalEntity approval = getRequired(id);
        approval.decide(request.approved(), request.note());
        return repository.save(approval);
    }
}
