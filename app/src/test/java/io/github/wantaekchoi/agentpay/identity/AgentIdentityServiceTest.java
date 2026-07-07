package io.github.wantaekchoi.agentpay.identity;

import io.github.wantaekchoi.agentpay.TestcontainersConfiguration;
import io.github.wantaekchoi.agentpay.identity.domain.Agent;
import io.github.wantaekchoi.agentpay.identity.domain.User;
import io.github.wantaekchoi.agentpay.identity.domain.UserRepository;
import io.github.wantaekchoi.agentpay.identity.port.AgentIdentity;
import io.github.wantaekchoi.agentpay.shared.crypto.Signatures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import java.util.UUID;
import org.web3j.utils.Numeric;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AgentIdentityServiceTest {

    @Autowired AgentRegistrationService registration;
    @Autowired AgentIdentity agentIdentity;
    @Autowired UserRepository users;

    @Test
    void registersAgentAndVerifiesItsSignature() {
        var ownerKp = Signatures.generateKeyPair();
        User owner = new User(UUID.randomUUID(), "alice", "0xpub", ownerKp.address());
        users.save(owner);

        var agentKp = Signatures.generateKeyPair();
        String pubHex = Numeric.toHexStringWithPrefixZeroPadded(agentKp.publicKey(), 128);
        Agent agent = registration.register(owner.getId(), pubHex, "shopper");

        assertThat(agent.getDid()).startsWith("did:key:zQ3s");
        assertThat(agent.getAddress()).isEqualTo(agentKp.address());

        String challenge = "login-nonce-42";
        String sig = Signatures.sign(agentKp.privateKey(), challenge);
        assertThat(agentIdentity.verifyChallenge(agent.getId(), challenge, sig)).isTrue();
    }

    @Test
    void rejectsSignatureFromWrongKey() {
        var ownerKp = Signatures.generateKeyPair();
        User owner = new User(UUID.randomUUID(), "bob", "0xpub", ownerKp.address());
        users.save(owner);

        var agentKp = Signatures.generateKeyPair();
        String pubHex = Numeric.toHexStringWithPrefixZeroPadded(agentKp.publicKey(), 128);
        Agent agent = registration.register(owner.getId(), pubHex, "shopper");

        var attackerKp = Signatures.generateKeyPair();
        String sig = Signatures.sign(attackerKp.privateKey(), "login-nonce-42");
        assertThat(agentIdentity.verifyChallenge(agent.getId(), "login-nonce-42", sig)).isFalse();
    }
}
