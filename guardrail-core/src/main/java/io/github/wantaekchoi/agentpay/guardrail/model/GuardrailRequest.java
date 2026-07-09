package io.github.wantaekchoi.agentpay.guardrail.model;

import java.util.List;
import java.util.Map;

/**
 * 가드레일 검사 대상 요청. 에이전트가 제안한 액션(메시지·참조컨텍스트·툴·도메인)을 담는다.
 */
public record GuardrailRequest(
        String subjectId,
        String action,
        String message,
        List<ReferenceContext> referenceContexts,
        List<String> proposedTools,
        List<String> requestedDomains,
        Map<String, String> metadata) {
}
