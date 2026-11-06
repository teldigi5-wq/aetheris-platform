package io.aetheris.users;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long> {
    boolean existsByEmail(String email);
    List<User> findAllByTenantId(UUID tenantId);
    Optional<User> findByIdAndTenantId(Long id, UUID tenantId);
}
