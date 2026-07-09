package io.github.wantaekchoi.agentpay.guardrail.model;

/**
 * 에이전트 액션에 첨부된 외부/참조 콘텐츠 한 조각.
 *
 * <p>신뢰 수준은 별도 필드로 표현하지 않는다 — 참조컨텍스트는 항상 untrusted로 취급하는 것이
 * 불변 규칙이며, 이 규칙이 교차컨텍스트 인젝션 스캔({@code referenceInstructionAttempt})의 근거다.
 */
public record ReferenceContext(String source, String content) {
}
