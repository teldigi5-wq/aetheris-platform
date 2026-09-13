package io.aetheris.orchestrator.model;

import io.aetheris.orchestrator.agent.ModelClass;
import io.aetheris.orchestrator.policy.OperationMode;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class ModelProviderRegistry {

    private final List<ModelProviderAdapter> adapters;

    public ModelProviderRegistry(List<ModelProviderAdapter> adapters) {
        this.adapters = List.copyOf(adapters);
    }

    public List<ModelProviderSnapshot> snapshots() {
        return adapters.stream().map(ModelProviderAdapter::snapshot).toList();
    }

    public ModelProviderAdapter getRequired(String providerId) {
        return adapters.stream()
                .filter(adapter -> adapter.id().equals(providerId))
                .findFirst()
                .orElseThrow(() -> new NoSuchElementException("Unknown model provider: " + providerId));
    }

    public ModelProviderAdapter select(ModelRouteRequest request) {
        OperationMode mode = request.mode() == null ? OperationMode.BALANCED : request.mode();
        ModelClass modelClass = request.modelClass() == null ? ModelClass.GENERAL : request.modelClass();

        return adapters.stream()
                .filter(adapter -> adapter.supportedClasses().contains(modelClass))
                .filter(adapter -> adapter.snapshot().available())
                .filter(adapter -> !(mode == OperationMode.PRIVATE || request.protectedData()) || adapter.local())
                .filter(adapter -> !(mode == OperationMode.ZERO_COST || !request.allowPaid()) || adapter.zeroCost())
                .max(Comparator.comparingInt(adapter -> score(adapter, mode)))
                .orElse(null);
    }

    private int score(ModelProviderAdapter adapter, OperationMode mode) {
        int score = 0;
        if (adapter.local()) score += 30;
        if (adapter.zeroCost()) score += 20;
        if (mode == OperationMode.TURBO && !adapter.local()) score += 5;
        if (mode == OperationMode.PRIVATE && adapter.local()) score += 100;
        if (mode == OperationMode.ZERO_COST && adapter.zeroCost()) score += 100;
        return score;
    }
}
