package io.github.wantaekchoi.agentpay.delegation.web;

import io.github.wantaekchoi.agentpay.delegation.domain.Mandate;
import io.github.wantaekchoi.agentpay.delegation.domain.MandateStatus;
import io.github.wantaekchoi.agentpay.delegation.port.IssueMandateCommand;
import io.github.wantaekchoi.agentpay.delegation.port.MandateService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/mandates")
public class MandateController {

    private final MandateService mandateService;

    public MandateController(MandateService mandateService) {
        this.mandateService = mandateService;
    }

    public record IssueMandateRequest(
            @NotNull UUID userId,
            @NotNull UUID agentId,
            @NotNull
            @Pattern(regexp = "^[A-Z0-9]{1,10}$", message = "currency must be 1-10 uppercase alphanumeric chars")
            String currency,
            @NotNull BigInteger perTxLimit,
            @NotNull BigInteger totalLimit,
            List<@Pattern(regexp = "^0x[0-9a-fA-F]{40}$",
                    message = "payee must be 0x + 40 hex chars") String> allowedPayees,
            boolean allowAny,
            long validFrom,
            long validUntil,
            @NotNull BigInteger nonce,
            @NotBlank String userSignature) {}

    public record MandateResponse(
            UUID id,
            UUID userId,
            UUID agentId,
            String currency,
            BigInteger perTxLimit,
            BigInteger totalLimit,
            BigInteger spent,
            boolean allowAny,
            List<String> allowedPayees,
            long validFrom,
            long validUntil,
            MandateStatus status) {}

    public record MandateStatusResponse(UUID id, MandateStatus status) {}

    public record RevokeRequest(@NotBlank String userSignature) {}

    @PostMapping
    public ResponseEntity<MandateStatusResponse> issue(@Valid @RequestBody IssueMandateRequest req) {
        IssueMandateCommand cmd = new IssueMandateCommand(
                req.userId(), req.agentId(), req.currency(),
                req.perTxLimit(), req.totalLimit(), req.allowedPayees(), req.allowAny(),
                req.validFrom(), req.validUntil(), req.nonce(), req.userSignature());
        Mandate mandate = mandateService.issue(cmd);
        return ResponseEntity.status(201)
                .body(new MandateStatusResponse(mandate.getId(), mandate.getStatus()));
    }

    // LAZY allowedPayees: MandateService가 자신의 @Transactional 경계 안에서
    // Hibernate.initialize(allowedPayees)로 미리 초기화해 반환하므로, 트랜잭션이
    // 닫힌 뒤에도(spring.jpa.open-in-view=false) 컨트롤러가 안전하게 매핑할 수 있다.
    @GetMapping("/{id}")
    public MandateResponse getById(@PathVariable UUID id) {
        return toResponse(mandateService.get(id));
    }

    @GetMapping
    public List<MandateResponse> list(
            @RequestParam(required = false) UUID agentId,
            @RequestParam(required = false) UUID userId) {
        List<Mandate> found;
        // agentId와 userId가 둘 다 주어지면 agentId가 우선한다.
        if (agentId != null) {
            found = mandateService.listByAgent(agentId);
        } else if (userId != null) {
            found = mandateService.listByUser(userId);
        } else {
            throw new IllegalArgumentException("agentId 또는 userId 파라미터 중 하나는 필수입니다");
        }
        return found.stream().map(this::toResponse).toList();
    }

    @PostMapping("/{id}/revoke")
    public MandateStatusResponse revoke(@PathVariable UUID id, @Valid @RequestBody RevokeRequest req) {
        mandateService.revoke(id, req.userSignature());
        return new MandateStatusResponse(id, MandateStatus.REVOKED);
    }

    private MandateResponse toResponse(Mandate m) {
        return new MandateResponse(
                m.getId(), m.getUserId(), m.getAgentId(), m.getCurrency(),
                m.getPerTxLimit(), m.getTotalLimit(), m.getSpent(),
                m.isAllowAnyPayee(), new ArrayList<>(m.getAllowedPayees()),
                m.getValidFrom(), m.getValidUntil(), m.getStatus());
    }
}
