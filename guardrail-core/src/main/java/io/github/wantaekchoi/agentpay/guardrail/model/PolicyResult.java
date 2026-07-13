package io.github.wantaekchoi.agentpay.guardrail.model;

import java.util.List;

/**
 * {@link io.github.wantaekchoi.agentpay.guardrail.port.PolicyDecision} 판정 결과.
 * {@code reasons}는 {@code status}가 {@link Status#ALLOWED}가 아닐 때 근거 규칙 식별자를 담는다.
 */
public record PolicyResult(Status status, List<String> reasons) {
}
