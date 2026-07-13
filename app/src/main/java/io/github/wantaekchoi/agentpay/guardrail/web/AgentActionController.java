package io.github.wantaekchoi.agentpay.guardrail.web;

import io.github.wantaekchoi.agentpay.guardrail.Guardrail;
import io.github.wantaekchoi.agentpay.guardrail.model.GuardrailDecision;
import io.github.wantaekchoi.agentpay.guardrail.model.Status;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 데모 에이전트-액션 엔드포인트 — 에이전트가 제안한 액션을 실제로 실행하기 전에 가드레일
 * admission을 먼저 거치는 흐름을 보여준다. 실제 커머스/결제 실행(commerce-mock 연동 등)은
 * 이 태스크 범위 밖이므로 ALLOWED여도 부수효과 없이 데모 문구만 반환한다.
 */
@RestController
public class AgentActionController {

    private final Guardrail guardrail;

    public AgentActionController(Guardrail guardrail) {
        this.guardrail = guardrail;
    }

    public record AgentActionResponse(Status status, String traceId, List<String> reasons, String detail) {}

    @PostMapping("/agent-actions")
    public AgentActionResponse propose(@Valid @RequestBody GuardrailInspectRequest req) {
        GuardrailDecision decision = guardrail.inspect(req.toCoreRequest());
        return switch (decision.status()) {
            case ALLOWED -> new AgentActionResponse(
                    Status.ALLOWED, decision.traceId(), List.of(), "액션 실행됨(데모): " + req.action());
            case DENIED -> new AgentActionResponse(
                    Status.DENIED, decision.traceId(), decision.reasons(), null);
            case APPROVAL_REQUIRED -> new AgentActionResponse(
                    Status.APPROVAL_REQUIRED, decision.traceId(), List.of(), "승인 대기 중");
        };
    }
}
