# NEXT_STEP

> 다음 3~5개. 완료되면 `CURRENT_STATE.md`로 이동.

## 즉시 처리
Phase 1 하드닝 완료(main): 404/400/409 에러 핸들링 · RegisterRequest 검증 · pubkey 유니크 · did:web `%3A` · 테스트 갭 보강. 남은 정리(비긴급):
1. **설정 이관**: `application.yml` datasource 자격증명 → env/secret. `DidWebController`의 `blockchainAccountId`를 실제 chain id로, `AgentCard.serviceEndpoint`를 절대 URL로. (배포 전)

## Phase 2+ (스펙 19장 순서)
- **Phase 2 위임(AP2 Mandate)** → Phase 3 커스터디·예치(evm-gateway+anvil+mock USDC) → Phase 4 결제 승인(단일 트랜잭션) → Phase 5 정산(x402/EIP-3009) → Phase 6 커머스·탐색(schema.org/ACP + `:agentpay-client`) → Phase 7 감사·VC영수증·E2E
- 각 Phase는 자체 계획 문서(`docs/superpowers/plans/`) → subagent-driven 실행.
