package io.github.wantaekchoi.agentpay.shared.crypto;

import java.math.BigInteger;

public final class Base58 {
    private static final String ALPHABET =
            "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final BigInteger BASE = BigInteger.valueOf(58);

    private Base58() {}

    public static String encode(byte[] input) {
        if (input.length == 0) return "";
        int zeros = 0;
        while (zeros < input.length && input[zeros] == 0) zeros++;

        BigInteger num = new BigInteger(1, input);
        StringBuilder sb = new StringBuilder();
        while (num.signum() > 0) {
            BigInteger[] dr = num.divideAndRemainder(BASE);
            num = dr[0];
            sb.append(ALPHABET.charAt(dr[1].intValue()));
        }
        for (int i = 0; i < zeros; i++) sb.append(ALPHABET.charAt(0));
        return sb.reverse().toString();
    }
}
