package io.aetheris.identity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "identity_accounts", uniqueConstraints = @UniqueConstraint(columnNames = "email"))
public class IdentityAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, length = 180)
    private String email;

    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private Role role = Role.API_CONSUMER;

    // Account-bounded tenant identifier. Existing identities remain NULL
    // until reviewed ownership migration; never infer links from numeric IDs.
    @Column(name = "tenant_id", columnDefinition = "uuid")
    private UUID tenantId;

    protected IdentityAccount() {}

    public IdentityAccount(String name, String email, String passwordHash, Role role) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.tenantId = UUID.randomUUID();
    }

    public Long getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPasswordHash() { return passwordHash; }
    public Role getRole() { return role; }
    public UUID getTenantId() { return tenantId; }
}
