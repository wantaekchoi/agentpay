# CURRENT_STATE

> 완료·검증된 상태만 기록. 진행 예정은 `NEXT_STEP.md`.

## 확정된 설계
- 설계 스펙: `docs/superpowers/specs/2026-07-07-agent-payment-mvp-design.md`
- Phase 1 계획: `docs/superpowers/plans/2026-07-07-phase1-foundation-identity.md`
- 성격: 제품 씨앗 · 표준 쇼케이스 · walking skeleton = 결제·위임 코어

## 스택 (확정)
- Java 25 (LTS) + Spring Boot 4.1.0 + Gradle 9.6.1 (Kotlin DSL, 컴파일·데몬 모두 25)
- 아키텍처: 헥사고날 코어 모놀리스 + ArchUnit 경계 (Modulith 미채택), commerce-mock·evm-gateway 분리
- 온체인: 하이브리드 커스터디 (PaymentRail 뒤), web3j
- 재사용: 결재대행 공개 API(무버전 URL) + `:agentpay-client` SDK + 웹훅 (Phase 6/7)

## 완료
- 저장소 초기화, remote `github.com/wantaekchoi/agentpay`
- **Task 1 (부트스트랩)**: `:app` 멀티모듈 스캐폴드, Boot 4.1/Java25, 스모크 테스트 green (pristine)
