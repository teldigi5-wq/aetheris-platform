package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.host.HostNodeEntity;
import io.aetheris.orchestrator.host.HostRegistrationRequest;
import io.aetheris.orchestrator.host.HostRegistryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orchestrator/hosts")
public class HostRegistryController {
    private final HostRegistryService hosts;
    public HostRegistryController(HostRegistryService hosts){this.hosts=hosts;}
    @PostMapping public HostNodeEntity register(@Valid @RequestBody HostRegistrationRequest request){return hosts.register(request);}
    @GetMapping public List<HostNodeEntity> list(){return hosts.list();}
    @GetMapping("/{id}") public HostNodeEntity get(@PathVariable UUID id){return hosts.getRequired(id);}
}
