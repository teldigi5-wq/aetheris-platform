package io.aetheris.orchestrator.model;

import io.aetheris.orchestrator.agent.ModelClass;
import io.aetheris.orchestrator.policy.OperationMode;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ModelRouterService {

    private final LocalModelService localModel;

    public ModelRouterService(LocalModelService localModel) {
        this.localModel = localModel;
    }

    public ModelRouteDecision route(ModelRouteRequest request) {
        OperationMode mode = request.mode() == null ? OperationMode.BALANCED : request.mode();
        ModelClass modelClass = request.modelClass() == null ? ModelClass.GENERAL : request.modelClass();
        LocalModelHealth local = localModel.health();

        if (local.available()) {
            return new ModelRouteDecision(
                    true,
                    local.provider(),
                    local.configuredModel(),
                    true,
                    true,
                    "Selected available local model for " + modelClass + " in " + mode + " mode.");
        }

        if (mode == OperationMode.PRIVATE || request.protectedData()) {
            return ModelRouteDecision.unavailable(
                    "No local model is currently reachable. Private/protected-data routing will not fall back off-device.");
        }

        if (mode == OperationMode.ZERO_COST || !request.allowPaid()) {
            return ModelRouteDecision.unavailable(
                    "No zero-cost model provider is currently reachable. Aetheris will not fabricate provider availability or use a paid provider.");
        }

        return ModelRouteDecision.unavailable(
                "No configured cloud model provider is available yet. Add an approved provider adapter before paid/cloud routing is enabled.");
    }

    public List<ModelProviderSnapshot> providers() {
        LocalModelHealth local = localModel.health();
        return List.of(new ModelProviderSnapshot(
                local.provider(),
                local.available(),
                true,
                true,
                local.configuredModel(),
                local.detail()));
    }
}
