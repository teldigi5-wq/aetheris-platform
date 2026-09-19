package io.aetheris.orchestrator;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.aetheris.orchestrator.approval.ApprovalEntity;
import io.aetheris.orchestrator.approval.ApprovalService;
import io.aetheris.orchestrator.connector.ConnectorConnectionEntity;
import io.aetheris.orchestrator.connector.ConnectorConnectionRepository;
import io.aetheris.orchestrator.connector.ConnectorProvider;
import io.aetheris.orchestrator.connector.ConnectorStatus;
import io.aetheris.orchestrator.connector.action.*;
import io.aetheris.orchestrator.connector.oauth.*;
import io.aetheris.orchestrator.task.DirectExecutionAuthorityService;
import io.aetheris.orchestrator.task.TaskEntity;
import io.aetheris.orchestrator.task.TaskService;
import io.aetheris.orchestrator.task.TaskVerificationService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class Phase12ProviderAccountContinuityTest {

    @Test
    void liveActionSnapshotsCurrentProviderAccountBeforeApproval() {
        ConnectorActionRepository actions = mock(ConnectorActionRepository.class);
        ConnectorConnectionRepository connections = mock(ConnectorConnectionRepository.class);
        TaskService tasks = mock(TaskService.class);
        ApprovalService approvals = mock(ApprovalService.class);
        LiveConnectorWriteExecutor liveExecutor = mock(LiveConnectorWriteExecutor.class);
        TaskVerificationService verification = mock(TaskVerificationService.class);
        DirectExecutionAuthorityService authority = mock(DirectExecutionAuthorityService.class);
        ProviderAccountContinuityService continuity = mock(ProviderAccountContinuityService.class);
        ConnectorConnectionEntity connection = mock(ConnectorConnectionEntity.class);
        TaskEntity task = mock(TaskEntity.class);
        ApprovalEntity approval = mock(ApprovalEntity.class);
        UUID connectionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();
        UUID approvalId = UUID.randomUUID();
        String fingerprint = "0123456789abcdef0123456789abcdef";

        when(actions.findByConnectionIdAndIdempotencyKey(connectionId, "continuity-live")).thenReturn(Optional.empty());
        when(connections.findById(connectionId)).thenReturn(Optional.of(connection));
        when(connection.getId()).thenReturn(connectionId);
        when(connection.getProvider()).thenReturn(ConnectorProvider.GITHUB);
        when(connection.getStatus()).thenReturn(ConnectorStatus.ENABLED);
        when(continuity.snapshot(connectionId, ConnectorProvider.GITHUB)).thenReturn(fingerprint);
        when(tasks.create(any())).thenReturn(task);
        when(task.getId()).thenReturn(taskId);
        when(tasks.transition(eq(taskId), any())).thenReturn(task);
        when(approvals.request(any())).thenReturn(approval);
        when(approval.getId()).thenReturn(approvalId);
        when(actions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ConnectorActionService service = new ConnectorActionService(
                actions, connections, tasks, approvals, liveExecutor, verification, authority, continuity, true);
        service.create(new CreateConnectorActionRequest(
                connectionId, ConnectorActionKind.GITHUB_CREATE_ISSUE, "continuity-live",
                "example/repo", "Create continuity proof issue", ConnectorActionExecutionMode.LIVE));

        ArgumentCaptor<ConnectorActionEntity> saved = ArgumentCaptor.forClass(ConnectorActionEntity.class);
        verify(actions).save(saved.capture());
        assertThat(saved.getValue().getAccountFingerprint()).isEqualTo(fingerprint);
        verify(continuity).snapshot(connectionId, ConnectorProvider.GITHUB);
    }

    @Test
    void syntheticActionDoesNotRequireProviderAccountIdentity() {
        ConnectorActionRepository actions = mock(ConnectorActionRepository.class);
        ConnectorConnectionRepository connections = mock(ConnectorConnectionRepository.class);
        TaskService tasks = mock(TaskService.class);
        ApprovalService approvals = mock(ApprovalService.class);
        LiveConnectorWriteExecutor liveExecutor = mock(LiveConnectorWriteExecutor.class);
        TaskVerificationService verification = mock(TaskVerificationService.class);
        DirectExecutionAuthorityService authority = mock(DirectExecutionAuthorityService.class);
        ProviderAccountContinuityService continuity = mock(ProviderAccountContinuityService.class);
        ConnectorConnectionEntity connection = mock(ConnectorConnectionEntity.class);
        TaskEntity task = mock(TaskEntity.class);
        ApprovalEntity approval = mock(ApprovalEntity.class);
        UUID connectionId = UUID.randomUUID();
        UUID taskId = UUID.randomUUID();

        when(actions.findByConnectionIdAndIdempotencyKey(connectionId, "continuity-synthetic")).thenReturn(Optional.empty());
        when(connections.findById(connectionId)).thenReturn(Optional.of(connection));
        when(connection.getId()).thenReturn(connectionId);
        when(connection.getProvider()).thenReturn(ConnectorProvider.GITHUB);
        when(connection.getStatus()).thenReturn(ConnectorStatus.ENABLED);
        when(tasks.create(any())).thenReturn(task);
        when(task.getId()).thenReturn(taskId);
        when(tasks.transition(eq(taskId), any())).thenReturn(task);
        when(approvals.request(any())).thenReturn(approval);
        when(approval.getId()).thenReturn(UUID.randomUUID());
        when(actions.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ConnectorActionService service = new ConnectorActionService(
                actions, connections, tasks, approvals, liveExecutor, verification, authority, continuity, false);
        service.create(new CreateConnectorActionRequest(
                connectionId, ConnectorActionKind.GITHUB_CREATE_ISSUE, "continuity-synthetic",
                "example/repo", "Synthetic compatibility proof", ConnectorActionExecutionMode.SYNTHETIC));

        verifyNoInteractions(continuity);
    }

    @Test
    void accountMismatchStopsBeforeCredentialResolutionOrProviderMutation() {
        ProviderCredentialRepository credentials = mock(ProviderCredentialRepository.class);
        InMemoryConnectorCredentialVault vault = mock(InMemoryConnectorCredentialVault.class);
        ProviderWriteEndpointRegistry endpoints = mock(ProviderWriteEndpointRegistry.class);
        ProviderAccountContinuityService continuity = mock(ProviderAccountContinuityService.class);
        ConnectorActionEntity action = new ConnectorActionEntity(
                UUID.randomUUID(), UUID.randomUUID(), ConnectorProvider.GITHUB,
                ConnectorActionKind.GITHUB_CREATE_ISSUE, ConnectorActionExecutionMode.LIVE,
                "rebind-proof", "example/repo", "Account rebind proof", UUID.randomUUID(), UUID.randomUUID(),
                "CONNECTOR_WRITE_GITHUB_CREATE_ISSUE", "0123456789abcdef0123456789abcdef");
        doThrow(new ConnectorActionBlockedException("Provider account changed after this live connector action was approved"))
                .when(continuity).assertCurrent(action);

        LiveConnectorWriteExecutor executor = new LiveConnectorWriteExecutor(
                credentials, vault, endpoints, new ObjectMapper(), continuity);

        assertThatThrownBy(() -> executor.execute(action))
                .isInstanceOf(ConnectorActionBlockedException.class)
                .hasMessageContaining("Provider account changed");
        verify(continuity).assertCurrent(action);
        verifyNoInteractions(credentials, vault, endpoints);
    }
}
