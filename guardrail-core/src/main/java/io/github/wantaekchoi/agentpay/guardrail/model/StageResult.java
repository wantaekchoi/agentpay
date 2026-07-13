package io.github.wantaekchoi.agentpay.guardrail.model;

/**
 * Guardrail 오케스트레이션 파이프라인 한 단계(입력가드/정책 등)의 실행 결과 요약.
 */
public record StageResult(String name, String status, String detail) {
}
