package io.github.wantaekchoi.agentpay.guardrail.fallback;

import io.github.wantaekchoi.agentpay.guardrail.GuardrailConfig;
import io.github.wantaekchoi.agentpay.guardrail.model.PolicyInput;
import io.github.wantaekchoi.agentpay.guardrail.model.PolicyResult;
import io.github.wantaekchoi.agentpay.guardrail.model.Status;
import io.github.wantaekchoi.agentpay.guardrail.port.PolicyDecision;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 무-인프라 폴백 정책 결정. 리서치 {@code guardrail.rego}의 deny 규칙 집합을 그대로 옮긴다 —
 * 각 규칙을 독립적으로 평가해 해당하는 모든 deny 사유를 모으고, 하나라도 있으면 DENIED로
 * 판정한다(OPA의 다중 {@code deny} 규칙 합산과 동일한 방식). deny 사유가 없을 때만 승인게이트
 * (고위험/승인필요 액션)를 확인한다. 외부 정책엔진(OPA 등) 없이 동작한다.
 */
public class JavaRulePolicy implements PolicyDecision {

    @Override
    public PolicyResult decide(PolicyInput input) {
        GuardrailConfig config = input.config();
        List<String> reasons = new ArrayList<>();

        if (input.injectionDetected()) {
            reasons.add("prompt_injection");
        }
        if (input.systemPromptRequested()) {
            reasons.add("system_prompt_request");
        }
        if (input.referenceInstructionAttempt()) {
            reasons.add("cross_context_instruction_denied");
        }
        if (config.domainCheckEnabled() && input.requestedDomains().isEmpty()) {
            reasons.add("no_source_declared");
        }
        if (config.domainCheckEnabled()
                && hasUnapprovedDomain(input.requestedDomains(), config.allowedDomainPatterns())) {
            reasons.add("unapproved_domain_requested");
        }
        if (hasBlockedTool(input.proposedTools(), config.blockedToolPatterns())) {
            reasons.add("blocked_tool_requested");
        }
        if (hasUnapprovedTool(input.proposedTools(), config.allowedTools())) {
            reasons.add("unapproved_tool_requested");
        }

        if (!reasons.isEmpty()) {
            return new PolicyResult(Status.DENIED, reasons);
        }

        if (config.approvalGateEnabled() && requiresApproval(input.action(), config)) {
            return new PolicyResult(Status.APPROVAL_REQUIRED, List.of());
        }

        return new PolicyResult(Status.ALLOWED, List.of());
    }

    private static boolean requiresApproval(String action, GuardrailConfig config) {
        return config.highRiskActions().contains(action) || config.approvalRequiredActions().contains(action);
    }

    private static boolean hasUnapprovedDomain(List<String> requestedDomains, List<String> allowedDomainPatterns) {
        for (String domain : requestedDomains) {
            if (!matchesAnyDomainPattern(domain, allowedDomainPatterns)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAnyDomainPattern(String domain, List<String> allowedDomainPatterns) {
        for (String pattern : allowedDomainPatterns) {
            if (matchesDomainPattern(domain, pattern)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesDomainPattern(String domain, String pattern) {
        if (pattern.startsWith("*.")) {
            String rootDomain = pattern.substring(2);
            return domain.equals(rootDomain) || domain.endsWith("." + rootDomain);
        }
        return domain.equals(pattern);
    }

    private static boolean hasBlockedTool(List<String> proposedTools, List<String> blockedToolPatterns) {
        for (String tool : proposedTools) {
            for (String pattern : blockedToolPatterns) {
                if (tool.contains(pattern)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasUnapprovedTool(List<String> proposedTools, Set<String> allowedTools) {
        for (String tool : proposedTools) {
            if (!allowedTools.contains(tool)) {
                return true;
            }
        }
        return false;
    }
}
