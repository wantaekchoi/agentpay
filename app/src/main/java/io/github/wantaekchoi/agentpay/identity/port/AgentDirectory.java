package io.github.wantaekchoi.agentpay.identity.port;

import io.github.wantaekchoi.agentpay.identity.AgentCard;
import java.util.UUID;

public interface AgentDirectory {
    AgentCard cardFor(UUID agentId);
}
