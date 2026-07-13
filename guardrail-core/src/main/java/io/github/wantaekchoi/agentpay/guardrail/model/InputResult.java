package io.github.wantaekchoi.agentpay.guardrail.model;

import java.util.List;

/**
 * 입력가드({@link io.github.wantaekchoi.agentpay.guardrail.port.InputGuardrail}) 검사 결과.
 *
 * <p>컬렉션 필드는 방어적으로 불변 복사된다 — 생성자에 가변 {@code ArrayList}를 넘겨도 이후
 * 외부에서 변경할 수 없다({@link io.github.wantaekchoi.agentpay.guardrail.model.GuardrailDecision}이
 * {@code guardrailActions()}를 그대로 노출하므로 이 불변성이 그쪽까지 전파된다).
 */
public record InputResult(
        String sanitizedMessage,
        List<String> guardrailActions,
        boolean injectionDetected,
        boolean systemPromptRequested,
        boolean referenceInstructionAttempt,
        List<String> piiFindings,
        List<String> providers) {

    public InputResult {
        guardrailActions = List.copyOf(guardrailActions);
        piiFindings = List.copyOf(piiFindings);
        providers = List.copyOf(providers);
    }
}
