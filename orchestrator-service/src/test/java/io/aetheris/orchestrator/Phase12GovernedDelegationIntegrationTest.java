package io.aetheris.orchestrator;

import io.aetheris.orchestrator.mission.CreateMissionRequest;
import io.aetheris.orchestrator.mission.MissionService;
import io.aetheris.orchestrator.mission.MissionSessionView;
import io.aetheris.orchestrator.planner.MissionPlanNodeEntity;
import io.aetheris.orchestrator.planner.MissionPlannerService;
import io.aetheris.orchestrator.planner.PlanMissionRequest;
import io.aetheris.orchestrator.planner.PlanStepRequest;
import io.aetheris.orchestrator.policy.OperationMode;
import io.aetheris.orchestrator.task.CreateTaskRequest;
import io.aetheris.orchestrator.task.TaskDelegationService;
import io.aetheris.orchestrator.task.TaskEntity;
import io.aetheris.orchestrator.task.TaskEventStreamService;
import io.aetheris.orchestrator.task.TaskService;
import io.aetheris.orchestrator.task.TaskState;
import io.aetheris.orchestrator.task.TaskTransitionRequest;
import io.aetheris.orchestrator.tool.SafeToolRegistryService;
import io.aetheris.orchestrator.tool.ToolAccessDecision;
import io.aetheris.orchestrator.tool.ToolAccessRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class Phase12GovernedDelegationIntegrationTest {

    @Autowired TaskService tasks;
    @Autowired TaskDelegationService delegations;
    @Autowired TaskEventStreamService events;
    @Autowired SafeToolRegistryService tools;
    @Autowired MissionService missions;
    @Autowired MissionPlannerService planner;

    @Test
    void executivePlannerCanDelegateAndDelegationIsDurablyAudited() {
        TaskEntity task = tasks.create(new CreateTaskRequest(
                "Governed delegation",
                "Delegate research without transferring authority",
                OperationMode.BALANCED));
        tasks.transition(task.getId(), new TaskTransitionRequest(
                TaskState.PLANNING,
                "executive-planner",
                "Executive planner prepared specialist assignment"));

        TaskEntity running = tasks.transition(task.getId(), new TaskTransitionRequest(
                TaskState.RUNNING,
                "research-scientist",
                "Research specialist accepted governed assignment"));

        assertThat(running.getState()).isEqualTo(TaskState.RUNNING);
        assertThat(running.getActiveAgentId()).isEqualTo("research-scientist");
        assertThat(events.history(task.getId()))
                .anySatisfy(event -> assertThat(event.metadata())
                        .containsEntry("delegationDecision", "ALLOW")
                        .containsEntry("delegationStage", "ACTIVATED")
                        .containsEntry("delegatorId", "executive-planner")
                        .containsEntry("delegateId", "research-scientist")
                        .containsEntry("authorityTransferred", false)
                        .containsEntry("verificationRequired", true)
                        .containsEntry("taskMode", "BALANCED"));
    }

    @Test
    void delegationDoesNotExpandDelegateToolAuthority() {
        TaskEntity task = tasks.create(new CreateTaskRequest(
                "No privilege transfer",
                "Research specialist remains limited to its own tool families",
                OperationMode.BALANCED));
        tasks.transition(task.getId(), new TaskTransitionRequest(
                TaskState.PLANNING,
                "executive-planner",
                "Prepare research delegation"));
        tasks.transition(task.getId(), new TaskTransitionRequest(
                TaskState.RUNNING,
                "research-scientist",
                "Activate research delegation"));

        ToolAccessDecision decision = tools.evaluate(new ToolAccessRequest(
                "research-scientist",
                "github.read",
                OperationMode.BALANCED,
                false,
                false,
                Map.of("delegatedTaskId", task.getId().toString())));

        assertThat(decision.policy().allowed()).isFalse();
        assertThat(decision.policy().requiresApproval()).isFalse();
        assertThat(decision.policy().reasons()).singleElement().asString()
                .contains("research-scientist")
                .contains("github");
    }

    @Test
    void parentPrivateModeSurvivesDelegationAndStillBlocksOffDeviceAccess() {
        TaskEntity task = tasks.create(new CreateTaskRequest(
                "Private delegated task",
                "Preserve parent policy mode across assignment",
                OperationMode.PRIVATE));
        tasks.transition(task.getId(), new TaskTransitionRequest(
                TaskState.PLANNING,
                "executive-planner",
                "Prepare private engineering assignment"));
        TaskEntity running = tasks.transition(task.getId(), new TaskTransitionRequest(
                TaskState.RUNNING,
                "backend-engineer",
                "Backend specialist accepted private assignment"));

        assertThat(running.getMode()).isEqualTo(OperationMode.PRIVATE);

        ToolAccessDecision decision = tools.evaluate(new ToolAccessRequest(
                "backend-engineer",
                "github.read",
                running.getMode(),
                false,
                true,
                Map.of("delegatedTaskId", task.getId().toString())));

        assertThat(decision.policy().allowed()).isFalse();
        assertThat(decision.policy().reasons()).anySatisfy(reason ->
                assertThat(reason).contains("PRIVATE mode"));
    }

    @Test
    void specialistWithoutDelegationCapabilityCannotEscalateToAnotherAgent() {
        TaskEntity task = tasks.create(new CreateTaskRequest(
                "Blocked delegation escalation",
                "Backend engineer must not assign a more privileged specialist",
                OperationMode.BALANCED));
        tasks.transition(task.getId(), new TaskTransitionRequest(
                TaskState.PLANNING,
                "backend-engineer",
                "Backend specialist owns planning state"));

        assertThatThrownBy(() -> tasks.transition(task.getId(), new TaskTransitionRequest(
                TaskState.RUNNING,
                "security-architect",
                "Attempt unauthorized specialist reassignment")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("does not have delegation capability");

        assertThat(tasks.getRequired(task.getId()).getState()).isEqualTo(TaskState.PLANNING);
    }

    @Test
    void unknownDelegateIdentityFailsClosed() {
        TaskEntity task = tasks.create(new CreateTaskRequest(
                "Unknown delegate",
                "Reject invented specialist identity",
                OperationMode.BALANCED));
        tasks.transition(task.getId(), new TaskTransitionRequest(
                TaskState.PLANNING,
                "executive-planner",
                "Prepare assignment"));

        assertThatThrownBy(() -> tasks.transition(task.getId(), new TaskTransitionRequest(
                TaskState.RUNNING,
                "invented-specialist",
                "Attempt invented delegate")))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Unknown agent");
    }

    @Test
    void selfDelegationIsRejected() {
        assertThatThrownBy(() -> delegations.assertPlannedAssignmentAllowed(
                "executive-planner",
                "executive-planner"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("cannot delegate work to itself");
    }

    @Test
    void missionPlannerRejectsUnknownSpecialistBeforeMaterializingTasks() {
        MissionSessionView mission = missions.create(new CreateMissionRequest(
                "Governed mission",
                "Reject unknown delegated specialist"));

        assertThatThrownBy(() -> planner.plan(mission.id(), new PlanMissionRequest(
                OperationMode.BALANCED,
                List.of(new PlanStepRequest(
                        "invalid",
                        "Invalid specialist",
                        "Collect evidence",
                        "invented-specialist",
                        50,
                        Set.of())))))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("Unknown agent");
    }

    @Test
    void missionPlannerPersistsDelegationLineageAndKeepsPlannerAsPlanningAuthority() {
        MissionSessionView mission = missions.create(new CreateMissionRequest(
                "Delegated mission",
                "Persist delegation lineage in the task journal"));
        List<MissionPlanNodeEntity> nodes = planner.plan(mission.id(), new PlanMissionRequest(
                OperationMode.ZERO_COST,
                List.of(new PlanStepRequest(
                        "research",
                        "Research evidence",
                        "Collect source-grounded evidence",
                        "research-scientist",
                        80,
                        Set.of()))));

        MissionPlanNodeEntity node = nodes.getFirst();
        assertThat(events.history(node.getTaskId()))
                .anySatisfy(event -> assertThat(event.metadata())
                        .containsEntry("delegationDecision", "ALLOW")
                        .containsEntry("delegationStage", "PLANNED")
                        .containsEntry("delegatorId", "executive-planner")
                        .containsEntry("delegateId", "research-scientist")
                        .containsEntry("authorityTransferred", false)
                        .containsEntry("taskMode", "ZERO_COST"));

        planner.releaseReady(mission.id());
        TaskEntity releasedTask = tasks.getRequired(node.getTaskId());
        assertThat(releasedTask.getState()).isEqualTo(TaskState.PLANNING);
        assertThat(releasedTask.getActiveAgentId()).isEqualTo("executive-planner");
    }
}
