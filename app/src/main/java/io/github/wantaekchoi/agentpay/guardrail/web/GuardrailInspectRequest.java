package io.github.wantaekchoi.agentpay.guardrail.web;

import io.github.wantaekchoi.agentpay.guardrail.model.GuardrailRequest;
import io.github.wantaekchoi.agentpay.guardrail.model.ReferenceContext;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;

/**
 * 가드레일 admission 엔드포인트(POST /guardrail/inspect, /agent-actions)의 웹 경계 요청 DTO.
 *
 * <p>core record {@link GuardrailRequest}는 순수 Java 모델이라 검증 애너테이션을 가질 수 없으므로
 * (guardrail-core는 Spring 무의존), subjectId/action 필수값 검증은 이 웹 경계에서 수행하고
 * core record로 매핑한다. 나머지 컬렉션 필드가 비어있거나 누락돼도 {@link GuardrailRequest}의
 * compact constructor가 null을 빈 컬렉션으로 정규화하므로 안전하다.
 */
public record GuardrailInspectRequest(
        @NotBlank String subjectId,
        @NotBlank String action,
        String message,
        List<ReferenceContext> referenceContexts,
        List<String> proposedTools,
        List<String> requestedDomains,
        Map<String, String> metadata) {

    public GuardrailRequest toCoreRequest() {
        return new GuardrailRequest(
                subjectId, action, message, referenceContexts, proposedTools, requestedDomains, metadata);
    }
}
