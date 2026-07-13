# NEXT_STEP

> 다음 3~5개. 완료되면 `CURRENT_STATE.md`로 이동.

## 🗺 현재 단계 (2026-07-13) — G1 코어 main 머지 완료, R1 잔여 작업 + 로드맵 관계자 논의 대기

Guardrail G1 코어(T1~T4)는 구현·태스크 리뷰·최종 리뷰·**main 머지(1f3d100)까지 완료**됐다(머지 후 main에서 114 tests green 재확인). 제품 로드맵 자료(`docs/superpowers/specs/2026-07-10-agentpay-roadmap.md` + 동명 `.html`)와 조립형 판매 피치(`2026-07-13-assembly-sales-pitch.html`, 피드백 반영 중)·데모 절차(`docs/guardrail-demo.md`)도 작성 완료. **관계자 논의·표결 후 R2 착수**가 합의된 진행 방식.

1. **R1 잔여** — 기존 경로(등록·mandate 발급) admission 소급 배선(최종리뷰 권고: `GuardrailInspectRequest` 웹 DTO 패턴 재사용) + **회사 VC 스택 secp256k1/EIP-712 호환 스파이크**(아래 표결 1의 입력이자 월렛 온보딩 경로의 결정 관문)
2. **조립형 판매 모델 확정** — 피치 v1 피드백 → 스펙 문서화 → 로드맵 md/html 정본 반영(표결 +1: 조립형 납품 모델 채택)
3. **관계자 논의** — 로드맵 자료 §8 표결 5건(+조립형 1건) 결정 (핵심: 표결 1 = R2를 credential 먼저 vs custody·결제 먼저). 자료: 로드맵 html + 피치 html + 라이브 데모(`docs/guardrail-demo.md`)
4. **표결 결과에 따라 R2-A(credential 코어) 또는 R2-B(custody·결제)** — 상세는 로드맵 문서 §6. **T5(OPA) 착수 전 필수 2건**: 감사 컬럼(injection/piiMasked)의 문자열 결합을 포트 계약 명시 신호로 승격 + ArchUnit 가드레일 경계 조기 추가(원장 최종리뷰 권고)

기존 Phase 3~7·G2~G5 번호는 로드맵의 R1~R4로 재편됨 (매핑은 로드맵 문서 §6 참조).

## G1 T5~T8 — R3/R4로 이연

T1~T4(=G1 코어)만 R1 범위. 나머지 태스크는 로드맵 재편(§6)에 따라 뒤로 미뤄졌다:
- **T5** OPA 어댑터(리서치 rego 재사용) · **T6** Presidio 어댑터 → **R3**
- **T7** Ollama 어댑터 · **T8** compose+ArchUnit+프로파일+문서 → **R4**

재개 시: 브랜치 `guardrail-g1`(또는 그 후속) 기반, 플랜 `docs/superpowers/plans/2026-07-08-guardrail-g1.md`의 Task 5부터 `superpowers:subagent-driven-development`로 진행. 태스크별 브리프 추출: `~/.claude/plugins/cache/claude-plugins-official/superpowers/6.1.1/skills/subagent-driven-development/scripts/task-brief <plan파일> <N>`.

## 중단 전 백로그 (변동 없음)
- 읽기/멀티테넌트 authz(인증 모델 필요), PolicyEngine `amount > 0`(Phase 4에서), revoke 서명 nonce/expiry, EIP-712 `verifyingContract`.

## 보류된 로드맵 (→ 2026-07-10 로드맵 문서로 재편됨)
- 기존 Phase·G 번호와 신규 릴리즈 매핑: R1=G1 코어(T2~T4)+소급 배선+VC 스파이크 / R2-A=credential 코어(신설)+audit 선행분 / R2-B=Phase 3·4 / R3=남은 쪽+Phase 5+G2+G1 T5·T6 / R4=Phase 6·7+MCP+G1 T7·T8, 이후 G3~G5.
- Phase 3 참고: evm-gateway 위치는 "코어 내 EVM 어댑터(ChainGateway 포트)"로 결정 완료, 이후 설계 질문부터 재개.
