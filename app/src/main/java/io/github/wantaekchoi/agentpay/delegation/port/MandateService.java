package io.github.wantaekchoi.agentpay.delegation.port;

import io.github.wantaekchoi.agentpay.delegation.domain.Mandate;
import java.util.List;
import java.util.UUID;

public interface MandateService {
    Mandate issue(IssueMandateCommand cmd);

    Mandate get(UUID id);

    List<Mandate> listByAgent(UUID agentId);

    List<Mandate> listByUser(UUID userId);

    void revoke(UUID id);
}
