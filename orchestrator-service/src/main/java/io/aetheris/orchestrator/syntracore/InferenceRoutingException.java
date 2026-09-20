package io.aetheris.orchestrator.syntracore;

import java.util.List;
import java.util.Objects;

public final class InferenceRoutingException extends IllegalStateException {
    private final List<String> rejectionReasons;

    public InferenceRoutingException(List<String> rejectionReasons) {
        super("no compatible local inference route: " + Objects.requireNonNull(rejectionReasons, "rejectionReasons"));
        this.rejectionReasons = List.copyOf(rejectionReasons);
    }

    public List<String> rejectionReasons() {
        return rejectionReasons;
    }
}
