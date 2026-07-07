package io.github.wantaekchoi.agentpay.identity;

import io.github.wantaekchoi.agentpay.identity.domain.Agent;
import io.github.wantaekchoi.agentpay.identity.domain.AgentRepository;
import io.github.wantaekchoi.agentpay.identity.domain.UserRepository;
import io.github.wantaekchoi.agentpay.shared.crypto.Signatures;
import io.github.wantaekchoi.agentpay.shared.did.DidKey;
import io.github.wantaekchoi.agentpay.shared.error.NotFoundException;
import org.springframework.stereotype.Service;

import java.math.BigInteger;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class AgentRegistrationService {
    private static final Pattern PUBLIC_KEY_HEX_PATTERN = Pattern.compile("[0-9a-fA-F]{128}");

    private final AgentRepository agents;
    private final UserRepository users;

    public AgentRegistrationService(AgentRepository agents, UserRepository users) {
        this.agents = agents;
        this.users = users;
    }

    public Agent register(UUID ownerUserId, String publicKeyHex, String alias) {
        if (!users.existsById(ownerUserId)) {
            throw new NotFoundException("소유자 미존재: " + ownerUserId);
        }
        String normalizedPublicKeyHex = normalizePublicKeyHex(publicKeyHex);
        BigInteger publicKey = Signatures.publicKeyFromHex(normalizedPublicKeyHex);
        String address = Signatures.addressFromPublicKey(publicKey);
        String did = DidKey.encode(publicKey);
        Agent agent = new Agent(UUID.randomUUID(), ownerUserId,
                normalizedPublicKeyHex,
                address, did, alias, "ACTIVE");
        return agents.save(agent);
    }

    // 방어적 정규화/검증: "0x"/"0X" 접두사를 대소문자 구분 없이 제거하고 정확히
    // 128자리(64바이트 secp256k1 공개키) 16진수인지 확인한다. 컨트롤러의 빈 검증을
    // 우회해 서비스가 직접 호출되는 경로(테스트, 내부 호출 등)에서도 잘못된 입력이
    // web3j Signatures까지 흘러가지 않도록 막는다.
    private static String normalizePublicKeyHex(String publicKeyHex) {
        if (publicKeyHex == null) {
            throw new IllegalArgumentException("공개키가 필요합니다.");
        }
        String hex = publicKeyHex.replaceFirst("(?i)^0x", "");
        if (!PUBLIC_KEY_HEX_PATTERN.matcher(hex).matches()) {
            throw new IllegalArgumentException(
                    "공개키는 0x 접두사를 포함해 128자리 16진수(secp256k1, 64바이트)여야 합니다: " + publicKeyHex);
        }
        return "0x" + hex;
    }
}
