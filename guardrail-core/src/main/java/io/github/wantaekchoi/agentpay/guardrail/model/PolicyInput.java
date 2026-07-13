package io.github.wantaekchoi.agentpay.guardrail.model;

import io.github.wantaekchoi.agentpay.guardrail.GuardrailConfig;
import java.util.List;

/**
 * {@link io.github.wantaekchoi.agentpay.guardrail.port.PolicyDecision} 판정 입력 —
 * 입력가드 신호(injection/systemPrompt/referenceInstruction)와 제안된 액션·도구·도메인,
 * 그리고 판정 근거가 되는 동적 정책 설정을 함께 담는다.
 */
public record PolicyInput(
        String action,
        List<String> requestedDomains,
        List<String> proposedTools,
        boolean injectionDetected,
        boolean systemPromptRequested,
        boolean referenceInstructionAttempt,
        GuardrailConfig config) {
}
