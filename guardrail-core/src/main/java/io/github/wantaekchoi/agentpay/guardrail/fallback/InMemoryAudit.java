package io.github.wantaekchoi.agentpay.guardrail.fallback;

import io.github.wantaekchoi.agentpay.guardrail.model.GuardrailDecision;
import io.github.wantaekchoi.agentpay.guardrail.model.GuardrailRequest;
import io.github.wantaekchoi.agentpay.guardrail.model.SemanticVerdict;
import io.github.wantaekchoi.agentpay.guardrail.port.GuardrailAudit;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 무-인프라 폴백 감사기록. traceId로 색인한 메모리 맵에 판정을 기록하고, 비동기
 * {@code SemanticAnalyzer} 완료 시점에 같은 traceId의 verdict를 별도 맵에 보강한다.
 * {@code updateSemanticVerdict}는 fire-and-forget 콜백 스레드(호출 스레드 또는 백그라운드
 * 스레드)에서 호출될 수 있으므로 {@link ConcurrentHashMap}으로 스레드 안전을 보장한다.
 * 외부 저장소(Postgres 등) 없이 동작한다.
 */
public class InMemoryAudit implements GuardrailAudit {

    private final Map<String, GuardrailDecision> decisionsByTraceId = new ConcurrentHashMap<>();
    private final Map<String, SemanticVerdict> semanticVerdictsByTraceId = new ConcurrentHashMap<>();

    @Override
    public void record(GuardrailDecision decision, GuardrailRequest req) {
        decisionsByTraceId.put(decision.traceId(), decision);
    }

    @Override
    public void updateSemanticVerdict(String traceId, SemanticVerdict verdict) {
        semanticVerdictsByTraceId.put(traceId, verdict);
    }

    public Optional<GuardrailDecision> findDecision(String traceId) {
        return Optional.ofNullable(decisionsByTraceId.get(traceId));
    }

    public Optional<SemanticVerdict> findSemanticVerdict(String traceId) {
        return Optional.ofNullable(semanticVerdictsByTraceId.get(traceId));
    }

    public int recordedCount() {
        return decisionsByTraceId.size();
    }
}
