package io.github.wantaekchoi.agentpay.guardrail.port;

import io.github.wantaekchoi.agentpay.guardrail.model.GuardrailRequest;
import io.github.wantaekchoi.agentpay.guardrail.model.InputResult;

/**
 * 입력가드 포트 — 프롬프트 인젝션·PII·교차컨텍스트 신호를 검사하고 메시지를 정제한다.
 * 실구현(Presidio 등)과 무-인프라 폴백({@code RegexInputGuardrail})이 대체 가능하다.
 */
public interface InputGuardrail {

    InputResult inspect(GuardrailRequest req);
}
