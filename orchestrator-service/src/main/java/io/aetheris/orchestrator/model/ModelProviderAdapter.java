package io.aetheris.orchestrator.model;

import io.aetheris.orchestrator.agent.ModelClass;

import java.util.Set;

public interface ModelProviderAdapter {
    String id();
    boolean local();
    boolean zeroCost();
    Set<ModelClass> supportedClasses();
    ModelProviderSnapshot snapshot();
    LocalGenerateResponse generate(LocalGenerateRequest request);
}
