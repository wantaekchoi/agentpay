# agentpay

> AI 에이전트 결제 플랫폼 MVP — 사용자가 에이전트를 등록하고 예치금·구매 권한을 위임하면, 에이전트가 사용자를 대신해 상품을 탐색·구매·결제한다.

특정 벤더나 표준에 종속되지 않고, 현재 논의되는 **에이전트 신원·위임·결제 표준을 어댑터로 수용**하는 것을 목표로 하는 **씨앗(seed) 코드베이스**다. "확장 가능"을 말로만 두지 않고 — 각 도메인 포트 뒤에 **실제 표준을 구현**하고 후보 표준은 같은 포트의 어댑터로 둔다(표준 쇼케이스).

## 현재 상태

**Phase 1: Foundation & Identity — 완료 ✅**

에이전트를 secp256k1 공개키로 등록하고, 에이전트가 서명한 요청을 검증하며, A2A AgentCard와 did:key/did:web 신원을 노출하는 계층이 동작한다. (21 tests green, ArchUnit 경계 강제.)

이후 위임(AP2) → 예치·커스터디 → 결제 승인 → 정산(x402) → 커머스 탐색(ACP) → 감사·영수증(VC) 순으로 확장한다. → [로드맵](#로드맵)

## 아키텍처

**헥사고날 (Ports & Adapters).** 도메인 코어는 표준·인프라를 모르고 포트(인터페이스)로만 대화한다. 표준 수용 = 어댑터 교체. 모듈 경계는 **ArchUnit 테스트로 강제**(빌드가 위반을 막는다).

### 표준 쇼케이스 매트릭스

각 포트마다 실제 표준을 구현하고, 후보 표준은 같은 포트의 어댑터로 둔다.

| 도메인 / 포트 | 표준 (구현/예정) | 상태 |
|---|---|---|
| identity `AgentIdentity` | **did:key + did:web** (secp256k1) | ✅ Phase 1 |
| discovery `AgentDirectory` | **A2A AgentCard** | ✅ Phase 1 |
| delegation `MandateService` | **AP2 Mandate** (후보: UCAN, VC) | 🔜 Phase 2 |
| payment `PaymentRail` | **x402 / EIP-3009** (후보: 에스크로, 카드) | 🔜 Phase 4–5 |
| commerce `CommerceGateway` | **ACP + schema.org** (후보: UCP) | 🔜 Phase 6 |
| audit `AuditLog` | **VC 영수증** | 🔜 Phase 7 |

> 결재대행(core)의 공개 API는 특정 몰에 종속되지 않는 **재사용 통합**으로 설계한다 — 무버전 리소스 URL + `agentpay-client` SDK + 웹훅. 어떤 커머스든 바로 붙일 수 있다.

## 기술 스택

- **Java 25 (LTS)** · **Spring Boot 4.1** · Gradle 9.6.1 (Kotlin DSL)
- PostgreSQL + Flyway · Testcontainers
- web3j (EVM) · ArchUnit (모듈 경계)

## 시작하기

**사전 요구사항**: JDK 25(LTS), Docker(Testcontainers용), Gradle 불필요(wrapper 포함).

```bash
# 전체 테스트 (단위 + Testcontainers 통합)
./gradlew :app:test

# 특정 테스트
./gradlew :app:test --tests "*SignaturesTest"

# 빌드
./gradlew :app:build

# 앱 실행 (로컬 Postgres 필요 — compose.yml)
docker compose up -d postgres
./gradlew :app:bootRun
```

> Gradle 데몬·컴파일 모두 JDK 25로 고정돼 있어(`gradle/gradle-daemon-jvm.properties`), PATH 기본 JDK가 달라도 동작한다.

## 프로젝트 구조

```
app/                         # core-service (Spring Boot)
  src/main/java/io/github/wantaekchoi/agentpay/
    shared/crypto/           # Signatures(secp256k1), Base58
    shared/did/              # DidKey (did:key)
    identity/domain/         # User, Agent (JPA)
    identity/port/           # AgentIdentity, AgentDirectory (포트)
    identity/                # 등록·검증·디렉터리 서비스
    identity/web/            # REST + did:web 문서
docs/
  superpowers/specs/         # 설계 스펙 (+ ADR)
  superpowers/plans/         # 구현 계획 (Phase별)
  CURRENT_STATE.md           # 완료 상태
  NEXT_STEP.md               # 다음 단계·백로그
CLAUDE.md                    # 빌드·검증·아키텍처·Boot 4.1 gotcha
```

## API (Phase 1)

| 메서드 | 경로 | 설명 |
|---|---|---|
| `POST` | `/agents` | 에이전트 등록 (공개키) → `{id, did, address}` |
| `GET` | `/agents/{id}/card` | A2A AgentCard |
| `POST` | `/agents/{id}/verify` | 서명된 챌린지 검증 → `{valid}` |
| `GET` | `/agents/{id}/did.json` | did:web DID Document |

URL은 무버전 리소스(`/v1` 프리픽스 없음). breaking change는 헤더 버전(날짜).

## 로드맵

| Phase | 내용 | 상태 |
|---|---|---|
| 1 | Foundation & Identity (등록·서명·did:key/web·AgentCard) | ✅ |
| 2 | 위임 (AP2 Mandate, PolicyEngine) | 🔜 |
| 3 | 커스터디 & 예치 (evm-gateway, anvil, test USDC) | |
| 4 | 결제 승인 (단일 트랜잭션: 신원+정책+잔고) | |
| 5 | 정산 (x402 / EIP-3009) | |
| 6 | 커머스 & 탐색 (schema.org/ACP, 필터) | |
| 7 | 감사 · VC 영수증 · E2E | |

## 문서

- [설계 스펙](docs/superpowers/specs/2026-07-07-agent-payment-mvp-design.md)
- [Phase 1 구현 계획](docs/superpowers/plans/2026-07-07-phase1-foundation-identity.md)
- [현재 상태](docs/CURRENT_STATE.md) · [다음 단계](docs/NEXT_STEP.md)
