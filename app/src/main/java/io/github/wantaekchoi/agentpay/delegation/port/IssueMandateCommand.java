package io.github.wantaekchoi.agentpay.delegation.port;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;

public record IssueMandateCommand(
        UUID userId,
        UUID agentId,
        String currency,
        BigInteger perTxLimit,
        BigInteger totalLimit,
        List<String> allowedPayees,
        boolean allowAny,
        long validFrom,
        long validUntil,
        BigInteger nonce,
        String userSignature) {}
