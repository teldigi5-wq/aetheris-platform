package io.aetheris.orchestrator.execution;

import io.aetheris.orchestrator.approval.ApprovalService;
import io.aetheris.orchestrator.github.GitHubAdapterService;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.task.DirectExecutionAuthorityService;
import io.aetheris.orchestrator.task.TaskControlService;
import io.aetheris.orchestrator.task.TaskEntity;
import io.aetheris.orchestrator.tool.SafeToolRegistryService;
import io.aetheris.orchestrator.tool.SpecialistToolAuthorizationService;
import io.aetheris.orchestrator.tool.ToolAccessDecision;
import io.aetheris.orchestrator.tool.ToolAccessRequest;
import io.aetheris.orchestrator.tool.ToolDescriptor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class ToolExecutionService {

    private final SafeToolRegistryService registry;
    private final SpecialistToolAuthorizationService specialistTools;
    private final DirectExecutionAuthorityService directAuthority;
    private final WorkspaceSandboxService workspace;
    private final CommandSandboxService commands;
    private final GitHubAdapterService github;
    private final ApprovalService approvals;
    private final InvocationAuditService audit;
    private final TaskControlService control;

    public ToolExecutionService(
            SafeToolRegistryService registry,
            SpecialistToolAuthorizationService specialistTools,
            DirectExecutionAuthorityService directAuthority,
            WorkspaceSandboxService workspace,
            CommandSandboxService commands,
            GitHubAdapterService github,
            ApprovalService approvals,
            InvocationAuditService audit,
            TaskControlService control) {
        this.registry = registry;
        this.specialistTools = specialistTools;
        this.directAuthority = directAuthority;
        this.workspace = workspace;
        this.commands = commands;
        this.github = github;
        this.approvals = approvals;
        this.audit = audit;
        this.control = control;
    }

    public ToolExecutionResponse execute(ToolExecutionRequest request) {
        ToolDescriptor tool = registry.getRequired(request.toolId());
        String toolFamily = specialistTools.toolFamilyFor(tool.id());
        boolean offDevice = request.toolId() != null && request.toolId().startsWith("github.");
        InvocationAuditEntity entry = audit.start(
                request.taskId(), request.agentId(), InvocationKind.TOOL,
                request.toolId(), Map.of(
                        "offDevice", offDevice,
                        "claimedMode", request.mode() == null ? "UNSPECIFIED" : request.mode().name()));

        if (control.isEmergencyStopActive()) {
            return finish(entry, ToolExecutionStatus.CANCELLED, InvocationStatus.CANCELLED, "Emergency stop is active", null);
        }
        if (toolFamily == null) {
            return finish(entry, ToolExecutionStatus.BLOCKED, InvocationStatus.BLOCKED,
                    "No specialist tool-family mapping exists for registered tool " + tool.id(), null);
        }

        TaskEntity task;
        try {
            task = request.mode() == null
                    ? directAuthority.requireRunningSpecialist(request.taskId(), request.agentId(), toolFamily)
                    : directAuthority.requireRunningSpecialist(request.taskId(), request.agentId(), toolFamily, request.mode());
        } catch (RuntimeException exception) {
            return finish(entry, ToolExecutionStatus.BLOCKED, InvocationStatus.BLOCKED,
                    "Direct tool execution authority denied: " + safeMessage(exception), null);
        }

        ToolAccessDecision decision = registry.evaluate(new ToolAccessRequest(
                request.agentId(), request.toolId(), task.getMode(), false, offDevice,
                Map.of("execution", "stage4", "durableTaskMode", task.getMode().name())));
        if (!decision.policy().allowed()) {
            return finish(entry, ToolExecutionStatus.BLOCKED, InvocationStatus.BLOCKED,
                    String.join(" | ", decision.policy().reasons()), null);
        }
        String approvalAction = "tool:" + request.toolId();
        if (decision.policy().requiresApproval() && !approvals.hasApproved(request.taskId(), approvalAction)) {
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
