package io.aetheris.orchestrator.execution;

import io.aetheris.orchestrator.approval.ApprovalService;
import io.aetheris.orchestrator.github.GitHubAdapterService;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.task.TaskControlService;
import io.aetheris.orchestrator.task.TaskService;
import io.aetheris.orchestrator.tool.SafeToolRegistryService;
import io.aetheris.orchestrator.tool.ToolAccessDecision;
import io.aetheris.orchestrator.tool.ToolAccessRequest;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ToolExecutionService {

    private final SafeToolRegistryService registry;
    private final WorkspaceSandboxService workspace;
    private final CommandSandboxService commands;
    private final GitHubAdapterService github;
    private final ApprovalService approvals;
    private final InvocationAuditService audit;
    private final TaskControlService control;
    private final TaskService tasks;

    public ToolExecutionService(
            SafeToolRegistryService registry,
            WorkspaceSandboxService workspace,
            CommandSandboxService commands,
            GitHubAdapterService github,
            ApprovalService approvals,
            InvocationAuditService audit,
            TaskControlService control,
            TaskService tasks) {
        this.registry = registry;
        this.workspace = workspace;
        this.commands = commands;
        this.github = github;
        this.approvals = approvals;
        this.audit = audit;
        this.control = control;
        this.tasks = tasks;
    }

    public ToolExecutionResponse execute(ToolExecutionRequest request) {
        if (request.taskId() != null) tasks.getRequired(request.taskId());
        boolean offDevice = request.toolId() != null && request.toolId().startsWith("github.");
        ToolAccessDecision decision = registry.evaluate(new ToolAccessRequest(
                request.agentId(), request.toolId(), request.mode(), false, offDevice, Map.of("execution", "stage4")));
        InvocationAuditEntity entry = audit.start(
                request.taskId(), request.agentId(), InvocationKind.TOOL,
                request.toolId(), Map.of("offDevice", offDevice, "mode", effectiveMode(request.mode()).name()));

        if (control.isEmergencyStopActive()) {
            return finish(entry, ToolExecutionStatus.CANCELLED, InvocationStatus.CANCELLED, "Emergency stop is active", null);
        }
        if (!decision.policy().allowed()) {
            return finish(entry, ToolExecutionStatus.BLOCKED, InvocationStatus.BLOCKED,
                    String.join(" | ", decision.policy().reasons()), null);
        }
        String approvalAction = "tool:" + request.toolId();
        if (decision.policy().requiresApproval() && (request.taskId() == null || !approvals.hasApproved(request.taskId(), approvalAction))) {
            return finish(entry, ToolExecutionStatus.APPROVAL_REQUIRED, InvocationStatus.BLOCKED,
                    "Owner approval is required for " + approvalAction, null);
        }

        try {
            Object output = switch (request.toolId()) {
                case "files.read-workspace" -> workspace.read(requiredString(request.parameters(), "path"));
                case "files.write-workspace" -> workspace.write(
                        requiredString(request.parameters(), "path"), optionalString(request.parameters(), "content", ""));
                case "terminal.inspect", "terminal.execute-workspace" -> commands.execute(
                        stringList(request.parameters().get("command")),
                        optionalString(request.parameters(), "workingDirectory", ""),
                        optionalInteger(request.parameters().get("timeoutSeconds")));
                case "github.read" -> github.readFile(
                        requiredString(request.parameters(), "repository"),
                        requiredString(request.parameters(), "path"),
                        optionalString(request.parameters(), "ref", "main"));
                case "github.propose-change" -> github.propose(
                        request.taskId(), request.agentId(),
                        requiredString(request.parameters(), "repository"),
                        requiredString(request.parameters(), "path"),
                        optionalString(request.parameters(), "baseRef", "main"),
                        optionalString(request.parameters(), "content", ""),
                        optionalString(request.parameters(), "summary", "Aetheris proposed change"));
                default -> throw new IllegalArgumentException("No execution adapter is registered for tool: " + request.toolId());
            };
            return finish(entry, ToolExecutionStatus.SUCCEEDED, InvocationStatus.SUCCEEDED, "Tool invocation completed", output);
        } catch (RuntimeException exception) {
            return finish(entry, ToolExecutionStatus.FAILED, InvocationStatus.FAILED, safeMessage(exception), null);
        }
    }

    private ToolExecutionResponse finish(InvocationAuditEntity entry, ToolExecutionStatus status, InvocationStatus auditStatus, String detail, Object output) {
        audit.finish(entry.getId(), auditStatus, detail, Map.of("status", status.name()));
        return new ToolExecutionResponse(status, entry.getTargetId(), detail, output, entry.getId());
    }

    private OperationMode effectiveMode(OperationMode mode) {
        return mode == null ? OperationMode.BALANCED : mode;
    }

    private String requiredString(Map<String, Object> parameters, String key) {
        Object value = parameters.get(key);
        if (value == null || String.valueOf(value).isBlank()) throw new IllegalArgumentException("Missing required parameter: " + key);
        return String.valueOf(value);
    }

    private String optionalString(Map<String, Object> parameters, String key, String fallback) {
        Object value = parameters.get(key);
        return value == null ? fallback : String.valueOf(value);
    }

    private Integer optionalInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.intValue();
        return Integer.parseInt(String.valueOf(value));
    }

    private List<String> stringList(Object value) {
        if (!(value instanceof List<?> raw)) throw new IllegalArgumentException("command must be a JSON array of arguments");
        List<String> command = new ArrayList<>();
        for (Object part : raw) command.add(String.valueOf(part));
        return List.copyOf(command);
    }

    private String safeMessage(RuntimeException exception) {
        return exception.getMessage() == null || exception.getMessage().isBlank()
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
