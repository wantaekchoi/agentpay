package io.github.wantaekchoi.agentpay.identity;

import io.github.wantaekchoi.agentpay.TestcontainersConfiguration;
import io.github.wantaekchoi.agentpay.identity.domain.Agent;
import io.github.wantaekchoi.agentpay.identity.domain.User;
import io.github.wantaekchoi.agentpay.identity.domain.UserRepository;
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
class AgentRegistrationServiceTest {

    @Autowired AgentRegistrationService registration;
    @Autowired UserRepository users;

    @Test
    void throwsNotFoundWhenOwnerMissing() {
        var agentKp = Signatures.generateKeyPair();
        String pubHex = Numeric.toHexStringWithPrefixZeroPadded(agentKp.publicKey(), 128);
        UUID missingOwnerId = UUID.randomUUID();

        assertThatThrownBy(() -> registration.register(missingOwnerId, pubHex, "shopper"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void throwsIllegalArgumentForMalformedPublicKeyHex() {
        var ownerKp = Signatures.generateKeyPair();
        User owner = new User(UUID.randomUUID(), "frank", "0xpub", ownerKp.address());
        users.save(owner);

        assertThatThrownBy(() -> registration.register(owner.getId(), "0x123", "shopper"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void normalizesPublicKeyHexWithoutPrefixAndUppercasePrefix() {
        var ownerKp = Signatures.generateKeyPair();
        User owner = new User(UUID.randomUUID(), "grace", "0xpub", ownerKp.address());
        users.save(owner);

        var agentKp = Signatures.generateKeyPair();
        String rawHex = Numeric.toHexStringWithPrefixZeroPadded(agentKp.publicKey(), 128)
                .replaceFirst("^0x", "");

        Agent agent = registration.register(owner.getId(), "0X" + rawHex, "shopper");

        assertThat(agent.getPublicKey()).isEqualTo("0x" + rawHex);
        assertThat(agent.getAddress()).isEqualTo(agentKp.address());
    }
}
