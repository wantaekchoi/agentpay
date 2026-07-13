package io.github.wantaekchoi.agentpay.guardrail;

import java.util.List;
import java.util.Set;

/**
 * 동적 정책 설정값. per-agent/tenant로 재배포 없이 튜닝 가능한 정책 파라미터를 담는다
 * ({@code JavaRulePolicy} 같은 {@code PolicyDecision} 구현체가 이 값을 근거로 판정한다).
 *
 * <p>{@code allowedDomainPatterns} 항목은 정확히 일치하는 도메인이거나, {@code "*.example.com"}처럼
 * {@code "*."} 접두사로 서브도메인 전체를 허용하는 와일드카드다. {@code blockedToolPatterns}는
 * 제안된 도구 이름에 대한 부분 문자열(substring) 매치다.
 */
public record GuardrailConfig(
        Set<String> allowedTools,
        List<String> blockedToolPatterns,
        List<String> allowedDomainPatterns,
        Set<String> highRiskActions,
        Set<String> approvalRequiredActions,
        boolean approvalGateEnabled,
        boolean domainCheckEnabled) {
}
