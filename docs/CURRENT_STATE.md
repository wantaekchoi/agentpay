# CURRENT_STATE

> 완료·검증된 상태만 기록. 진행 예정은 `NEXT_STEP.md`.

## 확정된 설계
- 설계 스펙: `docs/superpowers/specs/2026-07-07-agent-payment-mvp-design.md`
- Phase 1 계획: `docs/superpowers/plans/2026-07-07-phase1-foundation-identity.md`
- 성격: 제품 씨앗 · 표준 쇼케이스 · walking skeleton = 결제·위임 코어

## 스택 (확정)
- Java 25 (LTS) + Spring Boot 4.1.0 + Gradle 9.6.1 (Kotlin DSL, 컴파일·데몬 모두 25)
- 헥사고날 코어 모놀리스 + ArchUnit 경계(Modulith 미채택), commerce-mock·evm-gateway 분리(예정)
- 온체인: 하이브리드 커스터디(PaymentRail 뒤), web3j / 재사용: 결재대행 무버전 API + `:agentpay-client` SDK + 웹훅(예정)

## Phase 1 — 완료 (Foundation & Identity) ✅
브랜치 `phase-1-foundation-identity`, GitHub push 완료. 전 8태스크 + 최종리뷰 수정, 21 tests green.
- **T1 부트스트랩**: `:app`(Boot 4.1/Java25), Gradle wrapper, compose(postgres) 스텁
- **T2 `shared/crypto/Signatures`**: secp256k1 EIP-191 sign/recover, KeyPair.toString redaction, 서명길이 검증
- **T3 `shared/crypto/Base58` + `shared/did/DidKey`**: did:key(secp256k1) 인코딩
- **T4 `identity/domain`**: User/Agent JPA + Flyway V1 + 공유 TestcontainersConfiguration
- **T5 `identity`**: AgentRegistrationService + AgentIdentityService (`AgentIdentity` 포트) — 등록/챌린지검증
- **T6**: A2A `AgentCard` + `AgentDirectory` 포트
- **T7 `identity/web`**: REST(register/verify/card) + did:web 문서. URL 무버전
- **T8 ArchUnit**: 모듈 경계 3규칙(+ 최종리뷰 수정으로 `..identity..`→web3j 금지 강화)
- **최종 리뷰(opus)**: Critical 0. 게이트였던 Important #1(identity→web3j 누수 + vacuous 규칙) 수정 완료(commit e2bf918).

## Phase 1 하드닝 — 완료 ✅ (branch `phase1-hardening` → main)
최종리뷰 defer 항목 처리. **35 tests green.**
- 전역 에러 핸들링 `web/ApiExceptionHandler`: NotFoundException→404, @Valid/IllegalArgument→400, DataIntegrityViolation→409, 무-hex/타입불일치→400. body `{error,message}`
- `RegisterRequest` 검증(@Valid + publicKey `^0x[0-9a-fA-F]{128}$`), register 방어적 정규화, agent public_key 유니크(V2)
- did:web 포트 `%3A` 인코딩, 테스트 갭 보강(리뷰어 지적한 vacuous 테스트 2건을 revert-검증으로 강화)

## Phase 2 — 완료 ✅ (위임 / AP2 Intent Mandate, branch `phase-2-delegation` → main)
설계 `2026-07-08-phase2-delegation-design.md`, 계획 `2026-07-08-phase2-delegation.md`. **72 tests green.** 최종 whole-branch 리뷰(opus) Ready to merge, Critical 0.
- **`shared/crypto/Eip712Mandate`**: EIP-712 서명·복구(sign·recover 동일 typedDataJson) + JSON escape(defense-in-depth)
- **`delegation/domain`**: Mandate JPA + Flyway V3 + mandate_allowed_payees(LAZY, 서비스단 초기화)
- **`delegation` 서비스**: `Ap2MandateService`(EIP-712 검증, 서버측 주소로 struct 재구성 → **tamper 거부 회귀테스트로 잠금**), `DefaultPolicyEngine`(6규칙+경계), `UcanMandateAdapter` stub(포트 교체성)
- **`delegation/web/MandateController`**: POST /mandates·GET·list·revoke (포트 전용, currency/payee 형식검증)
- **`identity` POST /users** (mandate 발급자 등록)
- ArchUnit: `..delegation..`→web3j 금지 non-vacuous 검증

## 검증됨
- 서버측 개인키 부재(DB/로그), recoverAddress 크래시 내성, sign→recover 라운드트립, did:key/base58 정확성(리뷰어 독립검증), 실 Postgres 통합테스트.
- **Phase 2**: mandate 서명이 실제 강제 조건을 바인딩(변조 거부 실증), PolicyEngine 경계 정확, delegation 계층 경계 강제.
