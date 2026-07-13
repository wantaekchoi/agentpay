package io.github.wantaekchoi.agentpay.guardrail.model;

/**
 * {@code SemanticAnalyzer}(sLLM 기반 심층분석)가 비동기로 산출하는 위험도 평가.
 * admission 판정에는 관여하지 않고 감사기록을 사후 보강하는 용도다.
 *
 * @param risk 0.0(안전)~1.0(고위험) 사이의 위험 점수
 * @param label 분류 라벨(예: benign/suspicious/malicious)
 * @param rationale 판단 근거 설명
 */
public record SemanticVerdict(double risk, String label, String rationale) {
}
