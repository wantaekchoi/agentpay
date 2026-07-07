package io.github.wantaekchoi.agentpay.identity.web;

import io.github.wantaekchoi.agentpay.identity.UserRegistrationService;
import io.github.wantaekchoi.agentpay.identity.domain.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/users")
public class UserController {
    private final UserRegistrationService registration;

    public UserController(UserRegistrationService registration) {
        this.registration = registration;
    }

    public record RegisterUserRequest(
            @NotBlank
            @Pattern(regexp = "^0x[0-9a-fA-F]{128}$",
                    message = "publicKey must be 0x + 128 hex chars (64-byte secp256k1 key)")
            String publicKey,
            @NotBlank String alias) {}
    public record RegisterUserResponse(UUID id, String address) {}

    @PostMapping
    public ResponseEntity<RegisterUserResponse> register(@Valid @RequestBody RegisterUserRequest req) {
        User u = registration.register(req.publicKey(), req.alias());
        return ResponseEntity.status(201)
                .body(new RegisterUserResponse(u.getId(), u.getAddress()));
    }
}
