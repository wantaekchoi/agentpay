package io.github.wantaekchoi.agentpay.guardrail;

import java.util.List;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * {@code agentpay.guardrail.*}로 바인딩되는 동적 정책 설정. per-agent/tenant 값이 필요해지면
 * DB 기반 설정 API로 교체 가능하다(현재는 정적 바인딩). 기본값은 코드에 하드코딩하지 않고
 * {@code application.yml}에 명시한다.
 */
@ConfigurationProperties(prefix = "agentpay.guardrail")
public record GuardrailProperties(
        Set<String> allowedTools,
        List<String> blockedToolPatterns,
        List<String> allowedDomainPatterns,
        Set<String> highRiskActions,
        Set<String> approvalRequiredActions,
        boolean approvalGateEnabled,
        boolean domainCheckEnabled) {

    public GuardrailConfig toConfig() {
        return new GuardrailConfig(
                allowedTools,
                blockedToolPatterns,
                allowedDomainPatterns,
                highRiskActions,
                approvalRequiredActions,
                approvalGateEnabled,
                domainCheckEnabled);
    }
}
