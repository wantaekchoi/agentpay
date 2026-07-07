package io.github.wantaekchoi.agentpay.identity.web;

import io.github.wantaekchoi.agentpay.identity.AgentCard;
import io.github.wantaekchoi.agentpay.identity.AgentRegistrationService;
import io.github.wantaekchoi.agentpay.identity.domain.Agent;
import io.github.wantaekchoi.agentpay.identity.port.AgentDirectory;
import io.github.wantaekchoi.agentpay.identity.port.AgentIdentity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/agents")
public class AgentController {
    private final AgentRegistrationService registration;
    private final AgentIdentity agentIdentity;
    private final AgentDirectory directory;

    public AgentController(AgentRegistrationService registration,
                           AgentIdentity agentIdentity, AgentDirectory directory) {
        this.registration = registration;
        this.agentIdentity = agentIdentity;
        this.directory = directory;
    }

    public record RegisterRequest(
            @NotNull UUID ownerUserId,
            @NotBlank
            @Pattern(regexp = "^0x[0-9a-fA-F]{128}$",
                    message = "publicKey must be 0x + 128 hex chars (64-byte secp256k1 key)")
            String publicKey,
            @NotBlank String alias) {}
    public record RegisterResponse(UUID id, String did, String address) {}
    public record VerifyRequest(String message, String signature) {}

    @PostMapping
    public ResponseEntity<RegisterResponse> register(@Valid @RequestBody RegisterRequest req) {
        Agent a = registration.register(req.ownerUserId(), req.publicKey(), req.alias());
        return ResponseEntity.status(201)
                .body(new RegisterResponse(a.getId(), a.getDid(), a.getAddress()));
    }

    @GetMapping("/{id}/card")
    public AgentCard card(@PathVariable UUID id) {
        return directory.cardFor(id);
    }

    @PostMapping("/{id}/verify")
    public Map<String, Boolean> verify(@PathVariable UUID id, @RequestBody VerifyRequest req) {
        return Map.of("valid",
                agentIdentity.verifyChallenge(id, req.message(), req.signature()));
    }
}
