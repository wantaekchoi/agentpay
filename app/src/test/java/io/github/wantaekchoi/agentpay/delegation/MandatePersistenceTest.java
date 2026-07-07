package io.github.wantaekchoi.agentpay.delegation;

import io.github.wantaekchoi.agentpay.TestcontainersConfiguration;
import io.github.wantaekchoi.agentpay.delegation.domain.Mandate;
import io.github.wantaekchoi.agentpay.delegation.domain.MandateRepository;
import io.github.wantaekchoi.agentpay.delegation.domain.MandateStatus;
import io.github.wantaekchoi.agentpay.identity.domain.Agent;
import io.github.wantaekchoi.agentpay.identity.domain.AgentRepository;
import io.github.wantaekchoi.agentpay.identity.domain.User;
import io.github.wantaekchoi.agentpay.identity.domain.UserRepository;
import java.math.BigInteger;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MandatePersistenceTest {

    @Autowired UserRepository users;
    @Autowired AgentRepository agents;
    @Autowired MandateRepository mandates;

    @Test
    void persistsMandateWithAllowedPayeesAndFindsByAgentId() {
        User u = new User(UUID.randomUUID(), "alice", "0xpub", "0xuseraddr");
        users.save(u);

        Agent a = new Agent(UUID.randomUUID(), u.getId(),
                "0xagentpub", "0xagentaddr", "did:key:zQ3sTest", "shopper", "ACTIVE");
        agents.save(a);

        BigInteger nonce = BigInteger.valueOf(42);
        Mandate m = new Mandate(UUID.randomUUID(), u.getId(), a.getId(), "USD",
                BigInteger.valueOf(1_000), BigInteger.valueOf(10_000), BigInteger.ZERO,
                false, Set.of("merchant-1"),
                1_000L, 2_000L, nonce,
                "0xusersig", MandateStatus.ACTIVE);
        mandates.save(m);

        List<Mandate> found = mandates.findByAgentId(a.getId());
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().getAllowedPayees()).containsExactly("merchant-1");
        assertThat(found.getFirst().getStatus()).isEqualTo(MandateStatus.ACTIVE);

        assertThat(mandates.existsByUserIdAndNonce(u.getId(), nonce)).isTrue();
    }
}
