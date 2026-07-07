package io.github.wantaekchoi.agentpay.delegation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.wantaekchoi.agentpay.TestcontainersConfiguration;
import io.github.wantaekchoi.agentpay.delegation.domain.Mandate;
import io.github.wantaekchoi.agentpay.delegation.domain.MandateRepository;
import io.github.wantaekchoi.agentpay.delegation.domain.MandateStatus;
import io.github.wantaekchoi.agentpay.delegation.port.IssueMandateCommand;
import io.github.wantaekchoi.agentpay.identity.domain.Agent;
import io.github.wantaekchoi.agentpay.identity.domain.AgentRepository;
import io.github.wantaekchoi.agentpay.identity.domain.User;
import io.github.wantaekchoi.agentpay.identity.domain.UserRepository;
import io.github.wantaekchoi.agentpay.shared.crypto.Eip712Mandate;
import io.github.wantaekchoi.agentpay.shared.crypto.Signatures;
import io.github.wantaekchoi.agentpay.shared.error.NotFoundException;
import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class Ap2MandateServiceTest {

    private static final long CHAIN_ID = 31337L;
    private static final String PAYEE = "0x1111111111111111111111111111111111111111";

    @Autowired UserRepository users;
    @Autowired AgentRepository agents;
    @Autowired MandateRepository mandates;
    @Autowired Ap2MandateService service;

    private User registerUser(Signatures.KeyPair kp) {
        return users.save(new User(UUID.randomUUID(), "alice", "0xpub", kp.address()));
    }

    private Agent registerAgent(User owner, Signatures.KeyPair kp) {
        return agents.save(new Agent(UUID.randomUUID(), owner.getId(), "0xagentpub",
                kp.address(), "did:key:zTest", "shopper", "ACTIVE"));
    }

    private Eip712Mandate.MandateData dataFor(String userAddress, String agentAddress, BigInteger nonce) {
        return new Eip712Mandate.MandateData(userAddress, agentAddress, "USDC",
                BigInteger.valueOf(1_000), BigInteger.valueOf(10_000),
                List.of(PAYEE), false,
                1_000L, 2_000L, nonce);
    }

    private IssueMandateCommand commandFor(UUID userId, UUID agentId, Eip712Mandate.MandateData data, String sig) {
        return new IssueMandateCommand(userId, agentId, data.currency(),
                data.perTxLimit(), data.totalLimit(), data.allowedPayees(), data.allowAny(),
                data.validFrom(), data.validUntil(), data.nonce(), sig);
    }

    @Test
    void issue_withValidSignature_persistsActiveMandate() {
        var userKp = Signatures.generateKeyPair();
        var agentKp = Signatures.generateKeyPair();
        User user = registerUser(userKp);
        Agent agent = registerAgent(user, agentKp);

        var data = dataFor(user.getAddress(), agent.getAddress(), BigInteger.valueOf(1));
        String sig = Eip712Mandate.sign(data, CHAIN_ID, userKp.privateKey());
        IssueMandateCommand cmd = commandFor(user.getId(), agent.getId(), data, sig);

        Mandate mandate = service.issue(cmd);

        assertThat(mandate.getId()).isNotNull();
        assertThat(mandate.getStatus()).isEqualTo(MandateStatus.ACTIVE);
        assertThat(mandate.getSpent()).isEqualByComparingTo(BigInteger.ZERO);
        assertThat(mandate.getUserId()).isEqualTo(user.getId());
        assertThat(mandate.getAgentId()).isEqualTo(agent.getId());
        assertThat(mandate.getAllowedPayees()).containsExactly(PAYEE);
    }

    @Test
    void issue_withSignatureFromDifferentKey_throwsIllegalArgument() {
        var userKp = Signatures.generateKeyPair();
        var agentKp = Signatures.generateKeyPair();
        var otherKp = Signatures.generateKeyPair();
        User user = registerUser(userKp);
        Agent agent = registerAgent(user, agentKp);

        var data = dataFor(user.getAddress(), agent.getAddress(), BigInteger.valueOf(2));
        String sig = Eip712Mandate.sign(data, CHAIN_ID, otherKp.privateKey());
        IssueMandateCommand cmd = commandFor(user.getId(), agent.getId(), data, sig);

        assertThatThrownBy(() -> service.issue(cmd)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void issue_withTamperedEconomicTerms_afterValidSignature_isRejected() {
        // 가장 안전-critical한 속성: 서명은 사용자가 서명한 "그 정확한" 경제적 조건에 대해서만
        // 유효하다. 여기서는 합법적인 MandateData(perTxLimit=1000)에 대해 정상 서명을 받은 뒤,
        // 그 서명은 그대로 재사용하면서 커맨드의 perTxLimit만 변조(999_999_999)해 제출한다.
        // currency/payee 형식은 그대로 유효하므로 형식 검증은 통과하고, recoverSigner가 변조된
        // 데이터로 다시 해시를 계산해 서명 불일치를 잡아내야 한다.
        var userKp = Signatures.generateKeyPair();
        var agentKp = Signatures.generateKeyPair();
        User user = registerUser(userKp);
        Agent agent = registerAgent(user, agentKp);

        var nonce = BigInteger.valueOf(9);
        var data = dataFor(user.getAddress(), agent.getAddress(), nonce);
        String sig = Eip712Mandate.sign(data, CHAIN_ID, userKp.privateKey());

        IssueMandateCommand tampered = new IssueMandateCommand(user.getId(), agent.getId(), data.currency(),
                BigInteger.valueOf(999_999_999L), data.totalLimit(), data.allowedPayees(), data.allowAny(),
                data.validFrom(), data.validUntil(), data.nonce(), sig);

        assertThatThrownBy(() -> service.issue(tampered)).isInstanceOf(IllegalArgumentException.class);
        assertThat(mandates.existsByUserIdAndNonce(user.getId(), nonce)).isFalse();
    }

    @Test
    void issue_withDuplicateNonce_throwsDataIntegrityViolation() {
        var userKp = Signatures.generateKeyPair();
        var agentKp = Signatures.generateKeyPair();
        User user = registerUser(userKp);
        Agent agent = registerAgent(user, agentKp);

        var data = dataFor(user.getAddress(), agent.getAddress(), BigInteger.valueOf(3));
        String sig = Eip712Mandate.sign(data, CHAIN_ID, userKp.privateKey());
        IssueMandateCommand cmd = commandFor(user.getId(), agent.getId(), data, sig);
        service.issue(cmd);

        assertThatThrownBy(() -> service.issue(cmd)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void issue_withUnknownUserId_throwsNotFound() {
        User owner = registerUser(Signatures.generateKeyPair());
        Agent agent = registerAgent(owner, Signatures.generateKeyPair());
        var strangerKp = Signatures.generateKeyPair();

        var data = dataFor(strangerKp.address(), agent.getAddress(), BigInteger.valueOf(4));
        String sig = Eip712Mandate.sign(data, CHAIN_ID, strangerKp.privateKey());
        IssueMandateCommand cmd = commandFor(UUID.randomUUID(), agent.getId(), data, sig);

        assertThatThrownBy(() -> service.issue(cmd)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void issue_withUnknownAgentId_throwsNotFound() {
        var userKp = Signatures.generateKeyPair();
        User user = registerUser(userKp);
        var strangerAgentAddress = Signatures.generateKeyPair().address();

        var data = dataFor(user.getAddress(), strangerAgentAddress, BigInteger.valueOf(5));
        String sig = Eip712Mandate.sign(data, CHAIN_ID, userKp.privateKey());
        IssueMandateCommand cmd = commandFor(user.getId(), UUID.randomUUID(), data, sig);

        assertThatThrownBy(() -> service.issue(cmd)).isInstanceOf(NotFoundException.class);
    }

    @Test
    void issue_withInvalidCurrency_throwsIllegalArgument() {
        // Eip712Mandate.typedDataJson()은 문자열 필드를 JSON-escape하지 않으므로,
        // 따옴표가 섞인 currency("usdc\"")로 직접 sign()하면 JSON 구조체 자체가 깨진다
        // (그건 이 테스트가 검증하려는 대상이 아니다). 그래서 유효한 데이터로 서명한 뒤,
        // 서비스에 전달하는 커맨드에만 형식이 잘못된 currency를 심어 넣는다 — 서비스는
        // recoverSigner를 시도하기 전에 형식 검증에서 먼저 거부해야 한다.
        var userKp = Signatures.generateKeyPair();
        var agentKp = Signatures.generateKeyPair();
        User user = registerUser(userKp);
        Agent agent = registerAgent(user, agentKp);

        var validData = dataFor(user.getAddress(), agent.getAddress(), BigInteger.valueOf(6));
        String sig = Eip712Mandate.sign(validData, CHAIN_ID, userKp.privateKey());
        IssueMandateCommand cmd = new IssueMandateCommand(user.getId(), agent.getId(), "usdc\"",
                validData.perTxLimit(), validData.totalLimit(), validData.allowedPayees(), validData.allowAny(),
                validData.validFrom(), validData.validUntil(), validData.nonce(), sig);

        assertThatThrownBy(() -> service.issue(cmd)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void issue_withInvalidPayeeFormat_throwsIllegalArgument() {
        var userKp = Signatures.generateKeyPair();
        var agentKp = Signatures.generateKeyPair();
        User user = registerUser(userKp);
        Agent agent = registerAgent(user, agentKp);

        var data = new Eip712Mandate.MandateData(user.getAddress(), agent.getAddress(), "USDC",
                BigInteger.valueOf(1_000), BigInteger.valueOf(10_000),
                List.of("not-an-address"), false, 1_000L, 2_000L, BigInteger.valueOf(7));
        String sig = Eip712Mandate.sign(data, CHAIN_ID, userKp.privateKey());
        IssueMandateCommand cmd = commandFor(user.getId(), agent.getId(), data, sig);

        assertThatThrownBy(() -> service.issue(cmd)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void revoke_setsStatusRevoked() {
        var userKp = Signatures.generateKeyPair();
        var agentKp = Signatures.generateKeyPair();
        User user = registerUser(userKp);
        Agent agent = registerAgent(user, agentKp);

        var data = dataFor(user.getAddress(), agent.getAddress(), BigInteger.valueOf(8));
        String sig = Eip712Mandate.sign(data, CHAIN_ID, userKp.privateKey());
        IssueMandateCommand cmd = commandFor(user.getId(), agent.getId(), data, sig);
        Mandate mandate = service.issue(cmd);

        service.revoke(mandate.getId());

        Mandate revoked = service.get(mandate.getId());
        assertThat(revoked.getStatus()).isEqualTo(MandateStatus.REVOKED);
    }

    @Test
    void revoke_unknownId_throwsNotFound() {
        assertThatThrownBy(() -> service.revoke(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }

    @Test
    void get_unknownId_throwsNotFound() {
        assertThatThrownBy(() -> service.get(UUID.randomUUID())).isInstanceOf(NotFoundException.class);
    }
}
