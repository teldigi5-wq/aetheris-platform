package io.aetheris.audit;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/events")
public class AuditController {
    private final AuditEventStore store;

    public AuditController(AuditEventStore store) {
        this.store = store;
    }

    @GetMapping
    public List<UserDomainEvent> recentEvents() {
        return store.recent();
    }
}
