package io.aetheris.orchestrator.scheduler;
import jakarta.validation.constraints.*;
public record WorkerHeartbeatRequest(@NotBlank String workerId,@Min(1) @Max(64) int maxConcurrency,@Min(0) int activeLeases){}
