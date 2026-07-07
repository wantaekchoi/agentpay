package io.github.wantaekchoi.agentpay.identity;

import java.util.List;

public record AgentCard(
        String name,
        String did,
        String address,
        List<String> capabilities,
        String serviceEndpoint) {}
