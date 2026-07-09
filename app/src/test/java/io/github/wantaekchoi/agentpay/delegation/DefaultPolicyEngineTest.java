package io.github.wantaekchoi.agentpay.delegation;

import io.github.wantaekchoi.agentpay.delegation.domain.Mandate;
import io.github.wantaekchoi.agentpay.delegation.domain.MandateStatus;
import io.github.wantaekchoi.agentpay.delegation.port.PaymentContext;
import io.github.wantaekchoi.agentpay.delegation.port.PolicyDecision;
import java.math.BigInteger;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPolicyEngineTest {

    private final DefaultPolicyEngine engine = new DefaultPolicyEngine();

    private static final String CURRENCY = "USD";
    private static final BigInteger PER_TX_LIMIT = BigInteger.valueOf(1_000);
    private static final BigInteger TOTAL_LIMIT = BigInteger.valueOf(10_000);
    private static final long VALID_FROM = 1_000L;
    private static final long VALID_UNTIL = 2_000L;
    private static final String ALLOWED_PAYEE = "merchant-1";

    private Mandate mandate(MandateStatus status, String currency, BigInteger perTxLimit,
                             BigInteger totalLimit, BigInteger spent, boolean allowAnyPayee,
                             Set<String> allowedPayees, long validFrom, long validUntil) {
        return new Mandate(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), currency,
                perTxLimit, totalLimit, spent, allowAnyPayee, allowedPayees,
                validFrom, validUntil, BigInteger.ONE, "0xusersig", status);
    }

    private Mandate defaultMandate() {
        return mandate(MandateStatus.ACTIVE, CURRENCY, PER_TX_LIMIT, TOTAL_LIMIT, BigInteger.ZERO,
                false, Set.of(ALLOWED_PAYEE), VALID_FROM, VALID_UNTIL);
    }

    private PaymentContext ctx(String payee, BigInteger amount, String currency, long epochSecond) {
        return new PaymentContext(payee, amount, currency, Instant.ofEpochSecond(epochSecond));
    }

    @Test
    void allows_whenAllConditionsMet() {
        Mandate m = defaultMandate();
        PaymentContext c = ctx(ALLOWED_PAYEE, BigInteger.valueOf(500), CURRENCY, 1_500L);

        PolicyDecision decision = engine.evaluate(m, c);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isEqualTo("OK");
    }

    @Test
    void rejects_whenRevoked() {
        Mandate m = mandate(MandateStatus.REVOKED, CURRENCY, PER_TX_LIMIT, TOTAL_LIMIT,
                BigInteger.ZERO, false, Set.of(ALLOWED_PAYEE), VALID_FROM, VALID_UNTIL);
        PaymentContext c = ctx(ALLOWED_PAYEE, BigInteger.valueOf(500), CURRENCY, 1_500L);

        PolicyDecision decision = engine.evaluate(m, c);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("REVOKED");
    }

    @Test
    void rejects_whenExpired() {
        Mandate m = defaultMandate();
        PaymentContext c = ctx(ALLOWED_PAYEE, BigInteger.valueOf(500), CURRENCY, VALID_UNTIL + 1);

        PolicyDecision decision = engine.evaluate(m, c);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("expired");
    }

    @Test
    void rejects_whenNotYetValid() {
        Mandate m = defaultMandate();
        PaymentContext c = ctx(ALLOWED_PAYEE, BigInteger.valueOf(500), CURRENCY, VALID_FROM - 1);

        PolicyDecision decision = engine.evaluate(m, c);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("not-yet-valid");
    }

    @Test
    void rejects_whenCurrencyMismatch() {
        Mandate m = defaultMandate();
        PaymentContext c = ctx(ALLOWED_PAYEE, BigInteger.valueOf(500), "EUR", 1_500L);

        PolicyDecision decision = engine.evaluate(m, c);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("currency");
    }

    @Test
    void rejects_whenPayeeNotAllowed() {
        Mandate m = defaultMandate();
        PaymentContext c = ctx("merchant-2", BigInteger.valueOf(500), CURRENCY, 1_500L);

        PolicyDecision decision = engine.evaluate(m, c);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("payee");
    }

    @Test
    void allows_whenAllowAny_regardlessOfPayee() {
        Mandate m = mandate(MandateStatus.ACTIVE, CURRENCY, PER_TX_LIMIT, TOTAL_LIMIT,
                BigInteger.ZERO, true, Set.of(), VALID_FROM, VALID_UNTIL);
        PaymentContext c = ctx("some-other-merchant", BigInteger.valueOf(500), CURRENCY, 1_500L);

        PolicyDecision decision = engine.evaluate(m, c);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isEqualTo("OK");
    }

    @Test
    void rejects_whenAmountExceedsPerTxLimit() {
        Mandate m = defaultMandate();
        PaymentContext c = ctx(ALLOWED_PAYEE, PER_TX_LIMIT.add(BigInteger.ONE), CURRENCY, 1_500L);

        PolicyDecision decision = engine.evaluate(m, c);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("perTxLimit");
    }

    @Test
    void rejects_whenSpentPlusAmountExceedsTotal() {
        Mandate m = mandate(MandateStatus.ACTIVE, CURRENCY, PER_TX_LIMIT, BigInteger.valueOf(1_000),
                BigInteger.valueOf(900), false, Set.of(ALLOWED_PAYEE), VALID_FROM, VALID_UNTIL);
        PaymentContext c = ctx(ALLOWED_PAYEE, BigInteger.valueOf(200), CURRENCY, 1_500L);

        PolicyDecision decision = engine.evaluate(m, c);

        assertThat(decision.allowed()).isFalse();
        assertThat(decision.reason()).contains("totalLimit");
    }

    @Test
    void allows_atExactBoundaries() {
        BigInteger spent = BigInteger.valueOf(9_000);
        Mandate m = mandate(MandateStatus.ACTIVE, CURRENCY, PER_TX_LIMIT, TOTAL_LIMIT,
                spent, false, Set.of(ALLOWED_PAYEE), VALID_FROM, VALID_UNTIL);
        PaymentContext c = ctx(ALLOWED_PAYEE, PER_TX_LIMIT, CURRENCY, VALID_UNTIL);

        PolicyDecision decision = engine.evaluate(m, c);

        assertThat(decision.allowed()).isTrue();
        assertThat(decision.reason()).isEqualTo("OK");
        assertThat(spent.add(PER_TX_LIMIT)).isEqualTo(TOTAL_LIMIT);
    }
}
