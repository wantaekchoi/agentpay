package io.github.wantaekchoi.agentpay.delegation;

import io.github.wantaekchoi.agentpay.delegation.domain.Mandate;
import io.github.wantaekchoi.agentpay.delegation.domain.MandateStatus;
import io.github.wantaekchoi.agentpay.delegation.port.PaymentContext;
import io.github.wantaekchoi.agentpay.delegation.port.PolicyDecision;
import io.github.wantaekchoi.agentpay.delegation.port.PolicyEngine;
import org.springframework.stereotype.Service;

@Service
public class DefaultPolicyEngine implements PolicyEngine {

    @Override
    public PolicyDecision evaluate(Mandate mandate, PaymentContext ctx) {
        if (mandate.getStatus() != MandateStatus.ACTIVE) {
            return new PolicyDecision(false, "mandate not ACTIVE: " + mandate.getStatus());
        }
        long now = ctx.now().getEpochSecond();
        if (now < mandate.getValidFrom() || now > mandate.getValidUntil()) {
            return new PolicyDecision(false, "mandate expired/not-yet-valid");
        }
        if (!mandate.getCurrency().equals(ctx.currency())) {
            return new PolicyDecision(false, "currency mismatch");
        }
        if (!mandate.isAllowAnyPayee() && !mandate.getAllowedPayees().contains(ctx.payee())) {
            return new PolicyDecision(false, "payee not allowed");
        }
        if (ctx.amount().compareTo(mandate.getPerTxLimit()) > 0) {
            return new PolicyDecision(false, "amount exceeds perTxLimit");
        }
        if (mandate.getSpent().add(ctx.amount()).compareTo(mandate.getTotalLimit()) > 0) {
            return new PolicyDecision(false, "exceeds totalLimit");
        }
        return new PolicyDecision(true, "OK");
    }
}
