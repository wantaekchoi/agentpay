package io.github.wantaekchoi.agentpay.identity;

import io.github.wantaekchoi.agentpay.identity.domain.Agent;
import io.github.wantaekchoi.agentpay.identity.domain.AgentRepository;
import io.github.wantaekchoi.agentpay.identity.port.AgentDirectory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AgentDirectoryService implements AgentDirectory {
    private final AgentRepository agents;

    public AgentDirectoryService(AgentRepository agents) { this.agents = agents; }

    @Override
    public AgentCard cardFor(UUID agentId) {
        Agent a = agents.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("agent 미존재: " + agentId));
        return new AgentCard(
                a.getAlias(), a.getDid(), a.getAddress(),
                List.of("payments", "commerce.discovery"),
                "/agents/" + a.getId());
    }
}
