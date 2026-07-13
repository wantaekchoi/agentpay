package io.github.wantaekchoi.agentpay.guardrail;

import io.github.wantaekchoi.agentpay.guardrail.model.GuardrailDecision;
import io.github.wantaekchoi.agentpay.guardrail.model.GuardrailRequest;
import io.github.wantaekchoi.agentpay.guardrail.model.InputResult;
import io.github.wantaekchoi.agentpay.guardrail.model.PolicyInput;
import io.github.wantaekchoi.agentpay.guardrail.model.PolicyResult;
import io.github.wantaekchoi.agentpay.guardrail.model.StageResult;
import io.github.wantaekchoi.agentpay.guardrail.port.GuardrailAudit;
import io.github.wantaekchoi.agentpay.guardrail.port.InputGuardrail;
import io.github.wantaekchoi.agentpay.guardrail.port.PolicyDecision;
import io.github.wantaekchoi.agentpay.guardrail.port.SemanticAnalyzer;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * 가드레일 오케스트레이터 — 입력가드→정책의 동기 경로로 admission을 즉시 확정한다.
 *
 * <p>{@code inspect}는 sLLM을 호출하지 않는다(무지연). {@code SemanticAnalyzer} 심층분석은
 * fire-and-forget으로 트리거만 하고 반환값을 기다리지 않으며, 그 결과는 admission 판정에
 * 전혀 영향을 주지 않는다 — {@link GuardrailDecision#deepAnalysisPending()}은 그 분석이
 * 이 판정과 별개로 뒤이어 진행 중임을 나타낼 뿐이다. 완료되면 {@code audit.updateSemanticVerdict}로
 * 같은 traceId의 감사기록을 사후 보강한다.
 *
 * <p>analyzer 호출 전체({@code analyze} 자체의 동기 throw는 물론, 이미 완료된 future에 대해
 * 콜백이 호출 스레드에서 즉시 실행되는 경우의 {@code whenComplete} 동기 throw까지)를 감싸
 * 예외를 삼킨다 — 심층분석·감사보강 어느 쪽이 실패해도 admission 판정에는 영향이 없다.
 */
public class Guardrail {

    private final InputGuardrail inputGuardrail;
    private final PolicyDecision policyDecision;
    private final SemanticAnalyzer semanticAnalyzer;
    private final GuardrailAudit guardrailAudit;
    private final GuardrailConfig config;

    public Guardrail(
            InputGuardrail inputGuardrail,
            PolicyDecision policyDecision,
            SemanticAnalyzer semanticAnalyzer,
            GuardrailAudit guardrailAudit,
            GuardrailConfig config) {
        this.inputGuardrail = inputGuardrail;
        this.policyDecision = policyDecision;
        this.semanticAnalyzer = semanticAnalyzer;
        this.guardrailAudit = guardrailAudit;
        this.config = config;
    }

    public GuardrailDecision inspect(GuardrailRequest req) {
        InputResult inputResult = inputGuardrail.inspect(req);

        PolicyInput policyInput = new PolicyInput(
                req.action(),
                req.requestedDomains(),
                req.proposedTools(),
                inputResult.injectionDetected(),
                inputResult.systemPromptRequested(),
                inputResult.referenceInstructionAttempt(),
                config);
        PolicyResult policyResult = policyDecision.decide(policyInput);

        List<StageResult> stages = List.of(
                new StageResult("input", inputStageStatus(inputResult), inputStageDetail(inputResult)),
                new StageResult(
                        "policy", policyResult.status().name(), String.join(",", policyResult.reasons())));

        GuardrailDecision decision = new GuardrailDecision(
                UUID.randomUUID().toString(),
                policyResult.status(),
                policyResult.reasons(),
                inputResult.sanitizedMessage(),
                inputResult.guardrailActions(),
                stages,
                inputResult.providers(),
                true);

        guardrailAudit.record(decision, req);

        triggerDeepAnalysis(decision, req);

        return decision;
    }

    /**
     * fire-and-forget: 반환된 {@link CompletableFuture}를 기다리거나 admission에 반영하지 않는다.
     * 완료 시 {@code audit.updateSemanticVerdict}로 같은 traceId의 감사기록을 보강한다. analyzer가
     * 동기적으로 throw하거나(예: {@code analyze} 호출 자체, 또는 이미 완료된 future에 대해 호출
     * 스레드에서 즉시 실행되는 {@code whenComplete} 콜백 내부의 throw) 예외를 던져도 admission
     * 판정은 이미 확정되어 반환된 뒤이므로 영향이 없어야 한다 — 그 보장을 위해 여기서 삼킨다.
     */
    private void triggerDeepAnalysis(GuardrailDecision decision, GuardrailRequest req) {
        try {
            semanticAnalyzer.analyze(req).whenComplete((verdict, error) -> {
                if (verdict != null) {
                    guardrailAudit.updateSemanticVerdict(decision.traceId(), verdict);
                }
                // error != null (analyzer가 비동기로 실패)인 경우도 삼긴다 — admission에 이미 영향 없음.
            });
        } catch (RuntimeException ex) {
            // 동기 throw 격리: analyzer 또는 whenComplete 콜백(감사 보강 포함)이 무엇을 던지든
            // 심층분석은 admission과 완전히 분리된 사후 보강 경로이므로 여기서 삼킨다.
        }
    }

    private static String inputStageStatus(InputResult inputResult) {
        boolean flagged = inputResult.injectionDetected()
                || inputResult.systemPromptRequested()
                || inputResult.referenceInstructionAttempt();
        return flagged ? "flagged" : "clean";
    }

    private static String inputStageDetail(InputResult inputResult) {
        return "piiFindings=" + inputResult.piiFindings().size();
    }
}
