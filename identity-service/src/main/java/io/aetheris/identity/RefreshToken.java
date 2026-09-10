package io.aetheris.identity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "identity_refresh_tokens", indexes = @Index(name = "idx_refresh_token_hash", columnList = "token_hash", unique = true))
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private IdentityAccount account;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected RefreshToken() {}

    public RefreshToken(IdentityAccount account, String tokenHash, Instant expiresAt) {
        this.account = account;
        this.tokenHash = tokenHash;
        this.expiresAt = expiresAt;
    }

    public IdentityAccount getAccount() { return account; }
    public String getTokenHash() { return tokenHash; }
    public Instant getExpiresAt() { return expiresAt; }
    public boolean isRevoked() { return revoked; }
    public void revoke() { this.revoked = true; }
    public boolean isExpired() { return !expiresAt.isAfter(Instant.now()); }
}
