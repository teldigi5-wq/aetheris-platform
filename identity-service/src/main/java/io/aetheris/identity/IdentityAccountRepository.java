package io.aetheris.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface IdentityAccountRepository extends JpaRepository<IdentityAccount, Long> {
    Optional<IdentityAccount> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}
