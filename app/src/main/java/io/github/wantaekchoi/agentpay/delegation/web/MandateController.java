package io.github.wantaekchoi.agentpay.delegation.web;

import io.github.wantaekchoi.agentpay.delegation.domain.Mandate;
import io.github.wantaekchoi.agentpay.delegation.domain.MandateRepository;
import io.github.wantaekchoi.agentpay.delegation.domain.MandateStatus;
import io.github.wantaekchoi.agentpay.delegation.port.IssueMandateCommand;
import io.github.wantaekchoi.agentpay.delegation.port.MandateService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
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
    private final MandateRepository mandates;

    public MandateController(MandateService mandateService, MandateRepository mandates) {
        this.mandateService = mandateService;
        this.mandates = mandates;
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
            @NotNull String userSignature) {}

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

    // LAZY allowedPayees: spring.jpa.open-in-view=false 이므로 서비스 호출이 끝나면 영속성
    // 컨텍스트가 닫힌다. 컨트롤러 메서드 자체를 @Transactional(readOnly=true)로 선언해
    // toResponse()의 매핑(allowedPayees 접근)이 트랜잭션이 열려 있는 동안 끝나도록 한다.
    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public MandateResponse getById(@PathVariable UUID id) {
        return toResponse(mandateService.get(id));
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<MandateResponse> list(
            @RequestParam(required = false) UUID agentId,
            @RequestParam(required = false) UUID userId) {
        List<Mandate> found;
        if (agentId != null) {
            found = mandates.findByAgentId(agentId);
        } else if (userId != null) {
            found = mandates.findByUserId(userId);
        } else {
            throw new IllegalArgumentException("agentId 또는 userId 파라미터 중 하나는 필수입니다");
        }
        return found.stream().map(this::toResponse).toList();
    }

    @PostMapping("/{id}/revoke")
    public MandateStatusResponse revoke(@PathVariable UUID id) {
        mandateService.revoke(id);
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
