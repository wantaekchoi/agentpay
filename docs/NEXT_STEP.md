# NEXT_STEP

> 다음 3~5개. 완료되면 `CURRENT_STATE.md`로 이동.

## 진행 중
- **Phase 3 커스터디 & 예치** 시작 (design brainstorming부터).

## 남은 defer 백로그 (Phase 3~4에서 자연스럽게 처리)
- **읽기/멀티테넌트 authz** — mandate 조회 등은 아직 누구나 가능. 인증 모델(세션/토큰) 설계가 필요 → 정식 auth 도입 시. (파괴적 revoke는 서명 인가 완료)
- **PolicyEngine `amount > 0` 가드** — Phase 4 결제 실행이 caller를 붙일 때.
- **revoke 서명 nonce/expiry** — 현재 mandateId 바인딩·idempotent라 저위험. 상위 스테이크로 재사용 전 nonce 추가.
- **EIP-712 `verifyingContract`/배포별 도메인** — cross-app replay 방지(오프체인 mandate엔 선택).

## Phase 3+ (전체 스펙 19장 순서)
- **Phase 3 커스터디 & 예치**: `evm-gateway`(web3j 격리) + anvil + mock USDC(EIP-3009), 예치 감지 → DB 잔고
- Phase 4 결제 승인(신원+PolicyEngine+잔고 → 차감·spent 증가) → Phase 5 정산(x402) → Phase 6 커머스·탐색(ACP+`:agentpay-client`) → Phase 7 감사·VC영수증·E2E

## 완료된 백로그 (Phase 2)
- 설정 externalization(datasource env·chain-id·절대 URL), issue sanity, PolicyEngine not-yet-valid·User dup-409 테스트, **EIP-712 서명 인가 revoke**.
