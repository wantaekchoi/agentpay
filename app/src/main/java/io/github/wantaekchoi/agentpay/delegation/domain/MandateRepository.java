package io.github.wantaekchoi.agentpay.delegation.domain;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MandateRepository extends JpaRepository<Mandate, UUID> {
    List<Mandate> findByAgentId(UUID agentId);

    List<Mandate> findByUserId(UUID userId);

    boolean existsByUserIdAndNonce(UUID userId, BigInteger nonce);
}
