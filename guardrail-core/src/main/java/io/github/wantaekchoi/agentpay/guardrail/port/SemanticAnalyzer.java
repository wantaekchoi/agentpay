package io.github.wantaekchoi.agentpay.guardrail.port;

import io.github.wantaekchoi.agentpay.guardrail.model.GuardrailRequest;
import io.github.wantaekchoi.agentpay.guardrail.model.SemanticVerdict;
import java.util.concurrent.CompletableFuture;

/**
 * sLLM 기반 심층 의미분석 포트. {@code Guardrail#inspect}의 동기 admission 경로는 이 결과를
 * 기다리지 않는다(fire-and-forget) — {@code analyze}는 즉시 반환되는 {@link CompletableFuture}로
 * 비동기 실행되어야 하며, 완료 시점의 결과는 감사기록을 사후 보강하는 데만 쓰인다.
 * 실구현(Ollama 등)과 무-인프라 폴백이 대체 가능하다.
 */
public interface SemanticAnalyzer {

    CompletableFuture<SemanticVerdict> analyze(GuardrailRequest req);
}
