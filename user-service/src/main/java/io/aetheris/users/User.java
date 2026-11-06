package io.aetheris.users;

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    private String name;

    @Email
    @NotBlank
    @Column(unique = true, nullable = false)
    private String email;

    // NULL is intentionally reserved for legacy ADMIN-only global records.
    @Column(name = "tenant_id", columnDefinition = "uuid")
    private UUID tenantId;

    public User() {}

    public User(String name, String email) {
        this.name = name;
        this.email = email;
    }

    public User(String name, String email, UUID tenantId) {
        this(name, email);
        this.tenantId = java.util.Objects.requireNonNull(tenantId, "Verified tenant required");
    }

    public UUID getTenantId() { return tenantId; }
    public Long getId() { return id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
}
