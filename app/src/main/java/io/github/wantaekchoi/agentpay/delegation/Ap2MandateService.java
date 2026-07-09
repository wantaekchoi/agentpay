package io.github.wantaekchoi.agentpay.delegation;

import io.github.wantaekchoi.agentpay.delegation.domain.Mandate;
import io.github.wantaekchoi.agentpay.delegation.domain.MandateRepository;
import io.github.wantaekchoi.agentpay.delegation.domain.MandateStatus;
import io.github.wantaekchoi.agentpay.delegation.port.IssueMandateCommand;
import io.github.wantaekchoi.agentpay.delegation.port.MandateService;
import io.github.wantaekchoi.agentpay.identity.domain.Agent;
import io.github.wantaekchoi.agentpay.identity.domain.AgentRepository;
import io.github.wantaekchoi.agentpay.identity.domain.User;
import io.github.wantaekchoi.agentpay.identity.domain.UserRepository;
import io.github.wantaekchoi.agentpay.shared.crypto.Eip712Mandate;
import io.github.wantaekchoi.agentpay.shared.error.NotFoundException;
import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.hibernate.Hibernate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class Ap2MandateService implements MandateService {

    // 알려진 위험 완화: Eip712Mandate.typedDataJson은 문자열 필드를 JSON-escape하지 않으므로
    // 외부 입력(currency/allowedPayees)은 서명 구조체를 구성하기 전에 형식을 엄격히 검증한다.
    private static final Pattern CURRENCY_PATTERN = Pattern.compile("^[A-Z0-9]{1,10}$");
    private static final Pattern PAYEE_PATTERN = Pattern.compile("^0x[0-9a-fA-F]{40}$");

    private final UserRepository users;
    private final AgentRepository agents;
    private final MandateRepository mandates;

    @Value("${agentpay.chain-id:31337}")
    private long chainId;

    public Ap2MandateService(UserRepository users, AgentRepository agents, MandateRepository mandates) {
        this.users = users;
        this.agents = agents;
        this.mandates = mandates;
    }

    @Override
    @Transactional
    public Mandate issue(IssueMandateCommand cmd) {
        User user = users.findById(cmd.userId())
                .orElseThrow(() -> new NotFoundException("사용자 미존재: " + cmd.userId()));
        Agent agent = agents.findById(cmd.agentId())
                .orElseThrow(() -> new NotFoundException("에이전트 미존재: " + cmd.agentId()));

        if (cmd.validFrom() > cmd.validUntil()) {
            throw new IllegalArgumentException(
                    "validFrom은 validUntil보다 클 수 없습니다: " + cmd.validFrom() + " > " + cmd.validUntil());
        }
        if (cmd.perTxLimit() == null || cmd.perTxLimit().signum() <= 0) {
            throw new IllegalArgumentException("perTxLimit은 0보다 커야 합니다: " + cmd.perTxLimit());
        }
        if (cmd.totalLimit() == null || cmd.totalLimit().signum() <= 0) {
            throw new IllegalArgumentException("totalLimit은 0보다 커야 합니다: " + cmd.totalLimit());
        }

        if (!CURRENCY_PATTERN.matcher(cmd.currency()).matches()) {
            throw new IllegalArgumentException("통화 형식이 올바르지 않습니다: " + cmd.currency());
        }
        List<String> allowedPayees = cmd.allowedPayees() == null ? List.of() : cmd.allowedPayees();
        for (String payee : allowedPayees) {
            if (!PAYEE_PATTERN.matcher(payee).matches()) {
                throw new IllegalArgumentException("payee 형식이 올바르지 않습니다: " + payee);
            }
        }

        if (mandates.existsByUserIdAndNonce(cmd.userId(), cmd.nonce())) {
            throw new DataIntegrityViolationException("duplicate mandate nonce");
        }

        Eip712Mandate.MandateData data = new Eip712Mandate.MandateData(
                user.getAddress(), agent.getAddress(), cmd.currency(),
                cmd.perTxLimit(), cmd.totalLimit(), allowedPayees, cmd.allowAny(),
                cmd.validFrom(), cmd.validUntil(), cmd.nonce());

        String recovered = Eip712Mandate.recoverSigner(data, chainId, cmd.userSignature());
        if (!recovered.equalsIgnoreCase(user.getAddress())) {
            throw new IllegalArgumentException("서명 불일치");
        }

        Set<String> allowedPayeeSet = new HashSet<>(allowedPayees);
        Mandate mandate = new Mandate(UUID.randomUUID(), cmd.userId(), cmd.agentId(), cmd.currency(),
                cmd.perTxLimit(), cmd.totalLimit(), BigInteger.ZERO,
                cmd.allowAny(), allowedPayeeSet,
                cmd.validFrom(), cmd.validUntil(), cmd.nonce(),
                cmd.userSignature(), MandateStatus.ACTIVE);
        return mandates.save(mandate);
    }

    @Override
    @Transactional(readOnly = true)
    public Mandate get(UUID id) {
        Mandate mandate = mandates.findById(id)
                .orElseThrow(() -> new NotFoundException("mandate 미존재: " + id));
        Hibernate.initialize(mandate.getAllowedPayees());
        return mandate;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Mandate> listByAgent(UUID agentId) {
        List<Mandate> found = mandates.findByAgentId(agentId);
        found.forEach(m -> Hibernate.initialize(m.getAllowedPayees()));
        return found;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Mandate> listByUser(UUID userId) {
        List<Mandate> found = mandates.findByUserId(userId);
        found.forEach(m -> Hibernate.initialize(m.getAllowedPayees()));
        return found;
    }

    @Override
    @Transactional
    public void revoke(UUID id, String userSignature) {
        Mandate mandate = mandates.findById(id)
                .orElseThrow(() -> new NotFoundException("mandate 미존재: " + id));
        User user = users.findById(mandate.getUserId())
                .orElseThrow(() -> new NotFoundException("사용자 미존재: " + mandate.getUserId()));

        Eip712Mandate.RevocationData data = new Eip712Mandate.RevocationData(user.getAddress(), id.toString());
        String recovered = Eip712Mandate.recoverRevocationSigner(data, chainId, userSignature);
        if (!recovered.equalsIgnoreCase(user.getAddress())) {
            throw new IllegalArgumentException("revoke 서명 불일치");
        }

        mandate.revoke();
        mandates.save(mandate);
    }
}
