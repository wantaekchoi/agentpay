package io.github.wantaekchoi.agentpay.delegation.port;

import java.math.BigInteger;
import java.time.Instant;

public record PaymentContext(String payee, BigInteger amount, String currency, Instant now) {
}
