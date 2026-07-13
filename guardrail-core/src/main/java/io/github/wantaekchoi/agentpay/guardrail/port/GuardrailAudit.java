package io.github.wantaekchoi.agentpay.guardrail.port;

import io.github.wantaekchoi.agentpay.guardrail.model.GuardrailDecision;
import io.github.wantaekchoi.agentpay.guardrail.model.GuardrailRequest;
import io.github.wantaekchoi.agentpay.guardrail.model.SemanticVerdict;

/**
 * 가드레일 감사기록 포트 — 동기 admission 판정을 즉시 기록하고, 이후 비동기
 * {@code SemanticAnalyzer} 심층분석이 완료되면 같은 traceId의 기록에 verdict를 보강한다.
 * 실구현(Postgres 등)과 무-인프라 폴백이 대체 가능하다.
 */
public interface GuardrailAudit {

    void record(GuardrailDecision decision, GuardrailRequest req);

    void updateSemanticVerdict(String traceId, SemanticVerdict verdict);
}
