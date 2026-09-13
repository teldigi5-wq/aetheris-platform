package io.aetheris.orchestrator.api;

import io.aetheris.orchestrator.policy.ActionRequest;
import io.aetheris.orchestrator.policy.OwnerPolicyService;
import io.aetheris.orchestrator.policy.PolicyDecision;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orchestrator/policy")
public class PolicyController {

    private final OwnerPolicyService ownerPolicyService;

    public PolicyController(OwnerPolicyService ownerPolicyService) {
        this.ownerPolicyService = ownerPolicyService;
    }

    @PostMapping("/evaluate")
    public PolicyDecision evaluate(@RequestBody ActionRequest request) {
        return ownerPolicyService.evaluate(request);
    }
}
