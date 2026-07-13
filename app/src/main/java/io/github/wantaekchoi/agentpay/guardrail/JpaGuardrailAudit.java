package io.github.wantaekchoi.agentpay.guardrail;

import io.github.wantaekchoi.agentpay.guardrail.domain.GuardrailInspection;
import io.github.wantaekchoi.agentpay.guardrail.domain.GuardrailInspectionRepository;
import io.github.wantaekchoi.agentpay.guardrail.model.GuardrailDecision;
import io.github.wantaekchoi.agentpay.guardrail.model.GuardrailRequest;
import io.github.wantaekchoi.agentpay.guardrail.model.SemanticVerdict;
import io.github.wantaekchoi.agentpay.guardrail.port.GuardrailAudit;
import java.util.Optional;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Postgres 기반 {@link GuardrailAudit} 구현.
 *
 * <p>{@code record}는 동기 저장이다 — {@code Guardrail.inspect}의 동기 admission 경로에서
 * 호출되며, {@code SimpleJpaRepository.save}가 자체 트랜잭션으로 즉시 커밋하므로 POST 응답이
 * 반환된 직후 이어지는 {@code GET /guardrail/traces/{traceId}} 조회와 경합하지 않는다.
 *
 * <p>{@code updateSemanticVerdict}는 {@code SemanticAnalyzer} 심층분석이 완료된 뒤 별도
 * 스레드에서 같은 traceId의 기록을 사후 보강한다({@link Async}) — admission 판정과는 무관하다.
 *
 * <p>보안 불변: 원문 메시지({@code req.message()})는 저장하지 않는다 — sanitizedMessage만 저장.
 */
@Component
public class JpaGuardrailAudit implements GuardrailAudit {

    private final GuardrailInspectionRepository repository;

    public JpaGuardrailAudit(GuardrailInspectionRepository repository) {
        this.repository = repository;
    }

    @Override
    public void record(GuardrailDecision decision, GuardrailRequest req) {
        GuardrailInspection entity = new GuardrailInspection(
                decision.traceId(),
                req.subjectId(),
                req.action(),
                decision.status(),
                decision.reasons().toArray(new String[0]),
                decision.reasons().contains("prompt_injection"),
                !decision.guardrailActions().isEmpty(),
                decision.providers().toArray(new String[0]),
                decision.sanitizedMessage());
        repository.save(entity);
    }

    @Override
    @Async
    public void updateSemanticVerdict(String traceId, SemanticVerdict verdict) {
        repository.findById(traceId).ifPresent(entity -> {
            entity.applySemanticVerdict(verdict.risk(), verdict.label());
            repository.save(entity);
        });
    }

    public Optional<GuardrailInspection> findByTraceId(String traceId) {
        return repository.findById(traceId);
    }
}
