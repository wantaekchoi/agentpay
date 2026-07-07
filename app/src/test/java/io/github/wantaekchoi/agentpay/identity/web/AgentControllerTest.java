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
                .andExpect(jsonPath("$.id").value(Matchers.containsString("did:web:")))
                .andExpect(jsonPath("$.id").value(Matchers.containsString("localhost%3A")))
                .andExpect(jsonPath("$.id").value(Matchers.containsString(":agents:")))
                .andExpect(jsonPath("$.verificationMethod[0].blockchainAccountId")
                        .value(Matchers.containsString(agentKp.address())));
    }

    @Test
    void registerWithMalformedPublicKeyReturns400() throws Exception {
        var ownerKp = Signatures.generateKeyPair();
        User owner = new User(UUID.randomUUID(), "dave", "0xpub", ownerKp.address());
        users.save(owner);

        String body = json.writeValueAsString(Map.of(
                "ownerUserId", owner.getId().toString(),
                "publicKey", "0x123",
                "alias", "shopper"));

        mvc.perform(post("/agents").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void getCardReturnsRegisteredAgentDetails() throws Exception {
        var ownerKp = Signatures.generateKeyPair();
        User owner = new User(UUID.randomUUID(), "frank", "0xpub", ownerKp.address());
        users.save(owner);

        var agentKp = Signatures.generateKeyPair();
        String pubHex = Numeric.toHexStringWithPrefixZeroPadded(agentKp.publicKey(), 128);
        String body = json.writeValueAsString(Map.of(
                "ownerUserId", owner.getId().toString(), "publicKey", pubHex, "alias", "shopper"));
        String resp = mvc.perform(post("/agents").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        String agentId = json.readTree(resp).get("id").asText();

        mvc.perform(get("/agents/" + agentId + "/card"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").exists())
                .andExpect(jsonPath("$.did").exists())
                .andExpect(jsonPath("$.address").value(agentKp.address()));
    }

    @Test
    void postAgentsWithUnparsableJsonBodyReturns400() throws Exception {
        mvc.perform(post("/agents").contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void getCardWithNonUuidPathSegmentReturns400() throws Exception {
        mvc.perform(get("/agents/not-a-uuid/card"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.message")
                        .value(Matchers.containsString("요청 파라미터 형식이 올바르지 않습니다")));
    }

    @Test
    void registerWithMissingOwnerReturns404() throws Exception {
        var agentKp = Signatures.generateKeyPair();
        String pubHex = Numeric.toHexStringWithPrefixZeroPadded(agentKp.publicKey(), 128);

        String body = json.writeValueAsString(Map.of(
                "ownerUserId", UUID.randomUUID().toString(),
                "publicKey", pubHex,
                "alias", "shopper"));

        mvc.perform(post("/agents").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isNotFound());
    }

    @Test
    void cardForUnknownAgentReturns404() throws Exception {
        mvc.perform(get("/agents/" + UUID.randomUUID() + "/card"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void didDocumentForUnknownAgentReturns404() throws Exception {
        mvc.perform(get("/agents/" + UUID.randomUUID() + "/did.json"))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicatePublicKeyReturns409() throws Exception {
        var ownerKp = Signatures.generateKeyPair();
        User owner = new User(UUID.randomUUID(), "erin", "0xpub", ownerKp.address());
        users.save(owner);

        var agentKp = Signatures.generateKeyPair();
        String pubHex = Numeric.toHexStringWithPrefixZeroPadded(agentKp.publicKey(), 128);

        String firstBody = json.writeValueAsString(Map.of(
                "ownerUserId", owner.getId().toString(), "publicKey", pubHex, "alias", "shopper-1"));
        mvc.perform(post("/agents").contentType(MediaType.APPLICATION_JSON).content(firstBody))
                .andExpect(status().isCreated());

        String secondBody = json.writeValueAsString(Map.of(
                "ownerUserId", owner.getId().toString(), "publicKey", pubHex, "alias", "shopper-2"));
        mvc.perform(post("/agents").contentType(MediaType.APPLICATION_JSON).content(secondBody))
                .andExpect(status().isConflict());
    }

    @Test
    void verifyForUnknownAgentReturnsInvalidWithoutError() throws Exception {
        String verifyBody = json.writeValueAsString(Map.of("message", "nonce", "signature", "0x00"));

        mvc.perform(post("/agents/" + UUID.randomUUID() + "/verify")
                        .contentType(MediaType.APPLICATION_JSON).content(verifyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(false));
    }
}
