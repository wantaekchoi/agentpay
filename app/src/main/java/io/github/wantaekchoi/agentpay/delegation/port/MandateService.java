package io.github.wantaekchoi.agentpay.delegation.port;

import io.github.wantaekchoi.agentpay.delegation.domain.Mandate;
import java.util.UUID;

public interface MandateService {
    Mandate issue(IssueMandateCommand cmd);

    Mandate get(UUID id);

    void revoke(UUID id);
}
