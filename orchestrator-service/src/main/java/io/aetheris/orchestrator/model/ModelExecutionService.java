package io.aetheris.orchestrator.model;

import io.aetheris.orchestrator.agent.AgentCatalogService;
import io.aetheris.orchestrator.execution.InvocationAuditEntity;
import io.aetheris.orchestrator.execution.InvocationAuditService;
import io.aetheris.orchestrator.execution.InvocationKind;
import io.aetheris.orchestrator.execution.InvocationStatus;
import io.aetheris.orchestrator.task.TaskControlService;
import io.aetheris.orchestrator.task.TaskService;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ModelExecutionService {

    private final ModelRouterService router;
    private final ModelProviderRegistry providers;
    private final ProviderUsageService usage;
    private final InvocationAuditService audit;
    private final TaskControlService control;
    private final TaskService tasks;
    private final AgentCatalogService agents;

    public ModelExecutionService(
            ModelRouterService router,
            ModelProviderRegistry providers,
            ProviderUsageService usage,
            InvocationAuditService audit,
            TaskControlService control,
            TaskService tasks,
            AgentCatalogService agents) {
        this.router = router;
        this.providers = providers;
        this.usage = usage;
        this.audit = audit;
        this.control = control;
        this.tasks = tasks;
        this.agents = agents;
    }

    public ModelExecutionResponse execute(ModelExecutionRequest request) {
        if (request.prompt() == null || request.prompt().isBlank()) throw new IllegalArgumentException("Model prompt is required");
        agents.getRequired(request.agentId());
        if (request.taskId() != null) tasks.getRequired(request.taskId());

        ModelRouteDecision route = router.route(new ModelRouteRequest(
                request.modelClass(), request.mode(), request.protectedData(), request.allowPaid()));
        InvocationAuditEntity entry = audit.start(
                request.taskId(), request.agentId(), InvocationKind.MODEL,
                route.providerId() == null ? "unrouted" : route.providerId(),
                Map.of("modelClass", request.modelClass() == null ? "GENERAL" : request.modelClass().name(),
                        "mode", request.mode() == null ? "BALANCED" : request.mode().name()));

        if (control.isEmergencyStopActive()) {
            audit.finish(entry.getId(), InvocationStatus.CANCELLED, "Emergency stop is active", Map.of());
            return new ModelExecutionResponse(ModelExecutionStatus.CANCELLED, null, null, null, "Emergency stop is active", entry.getId());
        }
        if (!route.routable()) {
            audit.finish(entry.getId(), InvocationStatus.BLOCKED, route.reason(), Map.of());
            return new ModelExecutionResponse(ModelExecutionStatus.BLOCKED, null, null, null, route.reason(), entry.getId());
        }

        ModelProviderAdapter provider = providers.getRequired(route.providerId());
        ProviderBudgetSnapshot budget = usage.check(provider, request.prompt().length());
        if (!budget.allowed()) {
            audit.finish(entry.getId(), InvocationStatus.BLOCKED, budget.detail(), Map.of(
                    "provider", provider.id(),
                    "unitsToday", budget.unitsToday(),
                    "estimatedCostUsdToday", budget.estimatedCostUsdToday().toPlainString()));
            return new ModelExecutionResponse(ModelExecutionStatus.BLOCKED, provider.id(), route.model(), null, budget.detail(), entry.getId());
        }

        try {
            LocalGenerateResponse generated = provider.generate(new LocalGenerateRequest(
                    request.prompt(), request.model() == null || request.model().isBlank() ? route.model() : request.model()));
            long units = Math.max(1, request.prompt().length() + (generated.response() == null ? 0 : generated.response().length()));
            ProviderUsageEntity recorded = usage.record(provider, request.taskId(), units);
            audit.finish(entry.getId(), InvocationStatus.SUCCEEDED, "Model invocation completed", Map.of(
                    "provider", provider.id(),
                    "model", generated.model(),
                    "usageUnits", recorded.getUnits(),
                    "estimatedCostUsd", recorded.getEstimatedCostUsd().toPlainString()));
            return new ModelExecutionResponse(ModelExecutionStatus.SUCCEEDED, generated.provider(), generated.model(), generated.response(), "Model invocation completed", entry.getId());
        } catch (RuntimeException exception) {
            audit.finish(entry.getId(), InvocationStatus.FAILED, safeMessage(exception), Map.of());
            return new ModelExecutionResponse(ModelExecutionStatus.FAILED, route.providerId(), route.model(), null, safeMessage(exception), entry.getId());
        }
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
