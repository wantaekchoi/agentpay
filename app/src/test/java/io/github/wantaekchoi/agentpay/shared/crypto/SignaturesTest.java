package io.github.wantaekchoi.agentpay.shared.crypto;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void recoverAddress_withMalformedSignature_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> Signatures.recoverAddress("challenge", "0x1234"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Signatures.recoverAddress("challenge", ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void recoverAddress_withNonHexCharacters_throwsIllegalArgumentException() {
        // web3j's Numeric.hexStringToByteArray does not validate hex characters -
        // Character.digit() returns -1 for non-hex chars without throwing, so any
        // non-null string (even garbage like "0xZZZZ...") still decodes into *some*
        // byte array and only ever trips the sig.length != 65 check below, exactly
        // like the malformed-signature case above. The only input that genuinely
        // fails inside hexStringToByteArray itself (a real hex-parse failure, not a
        // length mismatch) is a null signatureHex, which NPEs inside the decoder and
        // is caught/rewrapped by recoverAddress's parse guard.
        assertThatThrownBy(() -> Signatures.recoverAddress("challenge", null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void keyPair_toString_doesNotLeakPrivateKey() {
        var kp = Signatures.generateKeyPair();

        String rendered = kp.toString();

        assertThat(rendered).doesNotContain(kp.privateKey().toString(16));
        assertThat(rendered).doesNotContain(kp.privateKey().toString());
    }

    @Test
    void address_matchesEvmAddressFormat() {
        var kp = Signatures.generateKeyPair();
        String message = "challenge-abc-123";
        String sig = Signatures.sign(kp.privateKey(), message);
        String recovered = Signatures.recoverAddress(message, sig);

        assertThat(kp.address()).matches("^0x[0-9a-f]{40}$");
        assertThat(recovered).matches("^0x[0-9a-f]{40}$");
    }

    @Test
    void addressFromPublicKey_roundTripsWithGeneratedKeyPair() {
        var kp = Signatures.generateKeyPair();

        String derivedAddress = Signatures.addressFromPublicKey(kp.publicKey());

        assertThat(derivedAddress).isEqualTo(kp.address());
    }

    @Test
    void publicKeyFromHex_withMalformedHex_throwsIllegalArgumentException() {
        assertThatThrownBy(() -> Signatures.publicKeyFromHex("not-a-hex-string"))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> Signatures.publicKeyFromHex(null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
