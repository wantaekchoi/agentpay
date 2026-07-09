# 가드레일 컴포넌트 (agentpay) — 설계 스펙 (G1)

- 날짜: 2026-07-08
- 상태: 설계 확정 (구현 계획 대기)
- 선행: `tf-ai-guardrail-research`(Python PoC — 발표 완료). 이 스펙은 그 오케스트레이터를 **Java로 재구현**하되 엔진은 **기존 도구 재사용**한다.
- 성격: **재사용 우선 · 포트로 대체가능 · 무지연(sLLM 비동기) · 라이브러리+서버 · agentpay 통합 데모**.

## 1. 목적

AI 에이전트 액션에 대한 다계층 시큐리티 가드레일(프롬프트 인젝션·PII·툴/도메인 거버넌스·인간 승인)을 **agentpay의 재사용 가능 컴포넌트**로 넣는다. 에이전트의 결제/액션 **앞단 보안 게이트**(금융 PolicyEngine과 상보적: 보안 가드 → 금융 정책 → 실행). 리서치의 검증된 자산(OPA Rego 정책·Presidio 설정)은 재사용하고, Python `service.py` 오케스트레이터만 Java로 교체한다.

## 2. 확정된 설계 결정

| 결정 | 값 | 근거 |
|---|---|---|
| 구현 형태 | Java 헥사고날 컴포넌트(agentpay 모듈) | PoC를 프로덕션 Java로. 오케스트레이션만 재작성 |
| **재사용 우선** | 포트 실구현 = 기존 도구(OPA+Rego·Presidio·Ollama), 최소 Java 폴백 | "다 구현 말고 대체가능하게 추상화해서 있는 걸 써" |
| 정책 엔진 | `PolicyDecision` 포트: **OPA(리서치 Rego 재사용)=실구현** + Java 규칙=폴백 | 동적 정책·리서치 자산 재사용 |
| sLLM | `SemanticAnalyzer` 포트: **비동기/섀도우**(임계경로 밖), v1=**로컬 Ollama** | "지연 시간 없음"을 문자 그대로 |
| 통합점 | **데모 에이전트-액션 엔드포인트** | 리서치 시나리오와 동형, Phase 4 결제와 자연 연결 |
| 딜리버리 | **라이브러리(JVM 임베드) + 서버(HTTP)** | "레거시에 끼우는 라이브러리/백엔드서버" |
| 데이터 자산 | Postgres append-only inspection 원장 | 감사·정책튜닝·sLLM 학습데이터 |

## 3. 아키텍처 & 파이프라인

**빠른 admission(동기) + 깊은 분석(비동기).**

```
inspect(GuardrailRequest)
  ── 동기(sub-ms, sLLM 0) ────────────────────────────────
   InputGuardrail(regex/Presidio: 인젝션·PII 마스킹·교차컨텍스트 스캔)
     → PolicyDecision(OPA/Java: allow/deny/approval)
     → [ApprovalGate: 고위험이면 approval_required]
     → admission verdict 즉시 반환 (ALLOWED/DENIED/APPROVAL_REQUIRED, sanitized, stages)
  ── 비동기/섀도우 ──────────────────────────────────────
   SemanticAnalyzer(Ollama sLLM) → deep verdict
     → GuardrailAudit(데이터자산) 갱신 + (향후)정산 게이트 + 동적정책 피드백
```

핵심: **admission은 sLLM을 기다리지 않는다.** async verdict는 원장에 사후 기록되고, agentpay의 비가역 단계(정산, Phase 5)를 게이트할 근거가 된다.

## 4. 포트 & 어댑터 (재사용 우선 매트릭스)

| 포트 | v1 실구현 = 기존 것 | 폴백(무-인프라 라이브러리 모드) | 후보 어댑터 |
|---|---|---|---|
| `InputGuardrail` | **Presidio**(리서치 컨테이너 재사용) | thin Java regex(인젝션·전화·secret·rrn·card) | 타 PII 엔진 |
| `PolicyDecision` | **OPA + `guardrail.rego`**(리서치 재사용) | 최소 Java 규칙 | 타 정책엔진 |
| `SemanticAnalyzer` | **Ollama**(로컬 sLLM, async) | 휴리스틱 점수 | 클라우드 LLM, ONNX 분류기 |
| `ApprovalGate` | DB 승인 큐 | 인메모리 | 외부 승인 워크플로 |
| `GuardrailAudit` | Postgres append-only | — | 온체인 앵커링·SIEM |

폴백이 곧 "무거운 서비스(OPA/Presidio/Ollama) 없이도 도는 라이브러리 모드"이자 **대체가능성의 실증**. 어댑터는 `@ConditionalOnProperty`/설정으로 실구현↔폴백 전환.

## 5. 요청/응답 모델 (리서치 `/v1/process`와 동형)

```
GuardrailRequest {
  subjectId (agent/user), action, message,
  referenceContexts[] (각 trust=untrusted 강제),
  proposedTools[], requestedDomains[], metadata
}
GuardrailDecision {
  traceId, status (ALLOWED/DENIED/APPROVAL_REQUIRED),
  reasons[], sanitizedMessage, guardrailActions[],
  stages[] (단계별 StageResult), providers[] (regex/presidio/opa/ollama),
  deepAnalysisPending (bool)   // async sLLM 진행 중
}
```

## 6. 데이터 자산 & 동적 정책

- **inspection 원장**(`guardrail_inspection`, append-only): traceId·subjectId·action·신호(injection/pii/tool/domain flags)·fastVerdict·reasons·sanitized·**sLLM verdict(async 채움)**·providers·outcome·ts. → 감사·정책튜닝·**sLLM 학습셋**으로 자산화.
- **동적 정책**(DB 기반 런타임 설정, per-agent/tenant): allowedTools·blockedToolPatterns·allowedDomainPatterns·highRiskActions·approvalGateEnabled·domainCheckEnabled·piiResponseMode(mask/review/approve)·piiThreshold. 설정 API로 무재배포 튜닝. (OPA Rego는 이 입력을 받아 판정.)

## 7. 딜리버리 (라이브러리 + 서버)

- **`guardrail-core`**(순수 Java 라이브러리 모듈, Spring 무관): `Guardrail.inspect(req) → decision`. 포트 + **폴백 기본 구현**만으로 JVM 레거시에 임베드(외부 서비스 없이 degraded 동작).
- **서버**(agentpay): `POST /guardrail/inspect`, 데모 시나리오 엔드포인트(`GET /guardrail/scenarios`, `POST /guardrail/scenarios/{id}/run` — 리서치 UI와 동형), `GET /guardrail/traces/{id}`. 비-JVM/레거시는 HTTP로 호출.
- **agentpay 통합**: `POST /agent-actions`(데모 에이전트-액션 엔드포인트) — 에이전트가 제안한 액션(message/tools/references)을 받아 가드레일 inspect → ALLOWED면 진행·DENIED 차단·APPROVAL_REQUIRED 보류. Phase 4 결제가 붙으면 정산 앞단을 async verdict가 게이트.

## 8. 무지연(async) 설계

- admission 경로는 결정론적 검사만(regex/OPA, sub-ms) — sLLM 호출 없음.
- `SemanticAnalyzer.analyze()`는 `@Async`/executor로 fire-and-forget, 완료 시 inspection 원장의 sLLM verdict 갱신 + `SemanticVerdictReady` 이벤트.
- 데모에서 "admission 응답은 즉시, sLLM verdict는 잠시 후 원장/UI에 채워짐"으로 무지연 실증.

## 9. 모듈 / 패키지 구조 + ArchUnit

```
:guardrail-core (신규 Gradle 모듈, 순수 Java — 라이브러리)
  guardrail/
    Guardrail.java              # 오케스트레이터(inspect)
    model/{GuardrailRequest,GuardrailDecision,StageResult,...}
    port/{InputGuardrail,PolicyDecision,SemanticAnalyzer,ApprovalGate,GuardrailAudit}
    fallback/{RegexInputGuardrail,JavaRulePolicy,HeuristicAnalyzer}  # 무-인프라 기본
:app (agentpay 서버)
  guardrail/
    adapter/{PresidioInputGuardrail,OpaPolicyDecision,OllamaSemanticAnalyzer}  # 기존 도구 배선
    JpaGuardrailAudit, GuardrailSettings(동적정책)
    web/{GuardrailController, AgentActionController}
  db/migration/V4__guardrail.sql
policies/guardrail.rego         # 리서치에서 가져와 재사용
```

- ArchUnit: `:guardrail-core`는 Spring/web3j/agentpay 의존 금지(순수 라이브러리). 포트는 인터페이스. 어댑터만 외부 도구 참조.

## 10. Walking skeleton (G1) 범위 + 로드맵

### G1 (이번)
- `guardrail-core` 라이브러리(포트 + 폴백: regex 입력가드·Java규칙 정책·휴리스틱) + `Guardrail.inspect` 오케스트레이션(동기 admission).
- **async SemanticAnalyzer 포트 + Ollama 어댑터**(fire-and-record로 무지연 실증) + 폴백 휴리스틱.
- OPA 어댑터(리서치 Rego 재사용) + Presidio 어댑터(재사용) — 설정으로 폴백↔실구현 전환.
- 데이터자산 원장(V4) + `POST /guardrail/inspect` + 데모 에이전트-액션 엔드포인트.
- compose에 OPA·Presidio·Ollama 추가.

### 이후
- **G2**: 정산(Phase 5) 게이팅 + async verdict 기반 정책 피드백 · **G3**: 동적정책 설정 API/데모 UI · **G4**: 어댑터(MCP 툴·A2A·훅·CLI 스크립트) · **G5**: 후보 어댑터 확장(클라우드 LLM·ONNX·SIEM).

## 11. 인프라 (compose)

```
opa               리서치 guardrail.rego 재사용 (0.70.0-static — v0 Rego 문법 유지)
presidio-analyzer / presidio-anonymizer   리서치 설정 재사용
ollama            로컬 sLLM(작은 모델 예: llama3.2:3b) — 또는 호스트 Ollama
postgres          agentpay 기존 DB (guardrail_inspection 추가)
```

## 12. 범위 밖 (로드맵)

- 실제 MCP/A2A/훅/스크립트 어댑터 (G4)
- 정산 게이팅·정책 자동튜닝 (G2)
- 동적정책 UI (G3)
- 프로덕션 인증/멀티테넌시

## 13. 구현 전 검증 필요

- **Ollama**: Java에서의 HTTP API(`/api/generate`·`/api/chat`), 작은 모델 가용성, async 타임아웃. 로컬 Ollama 설치 여부.
- **OPA 재사용**: 리서치 `guardrail.rego`(v0 문법)를 그대로 가져와 OPA 0.70에서 평가, Java `PolicyClient`(HTTP)로 호출.
- **Presidio 재사용**: 리서치 compose/설정, Java에서 analyzer/anonymizer HTTP 호출.
- Gradle 멀티모듈(`:guardrail-core` 추가) — 현재 단일 `:app`에서 확장.

## 14. 주요 설계 결정 (ADR 씨앗)

| 결정 | 근거 | 대안(기각) |
|---|---|---|
| Java 오케스트레이션 + 기존 엔진 재사용 | PoC를 프로덕션화하되 검증된 OPA/Presidio 자산 살림 | 전부 Java 재구현(과대), Python 서비스 유지(폴리글롯 운영부담) |
| sLLM 비동기/섀도우 | "무지연" 문자 그대로, 수락↔비가역 분리 활용 | 인라인 분류기(저지연이지 무지연 아님) |
| 포트+폴백 | 무-인프라 라이브러리 모드 + 대체가능성 실증 | 하드 의존(대체 불가) |

## 15. 다음 단계

1. 이 스펙 검토(사용자)
2. `writing-plans`로 G1 구현 계획 → subagent-driven 실행 → whole-branch 리뷰 → main 머지
3. 착수 시 CURRENT_STATE/NEXT_STEP 갱신
