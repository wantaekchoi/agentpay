package io.github.wantaekchoi.agentpay.guardrail;

import io.github.wantaekchoi.agentpay.guardrail.fallback.HeuristicAnalyzer;
import io.github.wantaekchoi.agentpay.guardrail.fallback.JavaRulePolicy;
import io.github.wantaekchoi.agentpay.guardrail.fallback.RegexInputGuardrail;
import io.github.wantaekchoi.agentpay.guardrail.port.GuardrailAudit;
import io.github.wantaekchoi.agentpay.guardrail.port.InputGuardrail;
import io.github.wantaekchoi.agentpay.guardrail.port.PolicyDecision;
import io.github.wantaekchoi.agentpay.guardrail.port.SemanticAnalyzer;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * 가드레일 포트 4종의 기본 배선. OPA/Presidio/Ollama 등 외부 도구 어댑터는 아직 없으므로
 * (T5~T7 소관) 무-인프라 폴백(RegexInputGuardrail·JavaRulePolicy·HeuristicAnalyzer)을 기본
 * 빈으로 등록하고, 감사 포트만 실 인프라(Postgres, {@link JpaGuardrailAudit})를 사용한다.
 *
 * <p>{@link EnableAsync}는 {@link JpaGuardrailAudit#updateSemanticVerdict}의 {@code @Async}
 * 실행을 활성화한다(전용 실행기 빈을 두지 않으므로 Spring 기본 {@code SimpleAsyncTaskExecutor}
 * 사용 — admission 판정과 무관한 사후 감사 보강이라 무겁지 않다).
 */
@Configuration
@EnableAsync
@EnableConfigurationProperties(GuardrailProperties.class)
public class GuardrailBeans {

    @Bean
    public InputGuardrail inputGuardrail() {
        return new RegexInputGuardrail();
    }

    @Bean
    public PolicyDecision policyDecision() {
        return new JavaRulePolicy();
    }

    @Bean
    public SemanticAnalyzer semanticAnalyzer() {
        return new HeuristicAnalyzer();
    }

    @Bean
    public GuardrailConfig guardrailConfig(GuardrailProperties properties) {
        return properties.toConfig();
    }

    @Bean
    public Guardrail guardrail(
            InputGuardrail inputGuardrail,
            PolicyDecision policyDecision,
            SemanticAnalyzer semanticAnalyzer,
            GuardrailAudit guardrailAudit,
            GuardrailConfig guardrailConfig) {
        return new Guardrail(inputGuardrail, policyDecision, semanticAnalyzer, guardrailAudit, guardrailConfig);
    }
}
