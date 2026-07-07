package io.github.wantaekchoi.agentpay.identity.web;

import io.github.wantaekchoi.agentpay.identity.domain.Agent;
import io.github.wantaekchoi.agentpay.identity.domain.AgentRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class DidWebController {
    private final AgentRepository agents;

    public DidWebController(AgentRepository agents) { this.agents = agents; }

    // did:web DID Document. did:web:<host>:agents:<id> 규약.
    @GetMapping("/agents/{id}/did.json")
    public Map<String, Object> didDocument(@PathVariable UUID id, HttpServletRequest request) {
        Agent a = agents.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("agent 미존재: " + id));
        String host = request.getServerName() + ":" + request.getServerPort();
        String didWeb = "did:web:" + host + ":agents:" + id;
        Map<String, Object> vm = Map.of(
                "id", didWeb + "#key-1",
                "type", "EcdsaSecp256k1RecoveryMethod2020",
                "controller", didWeb,
                "blockchainAccountId", "eip155:1:" + a.getAddress());
        return Map.of(
                "@context", List.of("https://www.w3.org/ns/did/v1"),
                "id", didWeb,
                "verificationMethod", List.of(vm),
                "authentication", List.of(didWeb + "#key-1"),
                "alsoKnownAs", List.of(a.getDid()));
    }
}
