package io.github.wantaekchoi.agentpay.identity;

import io.github.wantaekchoi.agentpay.TestcontainersConfiguration;
import io.github.wantaekchoi.agentpay.identity.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class IdentityPersistenceTest {

    @Autowired UserRepository users;
    @Autowired AgentRepository agents;

    @Test
    void persistsUserAndAgent() {
        User u = new User(UUID.randomUUID(), "alice", "0xpub", "0xuseraddr");
        users.save(u);

        Agent a = new Agent(UUID.randomUUID(), u.getId(),
                "0xagentpub", "0xagentaddr", "did:key:zQ3sTest", "shopper", "ACTIVE");
        agents.save(a);

        assertThat(agents.findById(a.getId())).isPresent()
                .get().extracting(Agent::getOwnerUserId).isEqualTo(u.getId());
    }
}
