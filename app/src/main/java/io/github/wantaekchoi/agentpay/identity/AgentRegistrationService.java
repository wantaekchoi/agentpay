package io.github.wantaekchoi.agentpay.identity;

import io.github.wantaekchoi.agentpay.identity.domain.Agent;
import io.github.wantaekchoi.agentpay.identity.domain.AgentRepository;
import io.github.wantaekchoi.agentpay.identity.domain.UserRepository;
import io.github.wantaekchoi.agentpay.shared.did.DidKey;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

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
        BigInteger publicKey = Numeric.toBigInt(publicKeyHex);
        String address = Numeric.prependHexPrefix(Keys.getAddress(publicKey)).toLowerCase();
        String did = DidKey.encode(publicKey);
        Agent agent = new Agent(UUID.randomUUID(), ownerUserId,
                Numeric.prependHexPrefix(publicKeyHex.replaceFirst("^0x", "")),
                address, did, alias, "ACTIVE");
        return agents.save(agent);
    }
}
