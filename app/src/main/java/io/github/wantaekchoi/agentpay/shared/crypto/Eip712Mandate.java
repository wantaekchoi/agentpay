package io.github.wantaekchoi.agentpay.shared.crypto;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.crypto.StructuredDataEncoder;
import org.web3j.utils.Numeric;

public final class Eip712Mandate {
    private Eip712Mandate() {}

    public record MandateData(String user, String agent, String currency,
            BigInteger perTxLimit, BigInteger totalLimit, List<String> allowedPayees,
            boolean allowAny, long validFrom, long validUntil, BigInteger nonce) {}

    public static String typedDataJson(MandateData d, long chainId) {
        String payees = d.allowedPayees().stream()
                .map(p -> "\"" + p + "\"")
                .collect(Collectors.joining(","));
        // EIP-712 JSON. 필드 순서는 types 정의 순서와 일치해야 함.
        return "{"
                + "\"types\":{"
                + "\"EIP712Domain\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"version\",\"type\":\"string\"},{\"name\":\"chainId\",\"type\":\"uint256\"}],"
                + "\"AgentPaymentMandate\":["
                + "{\"name\":\"user\",\"type\":\"address\"},{\"name\":\"agent\",\"type\":\"address\"},"
                + "{\"name\":\"currency\",\"type\":\"string\"},"
                + "{\"name\":\"perTxLimit\",\"type\":\"uint256\"},{\"name\":\"totalLimit\",\"type\":\"uint256\"},"
                + "{\"name\":\"allowedPayees\",\"type\":\"address[]\"},{\"name\":\"allowAny\",\"type\":\"bool\"},"
                + "{\"name\":\"validFrom\",\"type\":\"uint256\"},{\"name\":\"validUntil\",\"type\":\"uint256\"},{\"name\":\"nonce\",\"type\":\"uint256\"}"
                + "]},"
                + "\"primaryType\":\"AgentPaymentMandate\","
                + "\"domain\":{\"name\":\"agentpay\",\"version\":\"1\",\"chainId\":" + chainId + "},"
                + "\"message\":{"
                + "\"user\":\"" + d.user() + "\",\"agent\":\"" + d.agent() + "\","
                + "\"currency\":\"" + d.currency() + "\","
                + "\"perTxLimit\":" + d.perTxLimit() + ",\"totalLimit\":" + d.totalLimit() + ","
                + "\"allowedPayees\":[" + payees + "],\"allowAny\":" + d.allowAny() + ","
                + "\"validFrom\":" + d.validFrom() + ",\"validUntil\":" + d.validUntil() + ",\"nonce\":" + d.nonce()
                + "}}";
    }

    private static byte[] digest(MandateData d, long chainId) {
        try {
            return new StructuredDataEncoder(typedDataJson(d, chainId)).hashStructuredData();
        } catch (Exception e) {
            throw new IllegalArgumentException("EIP-712 인코딩 실패", e);
        }
    }

    public static String sign(MandateData d, long chainId, BigInteger privateKey) {
        Sign.SignatureData sd = Sign.signMessage(digest(d, chainId), ECKeyPair.create(privateKey), false);
        byte[] out = new byte[65];
        System.arraycopy(sd.getR(), 0, out, 0, 32);
        System.arraycopy(sd.getS(), 0, out, 32, 32);
        out[64] = sd.getV()[0];
        return Numeric.toHexString(out);
    }

    public static String recoverSigner(MandateData d, long chainId, String signatureHex) {
        byte[] sig = Numeric.hexStringToByteArray(signatureHex);
        if (sig.length != 65) {
            throw new IllegalArgumentException("서명 길이 오류");
        }
        byte[] r = new byte[32];
        byte[] s = new byte[32];
        System.arraycopy(sig, 0, r, 0, 32);
        System.arraycopy(sig, 32, s, 0, 32);
        Sign.SignatureData sd = new Sign.SignatureData(sig[64], r, s);
        try {
            BigInteger pub = Sign.signedMessageHashToKey(digest(d, chainId), sd);
            return Numeric.prependHexPrefix(Keys.getAddress(pub)).toLowerCase();
        } catch (Exception e) {
            throw new IllegalArgumentException("서명 복구 실패", e);
        }
    }
}
