package io.github.wantaekchoi.agentpay.shared.crypto;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

public final class Signatures {
    private Signatures() {}

    public record KeyPair(BigInteger privateKey, BigInteger publicKey, String address) {
        @Override
        public String toString() {
            return "KeyPair[address=" + address + "]";
        }
    }

    public static KeyPair generateKeyPair() {
        try {
            ECKeyPair kp = Keys.createEcKeyPair();
            String address = Numeric.prependHexPrefix(Keys.getAddress(kp.getPublicKey()));
            return new KeyPair(kp.getPrivateKey(), kp.getPublicKey(), address.toLowerCase());
        } catch (Exception e) {
            throw new IllegalStateException("keypair 생성 실패", e);
        }
    }

    public static String sign(BigInteger privateKey, String message) {
        ECKeyPair kp = ECKeyPair.create(privateKey);
        Sign.SignatureData sd = Sign.signPrefixedMessage(
                message.getBytes(StandardCharsets.UTF_8), kp);
        byte[] out = new byte[65];
        System.arraycopy(sd.getR(), 0, out, 0, 32);
        System.arraycopy(sd.getS(), 0, out, 32, 32);
        out[64] = sd.getV()[0];
        return Numeric.toHexString(out);
    }

    public static String recoverAddress(String message, String signatureHex) {
        byte[] sig;
        try {
            sig = Numeric.hexStringToByteArray(signatureHex);
        } catch (Exception e) {
            throw new IllegalArgumentException("서명 hex 파싱 실패: " + signatureHex, e);
        }
        if (sig.length != 65) {
            throw new IllegalArgumentException(
                    "서명 길이가 올바르지 않습니다. 65바이트가 필요하지만 " + sig.length + "바이트입니다.");
        }
        byte[] r = new byte[32];
        byte[] s = new byte[32];
        System.arraycopy(sig, 0, r, 0, 32);
        System.arraycopy(sig, 32, s, 0, 32);
        byte v = sig[64];
        Sign.SignatureData sd = new Sign.SignatureData(v, r, s);
        try {
            BigInteger pubKey = Sign.signedPrefixedMessageToKey(
                    message.getBytes(StandardCharsets.UTF_8), sd);
            return Numeric.prependHexPrefix(Keys.getAddress(pubKey)).toLowerCase();
        } catch (Exception e) {
            throw new IllegalArgumentException("서명 복구 실패", e);
        }
    }
}
