package io.github.wantaekchoi.agentpay;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AgentpayApplicationTests {

    @Test
    void contextLoads() {
    }
}
