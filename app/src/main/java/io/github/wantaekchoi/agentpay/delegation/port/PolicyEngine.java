package io.github.wantaekchoi.agentpay.delegation.port;

import io.github.wantaekchoi.agentpay.delegation.domain.Mandate;

public interface PolicyEngine {
    PolicyDecision evaluate(Mandate mandate, PaymentContext ctx);
}
