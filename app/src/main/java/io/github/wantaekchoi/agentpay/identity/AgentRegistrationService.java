package io.github.wantaekchoi.agentpay.identity;

import io.github.wantaekchoi.agentpay.identity.domain.Agent;
import io.github.wantaekchoi.agentpay.identity.domain.AgentRepository;
import io.github.wantaekchoi.agentpay.identity.domain.UserRepository;
import io.github.wantaekchoi.agentpay.shared.crypto.Signatures;
import io.github.wantaekchoi.agentpay.shared.did.DidKey;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.UUID;

@Service
public class AgentRegistrationService {
    private final AgentRepository agents;
    private final UserRepository users;

    public AgentRegistrationService(AgentRepository agents, UserRepository users) {
        this.agents = agents;
        this.users = users;
    }

    public Agent register(UUID ownerUserId, String publicKeyHex, String alias) {
        if (!users.existsById(ownerUserId)) {
            throw new IllegalArgumentException("소유자 미존재: " + ownerUserId);
        }
        BigInteger publicKey = Signatures.publicKeyFromHex(publicKeyHex);
        String address = Signatures.addressFromPublicKey(publicKey);
        String did = DidKey.encode(publicKey);
        String storedPublicKeyHex = "0x" + publicKeyHex.replaceFirst("^0x", "");
        Agent agent = new Agent(UUID.randomUUID(), ownerUserId,
                storedPublicKeyHex,
                address, did, alias, "ACTIVE");
        return agents.save(agent);
    }
}
