package io.github.wantaekchoi.agentpay.guardrail.fallback;

import io.github.wantaekchoi.agentpay.guardrail.model.GuardrailRequest;
import io.github.wantaekchoi.agentpay.guardrail.model.InputResult;
import io.github.wantaekchoi.agentpay.guardrail.model.ReferenceContext;
import io.github.wantaekchoi.agentpay.guardrail.port.InputGuardrail;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 무-인프라 폴백 입력가드. 프롬프트 인젝션·시스템프롬프트 요청·PII를 정규식으로 검사·마스킹하고,
 * {@code message}와 각 {@code referenceContexts[].content}를 모두 스캔해 교차컨텍스트 인젝션
 * 시도({@code referenceInstructionAttempt})를 잡아낸다. 외부 서비스(Presidio 등) 없이 동작한다.
 */
public class RegexInputGuardrail implements InputGuardrail {

    private static final String PROVIDER_NAME = "regex";

    private static final List<Pattern> INJECTION_PATTERNS = List.of(
            Pattern.compile("ignore (all )?previous instructions", Pattern.CASE_INSENSITIVE),
            Pattern.compile("disregard (the )?above", Pattern.CASE_INSENSITIVE),
            Pattern.compile("system prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("reveal your (system )?prompt", Pattern.CASE_INSENSITIVE),
            Pattern.compile("you are now", Pattern.CASE_INSENSITIVE),
            Pattern.compile("jailbreak", Pattern.CASE_INSENSITIVE));

    private static final Pattern SYSTEM_PROMPT_REQUEST_PATTERN =
            Pattern.compile("system prompt|시스템 프롬프트", Pattern.CASE_INSENSITIVE);

    private static final Pattern PHONE_PATTERN = Pattern.compile("0\\d{1,2}-?\\d{3,4}-?\\d{4}");
    private static final Pattern SECRET_PATTERN = Pattern.compile("sk-[A-Za-z0-9]{8,}");
    private static final Pattern RRN_PATTERN = Pattern.compile("\\d{6}-?\\d{7}");
    private static final Pattern CARD_PATTERN = Pattern.compile("\\d{4}-?\\d{4}-?\\d{4}-?\\d{4}");
    private static final Pattern EMAIL_PATTERN =
            Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");

    @Override
    public InputResult inspect(GuardrailRequest req) {
        String message = nullToEmpty(req.message());

        boolean injectionDetected = matchesAny(INJECTION_PATTERNS, message);
        boolean systemPromptRequested = SYSTEM_PROMPT_REQUEST_PATTERN.matcher(message).find();
        boolean referenceInstructionAttempt = scanReferencesForInstructionAttempt(req.referenceContexts());

        List<String> guardrailActions = new ArrayList<>();
        List<String> piiFindings = new ArrayList<>();
        String sanitizedMessage = sanitize(message, guardrailActions, piiFindings);

        return new InputResult(
                sanitizedMessage,
                guardrailActions,
                injectionDetected,
                systemPromptRequested,
                referenceInstructionAttempt,
                piiFindings,
                List.of(PROVIDER_NAME));
    }

    private static boolean scanReferencesForInstructionAttempt(List<ReferenceContext> referenceContexts) {
        if (referenceContexts == null) {
            return false;
        }
        for (ReferenceContext reference : referenceContexts) {
            String content = nullToEmpty(reference.content());
            if (matchesAny(INJECTION_PATTERNS, content)
                    || SYSTEM_PROMPT_REQUEST_PATTERN.matcher(content).find()) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesAny(List<Pattern> patterns, String text) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).find()) {
                return true;
            }
        }
        return false;
    }

    private static String sanitize(String message, List<String> guardrailActions, List<String> piiFindings) {
        String sanitized = message;
        sanitized = maskIfPresent(sanitized, PHONE_PATTERN, "[PHONE]", "phone", guardrailActions, piiFindings);
        sanitized = maskIfPresent(sanitized, SECRET_PATTERN, "[SECRET]", "secret", guardrailActions, piiFindings);
        sanitized = maskIfPresent(sanitized, RRN_PATTERN, "[RRN]", "rrn", guardrailActions, piiFindings);
        sanitized = maskIfPresent(sanitized, CARD_PATTERN, "[CARD]", "card", guardrailActions, piiFindings);
        sanitized = maskIfPresent(sanitized, EMAIL_PATTERN, "[EMAIL]", "email", guardrailActions, piiFindings);
        return sanitized;
    }

    private static String maskIfPresent(
            String text,
            Pattern pattern,
            String replacement,
            String piiKind,
            List<String> guardrailActions,
            List<String> piiFindings) {
        Matcher matcher = pattern.matcher(text);
        if (!matcher.find()) {
            return text;
        }
        guardrailActions.add(piiKind + "_masked");
        piiFindings.add(piiKind);
        return matcher.replaceAll(replacement);
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
