package io.github.wantaekchoi.agentpay.identity.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wantaekchoi.agentpay.TestcontainersConfiguration;
import io.github.wantaekchoi.agentpay.identity.domain.User;
import io.github.wantaekchoi.agentpay.identity.domain.UserRepository;
import io.github.wantaekchoi.agentpay.shared.crypto.Signatures;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.UUID;
import org.web3j.utils.Numeric;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AgentControllerTest {

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    // Spring Boot 4.1은 Jackson 3(tools.jackson)을 기본 자동구성하며 classic
    // com.fasterxml.jackson.databind.ObjectMapper 빈을 등록하지 않는다.
    // 테스트 바디 직렬화/응답 파싱용으로만 쓰이므로 스프링 빈이 아닌 순수 인스턴스로 대체.
    ObjectMapper json = new ObjectMapper();

    @Test
    void registerThenVerifyRoundTrip() throws Exception {
        var ownerKp = Signatures.generateKeyPair();
        User owner = new User(UUID.randomUUID(), "alice", "0xpub", ownerKp.address());
        users.save(owner);

        var agentKp = Signatures.generateKeyPair();
        String pubHex = Numeric.toHexStringWithPrefixZeroPadded(agentKp.publicKey(), 128);

        String body = json.writeValueAsString(Map.of(
                "ownerUserId", owner.getId().toString(),
                "publicKey", pubHex,
                "alias", "shopper"));

        String resp = mvc.perform(post("/agents")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.did").value(Matchers.startsWith("did:key:zQ3s")))
                .andReturn().getResponse().getContentAsString();

        String agentId = json.readTree(resp).get("id").asText();

        String challenge = "nonce-xyz";
        String sig = Signatures.sign(agentKp.privateKey(), challenge);
        String verifyBody = json.writeValueAsString(Map.of("message", challenge, "signature", sig));

        mvc.perform(post("/agents/" + agentId + "/verify")
                        .contentType(MediaType.APPLICATION_JSON).content(verifyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void servesDidWebDocument() throws Exception {
        var ownerKp = Signatures.generateKeyPair();
        User owner = new User(UUID.randomUUID(), "carol", "0xpub", ownerKp.address());
        users.save(owner);
        var agentKp = Signatures.generateKeyPair();
        String pubHex = Numeric.toHexStringWithPrefixZeroPadded(agentKp.publicKey(), 128);
        String body = json.writeValueAsString(Map.of(
                "ownerUserId", owner.getId().toString(), "publicKey", pubHex, "alias", "shopper"));
        String resp = mvc.perform(post("/agents").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        String agentId = json.readTree(resp).get("id").asText();

        mvc.perform(get("/agents/" + agentId + "/did.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(Matchers.containsString("did:web")))
                .andExpect(jsonPath("$.verificationMethod[0].blockchainAccountId")
                        .value(Matchers.containsString(agentKp.address())));
    }
}
