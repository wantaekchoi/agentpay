package io.github.wantaekchoi.agentpay.identity.web;

import io.github.wantaekchoi.agentpay.identity.domain.Agent;
import io.github.wantaekchoi.agentpay.identity.domain.AgentRepository;
import io.github.wantaekchoi.agentpay.shared.error.NotFoundException;
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
                .orElseThrow(() -> new NotFoundException("agent 미존재: " + id));
        // did:web 규약상 host:port의 콜론은 %3A로 percent-encode한다.
        // 경로 구분자(:agents:)의 콜론은 인코딩하지 않는다.
        String host = request.getServerName() + "%3A" + request.getServerPort();
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
