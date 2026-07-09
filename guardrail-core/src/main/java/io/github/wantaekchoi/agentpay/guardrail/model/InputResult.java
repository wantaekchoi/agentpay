package io.github.wantaekchoi.agentpay.guardrail.model;

import java.util.List;

/**
 * 입력가드({@link io.github.wantaekchoi.agentpay.guardrail.port.InputGuardrail}) 검사 결과.
 */
public record InputResult(
        String sanitizedMessage,
        List<String> guardrailActions,
        boolean injectionDetected,
        boolean systemPromptRequested,
        boolean referenceInstructionAttempt,
        List<String> piiFindings,
        List<String> providers) {
}
