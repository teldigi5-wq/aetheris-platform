package io.aetheris.orchestrator.workflow;

import io.aetheris.orchestrator.agent.RiskLevel;
import io.aetheris.orchestrator.approval.ApprovalService;
import io.aetheris.orchestrator.approval.CreateApprovalRequest;
import io.aetheris.orchestrator.policy.CompiledPolicyDecision;
import io.aetheris.orchestrator.policy.OwnerRuleCompilerService;
import io.aetheris.orchestrator.policy.PolicyEvaluationRequest;
import io.aetheris.orchestrator.task.CreateTaskRequest;
import io.aetheris.orchestrator.task.TaskEntity;
import io.aetheris.orchestrator.task.TaskService;
import io.aetheris.orchestrator.task.TaskState;
import io.aetheris.orchestrator.task.TaskTransitionRequest;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
public class EngineeringWorkflowService {

    private final EngineeringWorkflowRepository repository;
    private final TaskService tasks;
    private final OwnerRuleCompilerService policy;
    private final ApprovalService approvals;

    public EngineeringWorkflowService(
            EngineeringWorkflowRepository repository,
            TaskService tasks,
            OwnerRuleCompilerService policy,
            ApprovalService approvals) {
        this.repository = repository;
        this.tasks = tasks;
        this.policy = policy;
        this.approvals = approvals;
    }

    @Transactional
    public EngineeringWorkflowEntity start(EngineeringWorkflowRequest request) {
        TaskEntity task = tasks.create(new CreateTaskRequest(request.title(), request.command(), request.mode()));
        task = tasks.transition(task.getId(), new TaskTransitionRequest(
                TaskState.PLANNING,
                "executive-planner",
                "Planning engineering, QA and independent verification workflow"));

        CompiledPolicyDecision decision = policy.evaluate(new PolicyEvaluationRequest(
                "executive-planner",
                "workflow.engineering-review",
                "software-engineering",
                RiskLevel.MEDIUM,
                false,
                false,
                task.getMode(),
                Map.of("workflow", "engineering-qa-verifier")));

        if (!decision.allowed()) {
            tasks.transition(task.getId(), new TaskTransitionRequest(
                    TaskState.CANCELLED,
                    "executive-planner",
                    "Workflow blocked by deterministic policy"));
            return repository.save(new EngineeringWorkflowEntity(
                    UUID.randomUUID(), task.getId(), EngineeringWorkflowPhase.CANCELLED));
        }

        EngineeringWorkflowEntity workflow;
        if (decision.requiresApproval()) {
            workflow = repository.save(new EngineeringWorkflowEntity(
                    UUID.randomUUID(), task.getId(), EngineeringWorkflowPhase.AWAITING_APPROVAL));
            approvals.request(new CreateApprovalRequest(
                    task.getId(),
                    "workflow.engineering-review",
                    "Run Engineering -> QA -> independent Software Architect verification workflow",
                    RiskLevel.MEDIUM));
        } else {
            tasks.transition(task.getId(), new TaskTransitionRequest(
                    TaskState.RUNNING,
                    "backend-engineer",
                    "Engineering phase started"));
            workflow = repository.save(new EngineeringWorkflowEntity(
                    UUID.randomUUID(), task.getId(), EngineeringWorkflowPhase.ENGINEERING));
        }
        return workflow;
    }

    public EngineeringWorkflowEntity getRequired(UUID id) {
        return repository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Unknown engineering workflow: " + id));
    }

    public List<EngineeringWorkflowEntity> recent() {
        return repository.findTop50ByOrderByUpdatedAtDesc();
    }

    @Transactional
    public EngineeringWorkflowEntity advance(UUID id) {
        EngineeringWorkflowEntity workflow = getRequired(id);
        TaskEntity task = tasks.getRequired(workflow.getTaskId());

        switch (workflow.getPhase()) {
            case AWAITING_APPROVAL -> {
                if (task.getState() == TaskState.CANCELLED) {
                    workflow.moveTo(EngineeringWorkflowPhase.CANCELLED);
                } else if (task.getState() == TaskState.RUNNING) {
                    tasks.recordProgress(task.getId(), "backend-engineer", "Owner approval received; engineering phase activated",
                            Map.of("workflowPhase", "ENGINEERING"));
                    workflow.moveTo(EngineeringWorkflowPhase.ENGINEERING);
                } else {
                    throw new IllegalStateException("Workflow is still waiting for owner approval");
                }
            }
            case ENGINEERING -> {
                tasks.recordProgress(task.getId(), "backend-engineer", "Engineering handoff prepared for QA",
                        Map.of("workflowPhase", "ENGINEERING", "next", "QA"));
                tasks.transition(task.getId(), new TaskTransitionRequest(
                        TaskState.VERIFYING,
                        "qa-engineer",
                        "QA phase started"));
                workflow.moveTo(EngineeringWorkflowPhase.QA);
            }
            case QA -> {
                tasks.recordProgress(task.getId(), "qa-engineer", "QA evidence handed to independent verifier",
                        Map.of("workflowPhase", "QA", "next", "VERIFICATION"));
                tasks.recordProgress(task.getId(), "software-architect", "Independent architecture verification started",
                        Map.of("workflowPhase", "VERIFICATION"));
                workflow.moveTo(EngineeringWorkflowPhase.VERIFICATION);
            }
            case VERIFICATION -> {
                tasks.recordProgress(task.getId(), "software-architect", "Independent verification completed",
                        Map.of("workflowPhase", "VERIFICATION"));
                tasks.transition(task.getId(), new TaskTransitionRequest(
                        TaskState.COMPLETED,
                        "software-architect",
                        "Engineering workflow completed after QA and independent verification"));
                workflow.moveTo(EngineeringWorkflowPhase.COMPLETED);
            }
            case COMPLETED, CANCELLED -> throw new IllegalStateException("Workflow is already terminal: " + workflow.getPhase());
        }

        return repository.save(workflow);
    }
}
