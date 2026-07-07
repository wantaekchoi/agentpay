package io.github.wantaekchoi.agentpay.delegation.port;

public record PolicyDecision(boolean allowed, String reason) {
}
