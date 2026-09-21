package io.aetheris.orchestrator.syntracore;

import java.util.Optional;

/**
 * Stronger adapter runtime contract that binds the exact Slice 13 invocation
 * lease to a single-use runtime-owned stream handle.
 */
public interface AdapterInvocationHandleRuntime extends AdapterInvocationLeasingRuntime {

    Optional<AdapterRuntimeInvocationHandle> acquireAdapterInvocationHandle(
            AdapterRuntimeRegistration registration);
}
