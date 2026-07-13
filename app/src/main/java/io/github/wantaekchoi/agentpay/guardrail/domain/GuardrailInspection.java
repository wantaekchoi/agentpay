package io.github.wantaekchoi.agentpay.guardrail.domain;

import io.github.wantaekchoi.agentpay.guardrail.model.Status;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * {@code guardrail_inspection} 테이블 매핑. 보안 불변: 원문 {@code message}는 절대 저장하지
 * 않는다 — {@code sanitizedMessage}만 영속화한다.
 */
@Entity
@Table(name = "guardrail_inspection")
public class GuardrailInspection {

    @Id
    @Column(name = "trace_id")
    private String traceId;

    @Column(name = "subject_id")
    private String subjectId;

    private String action;

    @Enumerated(EnumType.STRING)
    private Status status;

    private String[] reasons;

    private boolean injection;

    @Column(name = "pii_masked")
    private boolean piiMasked;

    private String[] providers;

    @Column(name = "sanitized_message")
    private String sanitizedMessage;

    @Column(name = "semantic_risk")
    private BigDecimal semanticRisk;

    @Column(name = "semantic_label")
    private String semanticLabel;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    protected GuardrailInspection() {}

    public GuardrailInspection(
            String traceId,
            String subjectId,
            String action,
            Status status,
            String[] reasons,
            boolean injection,
            boolean piiMasked,
            String[] providers,
            String sanitizedMessage) {
        this.traceId = traceId;
        this.subjectId = subjectId;
        this.action = action;
        this.status = status;
        this.reasons = reasons;
        this.injection = injection;
        this.piiMasked = piiMasked;
        this.providers = providers;
        this.sanitizedMessage = sanitizedMessage;
    }

    public void applySemanticVerdict(double risk, String label) {
        this.semanticRisk = BigDecimal.valueOf(risk);
        this.semanticLabel = label;
    }

    public String getTraceId() { return traceId; }
    public String getSubjectId() { return subjectId; }
    public String getAction() { return action; }
    public Status getStatus() { return status; }
    public String[] getReasons() { return reasons; }
    public boolean isInjection() { return injection; }
    public boolean isPiiMasked() { return piiMasked; }
    public String[] getProviders() { return providers; }
    public String getSanitizedMessage() { return sanitizedMessage; }
    public BigDecimal getSemanticRisk() { return semanticRisk; }
    public String getSemanticLabel() { return semanticLabel; }
    public Instant getCreatedAt() { return createdAt; }
}
