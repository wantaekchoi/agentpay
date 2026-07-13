package io.github.wantaekchoi.agentpay.guardrail.model;

import java.util.List;
import java.util.Map;

/**
 * 가드레일 검사 대상 요청. 에이전트가 제안한 액션(메시지·참조컨텍스트·툴·도메인)을 담는다.
 *
 * <p>컬렉션 필드는 {@code null}이 들어오면 빈 컬렉션으로 정규화되고, 그 외에는 방어적으로
 * 불변 복사된다 — 웹 경계(JSON 바디에서 필드 누락)와 라이브러리 임베드 양쪽 모두 이 record를
 * 직접 생성해도 하류(정책 평가 등)에서 null 순회로 인한 NPE가 발생하지 않는다.
 */
public record GuardrailRequest(
        String subjectId,
        String action,
        String message,
        List<ReferenceContext> referenceContexts,
        List<String> proposedTools,
        List<String> requestedDomains,
        Map<String, String> metadata) {

    public GuardrailRequest {
        referenceContexts = referenceContexts == null ? List.of() : List.copyOf(referenceContexts);
        proposedTools = proposedTools == null ? List.of() : List.copyOf(proposedTools);
        requestedDomains = requestedDomains == null ? List.of() : List.copyOf(requestedDomains);
        metadata = metadata == null ? Map.of() : Map.copyOf(metadata);
    }
}
