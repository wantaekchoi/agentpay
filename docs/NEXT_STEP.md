# NEXT_STEP

> 다음 3~5개. 완료되면 `CURRENT_STATE.md`로 이동.

## 즉시 처리 (Phase 1 마무리 백로그 — 최종리뷰 defer 항목)
1. **404 핸들링**: unknown agentId가 `/card`·`/did.json`에서 500 → `@RestControllerAdvice`로 404 매핑(재사용 공개 API 품질). `RegisterRequest`에 `@Valid`도 함께.
2. **did:web `%3A`**: `DidWebController`의 포트 구분자 `:` → `%3A` 퍼센트 인코딩(did:web 스펙 준수), 테스트도 인코딩 형태로 강화.
3. **입력 하드닝**: `AgentRegistrationService` publicKeyHex 형식/길이 검증(shared 헬퍼로), `0x` strip 대소문자, agent pubkey 유니크.
4. **테스트 갭**: `GET /agents/{id}/card` HTTP 테스트, not-found 분기(cardFor/verify) 테스트, recoverAddress odd-length hex.
5. **설정**: `application.yml` datasource 자격증명 env/secret 이관, `blockchainAccountId` 실제 chain id, `AgentCard.serviceEndpoint` 절대 URL.

## Phase 2+ (스펙 19장 순서)
- **Phase 2 위임(AP2 Mandate)** → Phase 3 커스터디·예치(evm-gateway+anvil+mock USDC) → Phase 4 결제 승인(단일 트랜잭션) → Phase 5 정산(x402/EIP-3009) → Phase 6 커머스·탐색(schema.org/ACP + `:agentpay-client`) → Phase 7 감사·VC영수증·E2E
- 각 Phase는 자체 계획 문서(`docs/superpowers/plans/`) → subagent-driven 실행.
