package io.github.wantaekchoi.agentpay.shared.crypto;

import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

class Eip712MandateTest {
    private Eip712Mandate.MandateData sample(String user, String agent) {
        return new Eip712Mandate.MandateData(user, agent, "USDC",
                BigInteger.valueOf(100), BigInteger.valueOf(1000),
                List.of("0x1111111111111111111111111111111111111111"), false,
                1000L, 2000L, BigInteger.ONE);
    }

    @Test
    void signThenRecover_returnsSignerAddress() {
        var kp = Signatures.generateKeyPair();
        var d = sample(kp.address(), "0x2222222222222222222222222222222222222222");
        String sig = Eip712Mandate.sign(d, 31337L, kp.privateKey());
        assertThat(Eip712Mandate.recoverSigner(d, 31337L, sig)).isEqualTo(kp.address());
    }

    @Test
    void tamperedField_recoversDifferentAddress() {
        var kp = Signatures.generateKeyPair();
        var d = sample(kp.address(), "0x2222222222222222222222222222222222222222");
        String sig = Eip712Mandate.sign(d, 31337L, kp.privateKey());
        var tampered = sample(kp.address(), "0x3333333333333333333333333333333333333333");
        assertThat(Eip712Mandate.recoverSigner(tampered, 31337L, sig)).isNotEqualTo(kp.address());
    }

    @Test
    void stringFieldWithQuote_stillSignsAndRecovers() {
        var kp = Signatures.generateKeyPair();
        var d = new Eip712Mandate.MandateData(kp.address(), "0x2222222222222222222222222222222222222222",
                "USD\"C", BigInteger.valueOf(100), BigInteger.valueOf(1000),
                List.of("0x1111111111111111111111111111111111111111"), false,
                1000L, 2000L, BigInteger.ONE);
        String sig = Eip712Mandate.sign(d, 31337L, kp.privateKey());
        assertThat(Eip712Mandate.recoverSigner(d, 31337L, sig)).isEqualTo(kp.address());
    }

    @Test
    void signRevocationThenRecover_returnsSignerAddress() {
        var kp = Signatures.generateKeyPair();
        var d = new Eip712Mandate.RevocationData(kp.address(), UUID.randomUUID().toString());
        String sig = Eip712Mandate.signRevocation(d, 31337L, kp.privateKey());
        assertThat(Eip712Mandate.recoverRevocationSigner(d, 31337L, sig)).isEqualTo(kp.address());
    }

    @Test
    void revocationWithTamperedMandateId_recoversDifferentAddress() {
        var kp = Signatures.generateKeyPair();
        var d = new Eip712Mandate.RevocationData(kp.address(), UUID.randomUUID().toString());
        String sig = Eip712Mandate.signRevocation(d, 31337L, kp.privateKey());
        var tampered = new Eip712Mandate.RevocationData(kp.address(), UUID.randomUUID().toString());
        assertThat(Eip712Mandate.recoverRevocationSigner(tampered, 31337L, sig)).isNotEqualTo(kp.address());
    }
}
