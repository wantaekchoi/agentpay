package io.github.wantaekchoi.agentpay.delegation.adapter;

import io.github.wantaekchoi.agentpay.delegation.domain.Mandate;
import io.github.wantaekchoi.agentpay.delegation.port.IssueMandateCommand;
import io.github.wantaekchoi.agentpay.delegation.port.MandateService;
import java.util.List;
import java.util.UUID;

// UCAN 기반 위임 발급을 위한 자리표시자 어댑터. MandateService 포트가 교체 가능함을 증명하는
// 목적만 가지며, 의도적으로 @Service/@Component로 등록하지 않는다 — 기본 빈은 Ap2MandateService여야 한다.
public class UcanMandateAdapter implements MandateService {

    @Override
    public Mandate issue(IssueMandateCommand cmd) {
        throw new UnsupportedOperationException("UCAN adapter not implemented");
    }

    @Override
    public Mandate get(UUID id) {
        throw new UnsupportedOperationException("UCAN adapter not implemented");
    }

    @Override
    public List<Mandate> listByAgent(UUID agentId) {
        throw new UnsupportedOperationException("UCAN adapter not implemented");
    }

    @Override
    public List<Mandate> listByUser(UUID userId) {
        throw new UnsupportedOperationException("UCAN adapter not implemented");
    }

    @Override
    public void revoke(UUID id, String userSignature) {
        throw new UnsupportedOperationException("UCAN adapter not implemented");
    }
}
