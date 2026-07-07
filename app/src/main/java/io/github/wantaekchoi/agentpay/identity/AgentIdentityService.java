package io.github.wantaekchoi.agentpay.identity;

import io.github.wantaekchoi.agentpay.identity.domain.Agent;
import io.github.wantaekchoi.agentpay.identity.domain.AgentRepository;
import io.github.wantaekchoi.agentpay.identity.port.AgentIdentity;
import io.github.wantaekchoi.agentpay.shared.crypto.Signatures;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AgentIdentityService implements AgentIdentity {
    private final AgentRepository agents;

    public AgentIdentityService(AgentRepository agents) {
        this.agents = agents;
    }

    @Override
    public boolean verifyChallenge(UUID agentId, String message, String signatureHex) {
        Agent agent = agents.findById(agentId).orElse(null);
        if (agent == null) return false;
        try {
            String recovered = Signatures.recoverAddress(message, signatureHex);
            return recovered.equalsIgnoreCase(agent.getAddress());
        } catch (RuntimeException e) {
            return false;
        }
    }
}
