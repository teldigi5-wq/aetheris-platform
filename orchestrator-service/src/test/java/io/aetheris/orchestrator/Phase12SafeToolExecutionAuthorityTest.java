package io.aetheris.orchestrator;

import io.aetheris.orchestrator.agent.RiskLevel;
import io.aetheris.orchestrator.approval.ApprovalService;
import io.aetheris.orchestrator.execution.CommandSandboxService;
import io.aetheris.orchestrator.execution.InvocationAuditEntity;
import io.aetheris.orchestrator.execution.InvocationAuditService;
import io.aetheris.orchestrator.execution.ToolExecutionRequest;
import io.aetheris.orchestrator.execution.ToolExecutionService;
import io.aetheris.orchestrator.execution.ToolExecutionStatus;
import io.aetheris.orchestrator.execution.WorkspaceSandboxService;
import io.aetheris.orchestrator.github.GitHubAdapterService;
import io.aetheris.orchestrator.policy.CompiledPolicyDecision;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.task.DirectExecutionAuthorityService;
import io.aetheris.orchestrator.task.TaskControlService;
import io.aetheris.orchestrator.task.TaskEntity;
import io.aetheris.orchestrator.tool.SafeToolRegistryService;
import io.aetheris.orchestrator.tool.SpecialistToolAuthorizationService;
import io.aetheris.orchestrator.tool.ToolAccessDecision;
import io.aetheris.orchestrator.tool.ToolAccessRequest;
import io.aetheris.orchestrator.tool.ToolDescriptor;
import io.aetheris.orchestrator.tool.ToolTransport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class Phase12SafeToolExecutionAuthorityTest {

    @Test
    void mismatchedSpecialistBlocksBeforePolicyOrToolAdapter() {
        Fixture fixture = fixture(writeTool());
        UUID taskId = UUID.randomUUID();
        ToolExecutionRequest request = new ToolExecutionRequest(
                taskId, "frontend-engineer", "files.write-workspace", OperationMode.BALANCED,
                Map.of("path", "blocked.txt", "content", "blocked"));

        when(fixture.specialistTools.toolFamilyFor("files.write-workspace")).thenReturn("filesystem");
        when(fixture.directAuthority.requireRunningSpecialist(
                taskId, "frontend-engineer", "filesystem", OperationMode.BALANCED))
                .thenThrow(new IllegalStateException(
                        "Direct execution agent frontend-engineer is not the task's active specialist backend-engineer"));

        var response = fixture.service.execute(request);

        assertThat(response.status()).isEqualTo(ToolExecutionStatus.BLOCKED);
        assertThat(response.detail()).contains("Direct tool execution authority denied")
                .contains("not the task's active specialist");
        verify(fixture.registry, never()).evaluate(any());
        verifyNoInteractions(fixture.workspace, fixture.commands, fixture.github, fixture.approvals);
    }

    @Test
    void durableTaskModeDrivesPolicyWhenCallerDoesNotSupplyMode() {
        Fixture fixture = fixture(readTool());
        UUID taskId = UUID.randomUUID();
        TaskEntity task = mock(TaskEntity.class);
        when(task.getMode()).thenReturn(OperationMode.PRIVATE);
        when(fixture.specialistTools.toolFamilyFor("files.read-workspace")).thenReturn("filesystem");
        when(fixture.directAuthority.requireRunningSpecialist(taskId, "backend-engineer", "filesystem"))
                .thenReturn(task);
        when(fixture.registry.evaluate(any())).thenReturn(allowed(readTool()));

        var response = fixture.service.execute(new ToolExecutionRequest(
                taskId, "backend-engineer", "files.read-workspace", null,
                Map.of("path", "evidence.txt")));

        assertThat(response.status()).isEqualTo(ToolExecutionStatus.SUCCEEDED);
        ArgumentCaptor<ToolAccessRequest> policyRequest = ArgumentCaptor.forClass(ToolAccessRequest.class);
        verify(fixture.registry).evaluate(policyRequest.capture());
        assertThat(policyRequest.getValue().mode()).isEqualTo(OperationMode.PRIVATE);
        assertThat(policyRequest.getValue().metadata()).containsEntry("durableTaskMode", "PRIVATE");
        verify(fixture.workspace).read("evidence.txt");
    }

    @Test
    void ownerApprovalStillFollowsSpecialistAuthorityBeforeMutation() {
        Fixture fixture = fixture(writeTool());
        UUID taskId = UUID.randomUUID();
        TaskEntity task = mock(TaskEntity.class);
        when(task.getMode()).thenReturn(OperationMode.BALANCED);
        when(fixture.specialistTools.toolFamilyFor("files.write-workspace")).thenReturn("filesystem");
        when(fixture.directAuthority.requireRunningSpecialist(
                taskId, "backend-engineer", "filesystem", OperationMode.BALANCED)).thenReturn(task);
        when(fixture.registry.evaluate(any())).thenReturn(requiresApproval(writeTool()));
        when(fixture.approvals.hasApproved(taskId, "tool:files.write-workspace")).thenReturn(false);

        var response = fixture.service.execute(new ToolExecutionRequest(
                taskId, "backend-engineer", "files.write-workspace", OperationMode.BALANCED,
                Map.of("path", "proposal.txt", "content", "draft")));

        assertThat(response.status()).isEqualTo(ToolExecutionStatus.APPROVAL_REQUIRED);
        var order = inOrder(fixture.directAuthority, fixture.approvals, fixture.workspace);
        order.verify(fixture.directAuthority).requireRunningSpecialist(
                taskId, "backend-engineer", "filesystem", OperationMode.BALANCED);
        order.verify(fixture.approvals).hasApproved(taskId, "tool:files.write-workspace");
        verifyNoInteractions(fixture.workspace);
    }

    private Fixture fixture(ToolDescriptor tool) {
        SafeToolRegistryService registry = mock(SafeToolRegistryService.class);
        SpecialistToolAuthorizationService specialistTools = mock(SpecialistToolAuthorizationService.class);
        DirectExecutionAuthorityService directAuthority = mock(DirectExecutionAuthorityService.class);
        WorkspaceSandboxService workspace = mock(WorkspaceSandboxService.class);
        CommandSandboxService commands = mock(CommandSandboxService.class);
        GitHubAdapterService github = mock(GitHubAdapterService.class);
        ApprovalService approvals = mock(ApprovalService.class);
        InvocationAuditService audit = mock(InvocationAuditService.class);
        TaskControlService control = mock(TaskControlService.class);
        InvocationAuditEntity auditEntry = mock(InvocationAuditEntity.class);
        UUID auditId = UUID.randomUUID();

        when(registry.getRequired(tool.id())).thenReturn(tool);
        when(audit.start(any(), any(), any(), eq(tool.id()), anyMap())).thenReturn(auditEntry);
        when(auditEntry.getId()).thenReturn(auditId);
        when(auditEntry.getTargetId()).thenReturn(tool.id());
        when(control.isEmergencyStopActive()).thenReturn(false);

        ToolExecutionService service = new ToolExecutionService(
                registry, specialistTools, directAuthority, workspace, commands, github,
                approvals, audit, control);
        return new Fixture(service, registry, specialistTools, directAuthority, workspace,
                commands, github, approvals);
    }

    private ToolDescriptor readTool() {
        return new ToolDescriptor(
                "files.read-workspace", "Workspace Read", ToolTransport.FILESYSTEM,
                Set.of("workspace:read"), RiskLevel.LOW, true, false);
    }

    private ToolDescriptor writeTool() {
        return new ToolDescriptor(
                "files.write-workspace", "Workspace Write", ToolTransport.FILESYSTEM,
                Set.of("workspace:write"), RiskLevel.MEDIUM, false, false);
    }

    private ToolAccessDecision allowed(ToolDescriptor tool) {
        return new ToolAccessDecision(tool,
                new CompiledPolicyDecision(true, false, false, false, List.of(), List.of()));
    }

    private ToolAccessDecision requiresApproval(ToolDescriptor tool) {
        return new ToolAccessDecision(tool,
                new CompiledPolicyDecision(true, true, false, false, List.of(), List.of()));
    }

    private record Fixture(
            ToolExecutionService service,
            SafeToolRegistryService registry,
            SpecialistToolAuthorizationService specialistTools,
            DirectExecutionAuthorityService directAuthority,
            WorkspaceSandboxService workspace,
            CommandSandboxService commands,
            GitHubAdapterService github,
            ApprovalService approvals) {}
}
