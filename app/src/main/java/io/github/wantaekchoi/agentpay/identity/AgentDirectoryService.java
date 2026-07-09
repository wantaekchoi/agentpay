package io.github.wantaekchoi.agentpay.identity;

import io.github.wantaekchoi.agentpay.identity.domain.Agent;
import io.github.wantaekchoi.agentpay.identity.domain.AgentRepository;
import io.github.wantaekchoi.agentpay.identity.port.AgentDirectory;
import io.github.wantaekchoi.agentpay.shared.error.NotFoundException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AgentDirectoryService implements AgentDirectory {
    private final AgentRepository agents;

    @Value("${agentpay.base-url:http://localhost:8080}")
    private String baseUrl;

    public AgentDirectoryService(AgentRepository agents) { this.agents = agents; }

    @Override
    public AgentCard cardFor(UUID agentId) {
        Agent a = agents.findById(agentId)
                .orElseThrow(() -> new NotFoundException("agent 미존재: " + agentId));
        return new AgentCard(
                a.getAlias(), a.getDid(), a.getAddress(),
                List.of("payments", "commerce.discovery"),
                baseUrl + "/agents/" + a.getId());
    }
}
