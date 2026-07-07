package io.github.wantaekchoi.agentpay.delegation.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wantaekchoi.agentpay.TestcontainersConfiguration;
import io.github.wantaekchoi.agentpay.shared.crypto.Eip712Mandate;
import io.github.wantaekchoi.agentpay.shared.crypto.Signatures;
import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.web3j.utils.Numeric;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class MandateControllerTest {

    private static final long CHAIN_ID = 31337L;
    private static final String PAYEE = "0x1111111111111111111111111111111111111111";

    @Autowired MockMvc mvc;
    // Spring Boot 4.1은 Jackson 3(tools.jackson)을 기본 자동구성하며 classic
    // com.fasterxml.jackson.databind.ObjectMapper 빈을 등록하지 않는다.
    // 테스트 바디 직렬화/응답 파싱용으로만 쓰이므로 스프링 빈이 아닌 순수 인스턴스로 대체.
    ObjectMapper json = new ObjectMapper();

    private record Registered(UUID id, String address) {}

    private Registered registerUser(Signatures.KeyPair kp, String alias) throws Exception {
        String pubHex = Numeric.toHexStringWithPrefixZeroPadded(kp.publicKey(), 128);
        String body = json.writeValueAsString(Map.of("publicKey", pubHex, "alias", alias));
        String resp = mvc.perform(post("/users").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var node = json.readTree(resp);
        return new Registered(UUID.fromString(node.get("id").asText()), node.get("address").asText());
    }

    private Registered registerAgent(UUID ownerUserId, Signatures.KeyPair kp, String alias) throws Exception {
        String pubHex = Numeric.toHexStringWithPrefixZeroPadded(kp.publicKey(), 128);
        String body = json.writeValueAsString(Map.of(
                "ownerUserId", ownerUserId.toString(), "publicKey", pubHex, "alias", alias));
        String resp = mvc.perform(post("/agents").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        var node = json.readTree(resp);
        return new Registered(UUID.fromString(node.get("id").asText()), node.get("address").asText());
    }

    private Eip712Mandate.MandateData dataFor(String userAddress, String agentAddress, String currency, BigInteger nonce) {
        return new Eip712Mandate.MandateData(userAddress, agentAddress, currency,
                BigInteger.valueOf(1_000), BigInteger.valueOf(10_000),
                List.of(PAYEE), false, 1_000L, 2_000L, nonce);
    }

    private Map<String, Object> mandateBody(UUID userId, UUID agentId, Eip712Mandate.MandateData data, String sig) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("userId", userId.toString());
        body.put("agentId", agentId.toString());
        body.put("currency", data.currency());
        body.put("perTxLimit", data.perTxLimit());
        body.put("totalLimit", data.totalLimit());
        body.put("allowedPayees", data.allowedPayees());
        body.put("allowAny", data.allowAny());
        body.put("validFrom", data.validFrom());
        body.put("validUntil", data.validUntil());
        body.put("nonce", data.nonce());
        body.put("userSignature", sig);
        return body;
    }

    @Test
    void issueThenGetThenRevoke_fullLifecycle() throws Exception {
        var userKp = Signatures.generateKeyPair();
        var agentKp = Signatures.generateKeyPair();
        Registered user = registerUser(userKp, "alice");
        Registered agent = registerAgent(user.id(), agentKp, "shopper");

        var data = dataFor(user.address(), agent.address(), "USDC", BigInteger.valueOf(1));
        String sig = Eip712Mandate.sign(data, CHAIN_ID, userKp.privateKey());
        String body = json.writeValueAsString(mandateBody(user.id(), agent.id(), data, sig));

        String issueResp = mvc.perform(post("/mandates").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        String mandateId = json.readTree(issueResp).get("id").asText();

        mvc.perform(get("/mandates/" + mandateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mandateId))
                .andExpect(jsonPath("$.userId").value(user.id().toString()))
                .andExpect(jsonPath("$.agentId").value(agent.id().toString()))
                .andExpect(jsonPath("$.currency").value("USDC"))
                .andExpect(jsonPath("$.perTxLimit").value(1000))
                .andExpect(jsonPath("$.totalLimit").value(10000))
                .andExpect(jsonPath("$.spent").value(0))
                .andExpect(jsonPath("$.allowAny").value(false))
                .andExpect(jsonPath("$.allowedPayees", org.hamcrest.Matchers.hasItem(PAYEE)))
                .andExpect(jsonPath("$.validFrom").value(1000))
                .andExpect(jsonPath("$.validUntil").value(2000))
                .andExpect(jsonPath("$.status").value("ACTIVE"));

        mvc.perform(post("/mandates/" + mandateId + "/revoke"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(mandateId))
                .andExpect(jsonPath("$.status").value("REVOKED"));

        mvc.perform(get("/mandates/" + mandateId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REVOKED"));
    }

    @Test
    void issueWithSignatureFromDifferentKey_returns400() throws Exception {
        var userKp = Signatures.generateKeyPair();
        var agentKp = Signatures.generateKeyPair();
        var otherKp = Signatures.generateKeyPair();
        Registered user = registerUser(userKp, "bob");
        Registered agent = registerAgent(user.id(), agentKp, "shopper");

        var data = dataFor(user.address(), agent.address(), "USDC", BigInteger.valueOf(2));
        String sig = Eip712Mandate.sign(data, CHAIN_ID, otherKp.privateKey());
        String body = json.writeValueAsString(mandateBody(user.id(), agent.id(), data, sig));

        mvc.perform(post("/mandates").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void getUnknownId_returns404() throws Exception {
        mvc.perform(get("/mandates/" + UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void revokeUnknownId_returns404() throws Exception {
        mvc.perform(post("/mandates/" + UUID.randomUUID() + "/revoke"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void duplicateNonce_returns409() throws Exception {
        var userKp = Signatures.generateKeyPair();
        var agentKp = Signatures.generateKeyPair();
        Registered user = registerUser(userKp, "carol");
        Registered agent = registerAgent(user.id(), agentKp, "shopper");

        var data = dataFor(user.address(), agent.address(), "USDC", BigInteger.valueOf(3));
        String sig = Eip712Mandate.sign(data, CHAIN_ID, userKp.privateKey());
        String body = json.writeValueAsString(mandateBody(user.id(), agent.id(), data, sig));

        mvc.perform(post("/mandates").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mvc.perform(post("/mandates").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void malformedCurrency_returns400() throws Exception {
        var userKp = Signatures.generateKeyPair();
        var agentKp = Signatures.generateKeyPair();
        Registered user = registerUser(userKp, "dave");
        Registered agent = registerAgent(user.id(), agentKp, "shopper");

        var data = dataFor(user.address(), agent.address(), "US DC", BigInteger.valueOf(4));
        String sig = Eip712Mandate.sign(data, CHAIN_ID, userKp.privateKey());
        String body = json.writeValueAsString(mandateBody(user.id(), agent.id(), data, sig));

        mvc.perform(post("/mandates").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void malformedAllowedPayee_returns400() throws Exception {
        var userKp = Signatures.generateKeyPair();
        var agentKp = Signatures.generateKeyPair();
        Registered user = registerUser(userKp, "erin");
        Registered agent = registerAgent(user.id(), agentKp, "shopper");

        var data = new Eip712Mandate.MandateData(user.address(), agent.address(), "USDC",
                BigInteger.valueOf(1_000), BigInteger.valueOf(10_000),
                List.of("not-an-address"), false, 1_000L, 2_000L, BigInteger.valueOf(5));
        String sig = Eip712Mandate.sign(data, CHAIN_ID, userKp.privateKey());
        String body = json.writeValueAsString(mandateBody(user.id(), agent.id(), data, sig));

        mvc.perform(post("/mandates").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void listByAgentId_returnsIssuedMandate() throws Exception {
        var userKp = Signatures.generateKeyPair();
        var agentKp = Signatures.generateKeyPair();
        Registered user = registerUser(userKp, "frank");
        Registered agent = registerAgent(user.id(), agentKp, "shopper");

        var data = dataFor(user.address(), agent.address(), "USDC", BigInteger.valueOf(6));
        String sig = Eip712Mandate.sign(data, CHAIN_ID, userKp.privateKey());
        String body = json.writeValueAsString(mandateBody(user.id(), agent.id(), data, sig));
        mvc.perform(post("/mandates").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        mvc.perform(get("/mandates").param("agentId", agent.id().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].agentId").value(agent.id().toString()))
                .andExpect(jsonPath("$[0].allowedPayees", org.hamcrest.Matchers.hasItem(PAYEE)));

        mvc.perform(get("/mandates").param("userId", user.id().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(user.id().toString()));
    }

    @Test
    void listWithoutParams_returns400() throws Exception {
        mvc.perform(get("/mandates"))
                .andExpect(status().isBadRequest());
    }
}
