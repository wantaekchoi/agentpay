# NEXT_STEP

> 다음 3~5개. 완료되면 `CURRENT_STATE.md`로 이동.

## ⏸ 일시 중단 지점 (2026-07-08) — Guardrail G1 진행 중

**중단 당시 상태** (branch `guardrail-g1`, origin push 완료):
- 설계 스펙 커밋됨: `docs/superpowers/specs/2026-07-08-guardrail-component-design.md`
- 구현 계획(8 tasks) 커밋됨: `docs/superpowers/plans/2026-07-08-guardrail-g1.md`
- **T1 코드 구현·커밋됨(`03ae8ac`) — 단 태스크 리뷰 미실시.** `:guardrail-core` 신규 Gradle 모듈(순수 Java) + model/port + `RegexInputGuardrail`(4/4 tests green)
- 사용자 결정 대기: 스펙·플랜 검토 후 **(A) 검토 후 계속 / (B) 그대로 계속 실행 / (C) T1 되돌리고 플랜 단계로**

**재개 절차:**
1. `git checkout guardrail-g1` 후 위 스펙·플랜 2개 문서 검토 (방향: 재사용 우선 — Java 오케스트레이션 + 기존 OPA/Presidio/Ollama를 포트 뒤 어댑터로, 폴백=무-인프라 라이브러리 모드, sLLM은 비동기/섀도우로 무지연)
2. A/B 선택 시: **T1 리뷰부터** (`superpowers:subagent-driven-development`, 원장 `.superpowers/sdd/progress.md` — T1 구현자 concern 3건 기록됨) → T2~T8 순차 실행 → whole-branch 리뷰 → main 머지
3. C 선택 시: `git revert 03ae8ac` 후 플랜 논의부터
4. 태스크별 브리프 추출: `~/.claude/plugins/cache/claude-plugins-official/superpowers/6.1.1/skills/subagent-driven-development/scripts/task-brief <plan파일> <N>`

**G1 남은 태스크 (플랜 문서 기준):**
- T2 Java규칙 정책 + `Guardrail` 오케스트레이터(동기 admission) → T3 async `SemanticAnalyzer`+audit(무지연 배선) → T4 `:app` 통합(V4 마이그레이션·REST·데모 에이전트-액션) → T5 OPA 어댑터(리서치 rego 재사용) → T6 Presidio 어댑터 → T7 Ollama 어댑터 → T8 compose+ArchUnit+프로파일+문서

## 중단 전 백로그 (변동 없음)
- 읽기/멀티테넌트 authz(인증 모델 필요), PolicyEngine `amount > 0`(Phase 4에서), revoke 서명 nonce/expiry, EIP-712 `verifyingContract`.

## 보류된 로드맵
- **Phase 3 커스터디 & 예치**(가드레일 G1에 밀려 보류): evm-gateway 위치는 "코어 내 EVM 어댑터(ChainGateway 포트)"로 결정까지 완료, 이후 설계 질문부터 재개.
- Phase 4 결제 승인 → Phase 5 정산(x402) → Phase 6 커머스(ACP) → Phase 7 감사·E2E.
- Guardrail G2(정산 게이팅·정책 피드백) → G3(동적정책 UI) → G4(MCP/A2A/훅 어댑터) → G5(후보 어댑터 확장).
