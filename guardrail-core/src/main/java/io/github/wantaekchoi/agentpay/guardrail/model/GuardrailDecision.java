package io.github.wantaekchoi.agentpay.guardrail.model;

import java.util.List;

/**
 * {@code Guardrail#inspect}의 최종 admission 판정. 동기 경로(입력가드+정책)만으로 확정되며,
 * {@code deepAnalysisPending=true}는 sLLM 기반 {@code SemanticAnalyzer} 심층분석이 이 판정과
 * 무관하게 비동기로 뒤이어 실행되어 감사기록을 사후 보강함을 의미한다(admission을 막지 않음).
 */
public record GuardrailDecision(
        String traceId,
        Status status,
        List<String> reasons,
        String sanitizedMessage,
        List<String> guardrailActions,
        List<StageResult> stages,
        List<String> providers,
        boolean deepAnalysisPending) {
}
