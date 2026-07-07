package io.github.wantaekchoi.agentpay.shared.did;

import io.github.wantaekchoi.agentpay.shared.crypto.Signatures;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DidKeyTest {
    @Test
    void encodesSecp256k1PublicKeyAsDidKey() {
        var kp = Signatures.generateKeyPair();
        String did = DidKey.encode(kp.publicKey());

        assertThat(did).startsWith("did:key:z");
        // secp256k1 did:key(멀티코덱 0xe7 + 33바이트 압축키)는 base58btc에서 'zQ3s'로 시작한다.
        assertThat(did).startsWith("did:key:zQ3s");
    }

    @Test
    void isDeterministicForSameKey() {
        var kp = Signatures.generateKeyPair();
        assertThat(DidKey.encode(kp.publicKey())).isEqualTo(DidKey.encode(kp.publicKey()));
    }
}
