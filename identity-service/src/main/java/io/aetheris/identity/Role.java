package io.aetheris.identity;

import java.util.Set;

public enum Role {
    ADMIN(Set.of("users:read", "users:write", "services:read", "identity:read", "identity:write")),
    DEVELOPER(Set.of("users:read", "users:write", "services:read", "identity:read")),
    API_CONSUMER(Set.of("users:read", "services:read"));

    private final Set<String> scopes;

    Role(Set<String> scopes) {
        this.scopes = Set.copyOf(scopes);
    }

    public Set<String> scopes() {
        return scopes;
    }
}
