package io.aetheris.orchestrator.syntracore;

import java.util.List;

/**
 * Optional extension for local runtimes that can truthfully expose adapter-derived
 * model candidates after Slice 9 has verified activation of the exact artifact.
 */
public interface AdapterAwareSyntraModelRuntime extends SyntraModelRuntime {
    List<AdapterRuntimeRegistration> adapterRegistrations();
}
