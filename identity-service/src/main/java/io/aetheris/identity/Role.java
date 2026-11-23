package io.aetheris.identity;

import java.util.Set;

public enum Role {
    ADMIN(Set.of("users:read", "users:write", "services:read", "identity:read", "identity:write", "orchestrator:read", "orchestrator:write")),
    // User and Audit APIs are ADMIN-only (gateway and each service enforce it), so
    // non-admin roles are not issued scopes they can never use (least privilege).
    DEVELOPER(Set.of("identity:read", "orchestrator:read", "orchestrator:write")),
    API_CONSUMER(Set.of("orchestrator:read"));

    private final Set<String> scopes;

    Role(Set<String> scopes) {
        this.scopes = Set.copyOf(scopes);
    }

    public Set<String> scopes() {
        return scopes;
    }
}
