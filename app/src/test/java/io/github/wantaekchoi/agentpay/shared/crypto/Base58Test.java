package io.github.wantaekchoi.agentpay.shared.crypto;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

class Base58Test {
    @Test
    void encodesKnownVector() {
        assertThat(Base58.encode("Hello World!".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("2NEpo7TZRRrLZSi2U");
    }

    @Test
    void leadingZeroBytesBecomeLeadingOnes() {
        assertThat(Base58.encode(new byte[]{0, 0, 1})).startsWith("11");
    }
}
