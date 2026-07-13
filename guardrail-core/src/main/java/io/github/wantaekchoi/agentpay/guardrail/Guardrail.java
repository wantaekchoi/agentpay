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

/**
 * 가드레일 오케스트레이터 — 입력가드→정책의 동기 경로로 admission을 즉시 확정한다.
 *
 * <p>{@code inspect}는 sLLM을 호출하지 않는다(무지연). {@code SemanticAnalyzer} 심층분석은
 * fire-and-forget으로 트리거만 하고 반환값을 기다리지 않으며, 그 결과는 admission 판정에
 * 전혀 영향을 주지 않는다 — {@link GuardrailDecision#deepAnalysisPending()}은 그 분석이
 * 이 판정과 별개로 뒤이어 진행 중임을 나타낼 뿐이다.
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

        // fire-and-forget: 반환된 CompletableFuture를 기다리거나 admission에 반영하지 않는다.
        // 완료 시 감사기록을 보강하는 배선(whenComplete → audit.updateSemanticVerdict)은
        // 비동기 확정을 다루는 후속 태스크(T3)에서 마무리한다.
        semanticAnalyzer.analyze(req);

        return decision;
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
