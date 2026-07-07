package io.github.wantaekchoi.agentpay.identity.port;

import java.util.UUID;

public interface AgentIdentity {
    boolean verifyChallenge(UUID agentId, String message, String signatureHex);
}
