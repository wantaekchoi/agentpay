package io.github.wantaekchoi.agentpay.guardrail.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GuardrailInspectionRepository extends JpaRepository<GuardrailInspection, String> {
}
