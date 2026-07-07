package io.github.wantaekchoi.agentpay.shared.crypto;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SignaturesTest {

    @Test
    void signedMessage_recoversToSignerAddress() {
        var kp = Signatures.generateKeyPair();
        String message = "challenge-abc-123";

        String sig = Signatures.sign(kp.privateKey(), message);
        String recovered = Signatures.recoverAddress(message, sig);

        assertThat(recovered).isEqualTo(kp.address());
    }

    @Test
    void tamperedMessage_recoversToDifferentAddress() {
        var kp = Signatures.generateKeyPair();
        String sig = Signatures.sign(kp.privateKey(), "original");

        String recovered = Signatures.recoverAddress("tampered", sig);

        assertThat(recovered).isNotEqualTo(kp.address());
    }
}
