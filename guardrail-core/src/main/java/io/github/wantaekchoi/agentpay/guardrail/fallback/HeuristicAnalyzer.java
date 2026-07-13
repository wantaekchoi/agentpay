package io.github.wantaekchoi.agentpay.guardrail.fallback;

import io.github.wantaekchoi.agentpay.guardrail.model.GuardrailRequest;
import io.github.wantaekchoi.agentpay.guardrail.model.SemanticVerdict;
import io.github.wantaekchoi.agentpay.guardrail.port.SemanticAnalyzer;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * 무-인프라 폴백 심층분석기. sLLM 없이 인젝션/시스템프롬프트 키워드 밀도로 위험도를 근사한다.
 * 계산 자체는 동기이지만 {@link SemanticAnalyzer} 계약대로 {@link CompletableFuture#completedFuture}로
 * 감싸 반환한다 — 호출자({@code Guardrail})는 실구현(Ollama 등)과 동일하게 비동기 취급한다.
 * 외부 sLLM 없이 동작한다.
 */
public class HeuristicAnalyzer implements SemanticAnalyzer {

    private static final List<Pattern> RISK_KEYWORDS = List.of(
            Pattern.compile("ignore (all )?previous instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard (the )?above", Pattern.CASE_INSENSITIVE),
            Pattern.compile("system prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("reveal your (system )?prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you are now", Pattern.CASE_INSENSITIVE),
            Pattern.compile("jailbreak", Pattern.CASE_INSENSITIVE));

    private static final double HIGH_RISK_THRESHOLD = 0.6;
    private static final double SUSPICIOUS_THRESHOLD = 0.2;
    private static final double BASE_RISK_ON_MATCH = 0.5;
    private static final double DENSITY_WEIGHT = 5.0;

    @Override
    public CompletableFuture<SemanticVerdict> analyze(GuardrailRequest req) {
        String message = nullToEmpty(req.message());
        int matchCount = countMatches(message);
        double risk = riskFor(message, matchCount);
        String label = labelFor(risk);
        String rationale = "keywordMatches=" + matchCount + ",density=" + String.format("%.2f", density(message, matchCount));
        return CompletableFuture.completedFuture(new SemanticVerdict(risk, label, rationale));
    }

    private static double riskFor(String message, int matchCount) {
        if (matchCount == 0) {
            return 0.0;
        }
        double density = density(message, matchCount);
        return Math.min(1.0, BASE_RISK_ON_MATCH + density * DENSITY_WEIGHT);
    }

    private static double density(String message, int matchCount) {
        int wordCount = Math.max(1, message.trim().split("\\s+").length);
        return (double) matchCount / wordCount;
    }

    private static int countMatches(String message) {
        int count = 0;
        for (Pattern pattern : RISK_KEYWORDS) {
            if (pattern.matcher(message).find()) {
                count++;
            }
        }
        return count;
    }

    private static String labelFor(double risk) {
        if (risk >= HIGH_RISK_THRESHOLD) {
            return "malicious";
        }
        if (risk >= SUSPICIOUS_THRESHOLD) {
            return "suspicious";
        }
        return "benign";
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
