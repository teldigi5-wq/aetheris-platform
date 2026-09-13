package io.aetheris.orchestrator.scheduler;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.*;
public interface SchedulerTicketRepository extends JpaRepository<SchedulerTicketEntity,UUID>{
    Optional<SchedulerTicketEntity> findByWorkItemId(UUID workItemId);
    List<SchedulerTicketEntity> findTop200ByStateOrderByPriorityDescCreatedAtAsc(SchedulerTicketState state);
    long countByState(SchedulerTicketState state);
}
