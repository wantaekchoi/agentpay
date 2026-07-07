package io.github.wantaekchoi.agentpay.identity;

import io.github.wantaekchoi.agentpay.TestcontainersConfiguration;
import io.github.wantaekchoi.agentpay.identity.domain.Agent;
import io.github.wantaekchoi.agentpay.identity.domain.User;
import io.github.wantaekchoi.agentpay.identity.domain.UserRepository;
import io.github.wantaekchoi.agentpay.identity.port.AgentDirectory;
import io.github.wantaekchoi.agentpay.shared.crypto.Signatures;
import io.github.wantaekchoi.agentpay.shared.error.NotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;
import org.web3j.utils.Numeric;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AgentDirectoryServiceTest {

    @Autowired AgentRegistrationService registration;
    @Autowired AgentDirectory directory;
    @Autowired UserRepository users;

    @Test
    void buildsAgentCardFromRegisteredAgent() {
        var ownerKp = Signatures.generateKeyPair();
        User owner = new User(UUID.randomUUID(), "alice", "0xpub", ownerKp.address());
        users.save(owner);
        var agentKp = Signatures.generateKeyPair();
        String pubHex = Numeric.toHexStringWithPrefixZeroPadded(agentKp.publicKey(), 128);
        Agent agent = registration.register(owner.getId(), pubHex, "shopper");

        AgentCard card = directory.cardFor(agent.getId());

        assertThat(card.name()).isEqualTo("shopper");
        assertThat(card.did()).isEqualTo(agent.getDid());
        assertThat(card.address()).isEqualTo(agent.getAddress());
        assertThat(card.capabilities()).contains("payments");
    }

    @Test
    void throwsNotFoundForUnknownAgent() {
        UUID missingAgentId = UUID.randomUUID();

        assertThatThrownBy(() -> directory.cardFor(missingAgentId))
                .isInstanceOf(NotFoundException.class);
    }
}
