package io.aetheris.orchestrator.host;
import java.util.Map;
import java.util.UUID;
public record HostCommandRequest(UUID hostId,String capability,String action,Map<String,Object> arguments) {}
