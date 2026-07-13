package io.github.wantaekchoi.agentpay.guardrail.port;

import io.github.wantaekchoi.agentpay.guardrail.model.PolicyInput;
import io.github.wantaekchoi.agentpay.guardrail.model.PolicyResult;

/**
 * 정책 판정 포트 — 입력가드 신호와 제안된 액션/도구/도메인을 동적 정책 설정과 대조해
 * ALLOWED/DENIED/APPROVAL_REQUIRED를 판정한다. 실구현(OPA 등)과 무-인프라 폴백
 * ({@code JavaRulePolicy})이 대체 가능하다.
 */
public interface PolicyDecision {

    PolicyResult decide(PolicyInput input);
}
