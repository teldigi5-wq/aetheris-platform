package io.aetheris.identity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(flushAutomatically = true)
    @Query("""
            update RefreshToken token
               set token.revoked = true
             where token.tokenHash = :tokenHash
               and token.revoked = false
               and token.expiresAt > :now
            """)
    int revokeIfActive(@Param("tokenHash") String tokenHash, @Param("now") Instant now);
}
