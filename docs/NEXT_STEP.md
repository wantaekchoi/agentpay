# NEXT_STEP

> 다음 3~5개. 완료되면 `CURRENT_STATE.md`로 이동.

## 즉시 처리 (Phase 2 최종리뷰 defer 백로그 — 비긴급)
1. **object-level authz** — mandate 읽기/revoke가 누구나 가능(MVP 문서화 자세). 멀티테넌트/운영 노출 전 소유자 검증(서명 기반 revoke 포함). → Phase 3 이전.
2. **PolicyEngine `amount > 0` 가드** — Phase 4 결제 실행이 caller를 붙일 때 추가(현재 미도달).
3. **테스트 보강** — PolicyEngine `now < validFrom`(not-yet-valid), User dup-address 409, `issue`의 `validFrom ≤ validUntil` sanity.
4. **EIP-712 도메인 강화** — `verifyingContract`/배포별 도메인 필드로 cross-app replay 방지(오프체인 mandate엔 선택).
5. **설정 이관** — `application.yml` datasource 자격증명 → env/secret, `blockchainAccountId` 실제 chain id, `AgentCard.serviceEndpoint` 절대 URL.

## Phase 3+ (전체 스펙 19장 순서)
- **Phase 3 커스터디 & 예치**: `evm-gateway` 서비스(web3j 격리) + anvil + mock USDC(EIP-3009), 예치 감지 → DB 잔고
- Phase 4 결제 승인(단일 트랜잭션: 신원+PolicyEngine+잔고 → 차감·spent 증가) → Phase 5 정산(x402/EIP-3009) → Phase 6 커머스·탐색(schema.org/ACP + `:agentpay-client`) → Phase 7 감사·VC영수증·E2E
- 각 Phase: 자체 design+plan → subagent-driven 실행 → whole-branch 리뷰 → main 머지.
