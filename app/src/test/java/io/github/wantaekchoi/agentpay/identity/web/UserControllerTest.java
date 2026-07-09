package io.github.wantaekchoi.agentpay.identity.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wantaekchoi.agentpay.TestcontainersConfiguration;
import io.github.wantaekchoi.agentpay.shared.crypto.Signatures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import org.web3j.utils.Numeric;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class UserControllerTest {

    @Autowired MockMvc mvc;
    // Spring Boot 4.1은 Jackson 3(tools.jackson)을 기본 자동구성하며 classic
    // com.fasterxml.jackson.databind.ObjectMapper 빈을 등록하지 않는다.
    // 테스트 바디 직렬화/응답 파싱용으로만 쓰이므로 스프링 빈이 아닌 순수 인스턴스로 대체.
    ObjectMapper json = new ObjectMapper();

    @Test
    void registerWithValidPublicKeyReturns201() throws Exception {
        var kp = Signatures.generateKeyPair();
        String pubHex = Numeric.toHexStringWithPrefixZeroPadded(kp.publicKey(), 128);

        String body = json.writeValueAsString(Map.of(
                "publicKey", pubHex,
                "alias", "alice"));

        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.address").value(kp.address()));
    }

    @Test
    void duplicatePublicKeyReturns409() throws Exception {
        var kp = Signatures.generateKeyPair();
        String pubHex = Numeric.toHexStringWithPrefixZeroPadded(kp.publicKey(), 128);

        String firstBody = json.writeValueAsString(Map.of(
                "publicKey", pubHex,
                "alias", "alice"));
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(firstBody))
                .andExpect(status().isCreated());

        String secondBody = json.writeValueAsString(Map.of(
                "publicKey", pubHex,
                "alias", "alice-2"));
        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(secondBody))
                .andExpect(status().isConflict());
    }

    @Test
    void registerWithMalformedPublicKeyReturns400() throws Exception {
        String body = json.writeValueAsString(Map.of(
                "publicKey", "0x123",
                "alias", "alice"));

        mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists());
    }
}
