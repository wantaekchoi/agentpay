package io.github.wantaekchoi.agentpay.shared.did;

import io.github.wantaekchoi.agentpay.shared.crypto.Base58;
import java.math.BigInteger;
import java.util.Arrays;
import org.web3j.utils.Numeric;

public final class DidKey {
    // secp256k1-pub 멀티코덱 0xe7 의 unsigned-varint 인코딩
    private static final byte[] MULTICODEC_SECP256K1 = {(byte) 0xe7, (byte) 0x01};

    private DidKey() {}

    public static String encode(BigInteger publicKey) {
        byte[] compressed = compress(publicKey);
        byte[] prefixed = new byte[MULTICODEC_SECP256K1.length + compressed.length];
        System.arraycopy(MULTICODEC_SECP256K1, 0, prefixed, 0, MULTICODEC_SECP256K1.length);
        System.arraycopy(compressed, 0, prefixed, MULTICODEC_SECP256K1.length, compressed.length);
        return "did:key:z" + Base58.encode(prefixed);
    }

    // web3j 공개키(64바이트 X||Y 비압축)를 33바이트 압축키로.
    static byte[] compress(BigInteger publicKey) {
        byte[] full = Numeric.toBytesPadded(publicKey, 64);
        byte[] x = Arrays.copyOfRange(full, 0, 32);
        byte yParity = (byte) (((full[63] & 1) == 0) ? 0x02 : 0x03);
        byte[] out = new byte[33];
        out[0] = yParity;
        System.arraycopy(x, 0, out, 1, 32);
        return out;
    }
}
