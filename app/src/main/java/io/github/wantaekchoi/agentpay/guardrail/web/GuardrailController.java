package io.github.wantaekchoi.agentpay.guardrail.web;

import io.github.wantaekchoi.agentpay.guardrail.Guardrail;
import io.github.wantaekchoi.agentpay.guardrail.JpaGuardrailAudit;
import io.github.wantaekchoi.agentpay.guardrail.domain.GuardrailInspection;
import io.github.wantaekchoi.agentpay.guardrail.model.GuardrailDecision;
import io.github.wantaekchoi.agentpay.guardrail.model.Status;
import io.github.wantaekchoi.agentpay.shared.error.NotFoundException;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class GuardrailController {

    private final Guardrail guardrail;
    private final JpaGuardrailAudit audit;

    public GuardrailController(Guardrail guardrail, JpaGuardrailAudit audit) {
        this.guardrail = guardrail;
        this.audit = audit;
    }

    public record GuardrailTraceResponse(
            String traceId,
            String subjectId,
            String action,
            Status status,
            List<String> reasons,
            boolean injection,
            boolean piiMasked,
            List<String> providers,
            String sanitizedMessage,
            BigDecimal semanticRisk,
            String semanticLabel,
            Instant createdAt) {}

    @PostMapping("/guardrail/inspect")
    public GuardrailDecision inspect(@Valid @RequestBody GuardrailInspectRequest req) {
        return guardrail.inspect(req.toCoreRequest());
    }

    // 저장된 sanitizedMessage만 반환한다 — 원문 message는 애초에 저장되지 않는다.
    @GetMapping("/guardrail/traces/{traceId}")
    public GuardrailTraceResponse getTrace(@PathVariable String traceId) {
        GuardrailInspection inspection = audit.findByTraceId(traceId)
                .orElseThrow(() -> new NotFoundException("guardrail trace 미존재: " + traceId));
        return toResponse(inspection);
    }

    private GuardrailTraceResponse toResponse(GuardrailInspection inspection) {
        return new GuardrailTraceResponse(
                inspection.getTraceId(),
                inspection.getSubjectId(),
                inspection.getAction(),
                inspection.getStatus(),
                Arrays.asList(inspection.getReasons()),
                inspection.isInjection(),
                inspection.isPiiMasked(),
                Arrays.asList(inspection.getProviders()),
                inspection.getSanitizedMessage(),
                inspection.getSemanticRisk(),
                inspection.getSemanticLabel(),
                inspection.getCreatedAt());
    }
}
