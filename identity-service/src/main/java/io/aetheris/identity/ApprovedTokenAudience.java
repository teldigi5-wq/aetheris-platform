package io.aetheris.identity;

import java.util.Set;

/**
 * Fixed protocol targets for a future OWNER-APPROVED internal exchange.
 * Neither a caller-provided URL nor a request header can name a new target.
 * Catalog entries grant NO authority by themselves.
 */
enum ApprovedTokenAudience {
    USER("aetheris-user-api", Set.of("users:read", "users:write")),
    AUDIT("aetheris-audit-api", Set.of("services:read")),
    ORCHESTRATOR("aetheris-orchestrator-api", Set.of("orchestrator:read", "orchestrator:write"));

    private final String audience;
    private final Set<String> scopes;

    ApprovedTokenAudience(String audience, Set<String> scopes) {
        this.audience = audience;
        this.scopes = Set.copyOf(scopes);
    }

    String audience() { return audience; }

    /**
     * Minimal subset of account's already-existing privileges.
     * API_CONSUMER deliberately has NO target-token issuance authority
     * until Identity-to-object/tenant links are operationally certified.
     */
    Set<String> allowedScopes(Role role) {
        if (role != Role.ADMIN && role != Role.DEVELOPER) {
            return Set.of();
        }
        Set<String> result = new java.util.HashSet<>(role.scopes());
        result.retainAll(scopes);
        return Set.copyOf(result);
    }
}
