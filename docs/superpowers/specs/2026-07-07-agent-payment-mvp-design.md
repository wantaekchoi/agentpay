# AI 에이전트 결제 플랫폼 MVP — 설계 스펙

- 날짜: 2026-07-07
- 상태: 설계 확정 (구현 계획 대기)
- 저장소: `tf-agent-commercial`
- 문서 유형: brainstorming 산출 설계 스펙 (→ 다음 단계: writing-plans)

---

## 1. 목적 & 성격

사용자가 AI 에이전트를 등록하고 **구매 권한(위임)과 예치금을 맡기면, 에이전트가 사용자를 대신해 상품을 조건 탐색하고 결제**하는 흐름을 검증하는 MVP.

이 MVP의 성격은 세 가지로 못 박는다:

1. **제품 코드 기반 씨앗** — 데모용 일회성 코드가 아니라, 앞으로 계속 확장할 실제 코드베이스의 1호 버전. 따라서 **계층 경계와 확장 인터페이스(표준 수용 지점)가 최우선 품질 기준**이다.
2. **표준 쇼케이스** — "확장 가능"을 말로 두지 않는다. 각 도메인 포트 뒤에 **실제 표준을 최소 1개 동작하도록 구현**하고, 나머지 후보 표준은 **같은 포트에 등록된 추상 어댑터(stub)** 로 존재를 증명한다. 데모에서 "이 자리에 x402가 꽂히고, 여기 AP2가 들어가며, 이건 지금 동작한다"를 실물로 보여주는 것이 핵심 가치.
3. **Walking skeleton = 결제·위임 코어** — 9개 계층을 전부 얇게 펴지 않는다. 이 제품의 심장이자 표준(AP2·x402·DID)이 몰리는 **위임된·정책으로 제한된·에이전트가 개시하는 예치금 결제** 한 줄기만 진짜로 관통시키고, 나머지는 인터페이스만 남겨 mock 처리한다.

---

## 2. 범위 (YAGNI)

### v1에 포함 (진짜 동작)
- 에이전트 등록 + 공개키 기반 신원 검증 (did:key, did:web)
- 사용자 → 에이전트 구매 권한 위임 (AP2 Mandate, 서명·검증)
- 예치금 관리 (하이브리드 커스터디: 온체인 입금 → DB 잔고)
- 에이전트 서명 결제 요청 → 신원·정책·잔고 검증 → 차감 (단일 트랜잭션)
- 온체인 정산 (x402 / EIP-3009, 테스트 USDC)
- 필터 가능 상품 카탈로그 + 조건 탐색 (schema.org / ACP Feed)
- 전 과정 감사 이력 + 서명된 VC 영수증

### v1에서 제외 (인터페이스/stub만)
- LLM 주도 에이전트 런타임 (에이전트 = 프로그래밍 클라이언트 CLI)
- 실제 결제수단(카드·계좌) — `PaymentRail` 후보 어댑터로만
- 후보 표준의 실제 구현 (did:ethr, UCAN, ERC-4337, UCP 등) — stub 어댑터로 존재만
- 프로덕션 인증/인가·멀티테넌시·정교한 지갑 프로바이더 추상화
- 분산 트랜잭션·메시지 브로커 (코어는 로컬 트랜잭션)

---

## 3. 기술 스택 (씨앗 베이스라인)

| 항목 | 선택 | 비고 |
|---|---|---|
| 언어 | **Java 25 (LTS)** | 최신 LTS(Temurin 25 설치). Boot 4.1 baseline은 17. 컴파일·Gradle 데몬 모두 25로 고정(비-LTS 26 배제) |
| 프레임워크 | **Spring Boot 4.1.x** | Spring Framework 7 기반 |
| 빌드 | **Gradle (Kotlin DSL)** | |
| DB | **PostgreSQL** | core_db / commerce_db 스키마 분리 |
| EVM 클라이언트 | **web3j** | `evm-gateway` 서비스에 격리 |
| 로컬 EVM | **Foundry anvil** | env로 Base Sepolia 테스트넷 전환 가능 |
| 프록시 | **nginx** | 리버스 프록시, 단일 진입점 |
| 오케스트레이션 | **docker compose** | |
| 모듈 경계 강제 | **ArchUnit** | Spring Modulith 미채택 (아래 4.1 참조) |

> **버전 확정/검증**: Spring Boot **4.1.0**(plugin), Java **25 (LTS)**. `gradle/gradle-daemon-jvm.properties`로 데몬도 25 고정. web3j의 Spring 7/Jakarta 호환, springdoc 등 통합 라이브러리의 Framework 7 지원 여부는 각 라이브러리를 도입하는 시점에 확인한다(early-adopter 세금).

---

## 4. 아키텍처

### 4.1 왜 Modulith가 아니라 헥사고날 코어 모놀리스 + ArchUnit인가

- Spring Modulith 2.0은 공식적으로 **Spring Boot 4.0 컴파일 기준**이라 4.1과 버전 마찰 가능성이 있고, 갓 나온 의존성에 씨앗을 걸 이유가 없다. → **Modulith 의존성 미채택.**
- 모듈 경계는 Modulith 없이도 **ArchUnit 테스트**로 동일하게 컴파일/CI 레벨에서 강제한다.
- 모듈 간 durable 이벤트(Modulith Event Publication Registry)는 **DB outbox 테이블 + Spring `@TransactionalEventListener`** 로 대체한다 (재시작에도 살아남는 감사 이벤트 전달).

### 4.2 서비스 토폴로지

```
                     nginx  (리버스 프록시, :80, 단일 진입점)
                       │
        ┌──────────────┼──────────────────┐
        │              │                  │
  core-service    commerce-mock       evm-gateway
  (Spring Boot     (Spring Boot        (Spring Boot + web3j)
   4.1 / Java25)    카탈로그·ACP)       예치감지·x402 정산
   identity            │                  │
   delegation      commerce_db        EVM 노드(anvil) / Base Sepolia
   custody
   payment
   audit  ← ArchUnit 경계
        │
    postgres (core_db)

  agent-cli  ── 온디맨드 실행(상주 X). 키 보관·EIP-712 서명·요청.
```

- **core-service**: 트랜잭션 심장. identity·delegation·custody·payment·audit를 내부 패키지 모듈로 두고 ArchUnit로 경계 강제. `core_db` 소유.
- **commerce-mock**: 별도 서비스. 상품 카탈로그·필터·주문·ACP Feed. 이 분리가 곧 미래 커머스 표준(ACP/UCP) 교체 지점.
- **evm-gateway**: web3j를 격리하는 별도 서비스. 예치 감지 + 온체인 정산. 이 분리가 곧 체인 교체 지점. core는 REST로만 대화.
- **nginx**: 세 서비스를 하나의 진입점으로 프록시.
- **agent-cli**: 별도 Gradle 모듈. 서버가 아니라 클라이언트 — 키페어 보관, 요청/mandate 서명, core·commerce 호출.

### 4.3 헥사고날 포트 (도메인 코어가 아는 유일한 확장 지점)

도메인 코어는 표준·인프라를 모르고 아래 **포트 인터페이스**로만 대화한다. 표준 수용 = 어댑터 교체.

| 포트 | 책임 |
|---|---|
| `AgentIdentity` | 에이전트/사용자 서명 검증, DID 해석 |
| `AgentDirectory` | 에이전트 등록·발견 |
| `MandateService` + `PolicyEngine` | 위임 발급·검증, 정책 평가 |
| `PaymentRail` | 결제 승인·차감·정산 |
| `CommerceGateway` | 상품 조회·주문·charge |
| `AuditLog` | append-only 이력 + 영수증 |

---

## 5. 표준 쇼케이스 매트릭스

각 포트: **v1 실제 구현(동작) + 추상화 후보(같은 포트의 stub 어댑터)**.

| 도메인 / 포트 | v1 구현 (real, 동작) | 추상화 후보 (stub) |
|---|---|---|
| identity `AgentIdentity` | **did:key + did:web** (secp256k1) | did:ethr, did:pkh |
| discovery `AgentDirectory` | **A2A AgentCard** | MCP, plain REST |
| delegation `MandateService` | **AP2 Mandate** (Intent/Cart, 서명 검증가능 위임) | UCAN, OAuth2 scopes, W3C VC |
| payment `PaymentRail` | **x402** (HTTP 402 + EIP-3009 USDC 정산) | 온체인 에스크로, ERC-4337, 카드/계좌 |
| commerce `CommerceGateway` | **ACP** (Product Feed + Checkout) | UCP, A2A-commerce |
| audit `AuditLog` | **VC 영수증** (서명된 Verifiable Credential) | 온체인 앵커링, 외부 SIEM |

**설계 정합성 근거:**
- **AP2 + x402는 원래 짝** — AP2 mandate가 "위임 권한을 서명으로 증명", x402가 "그 권한으로 온체인 스테이블코인 결제를 HTTP로 실행". AP2 스펙이 x402 익스텐션을 언급.
- **did:key/did:web**을 identity 실구현으로 두면 secp256k1 키 하나가 DID·EVM 신원·서명키로 3중 활용 → 계층이 하나의 키로 꿰진다.
- **VC 실(thread)이 관통** — 오픈배지 상품(Open Badges 3.0 = VC) ↔ audit 영수증(VC)이 같은 표준.

**스코프 규칙**: v1은 도메인당 헤드라인을 확실히 동작시키고(identity는 2개), 후보는 `@Component` 등록된 stub 어댑터(같은 인터페이스, mock 반환 또는 `UnsupportedOperationException`)로 존재만 증명한다.

---

## 6. 도메인 모델 (core_db)

| 엔티티 | 핵심 필드 | 모듈 |
|---|---|---|
| `User` | id, alias, publicKey(secp256k1) | identity |
| `Agent` | id, ownerUserId, **publicKey(secp256k1)**, did, alias, status | identity |
| `CustodyAccount` | id, ownerUserId, token, **balance**, depositAddress | custody |
| `Deposit` | id, custodyAccountId, txHash, amount, confirmedAt | custody |
| `Mandate` | id, agentId, ownerUserId, perTxLimit, totalLimit, **spent**, validFrom/Until, allowedPayees[], currency, nonce, **userSignature**, status | delegation |
| `Payment` | id, agentId, mandateId, payee, amount, currency, ref, nonce, **agentSignature**, status | payment |
| `LedgerEntry` | id, custodyAccountId, paymentId, delta, ts (append-only) | custody |
| `SettlementTx` | id, paymentId, txHash, onchainStatus | custody |
| `OutboxEvent` | id, type, payload, published, ts (append-only) | (공통) |
| `AuditEvent` | id, type, payload, ts (append-only) | audit |

`Payment.status`: `PENDING → AUTHORIZED → SETTLING → SETTLED` / `REJECTED` / `FAILED`.

---

## 7. 결제 코어 흐름 (walking skeleton)

1. **에이전트 등록** — 사용자가 에이전트 공개키·DID 등록 → `AgentRegistered`
2. **예치** — 사용자가 테스트 USDC를 depositAddress로 전송(온체인) → evm-gateway 확인 → 잔고 크레딧 + `Deposit` → `DepositCredited`
3. **mandate 발급** — 사용자가 (perTxLimit·totalLimit·기간·allowedPayees·currency·nonce)를 **EIP-712 서명** → core가 사용자 서명 검증·저장 → `MandateIssued`
4. **탐색** — agent-cli가 commerce-mock에 `type=&maxBudget=남은한도` 조건 질의 → 후보 상품·payee 수신 → 하나 선택
5. **결제 개시** — agent-cli가 (mandateId, payee, amount, ref, nonce)를 **에이전트키로 EIP-712 서명** → core로 POST
6. **★ 승인 (심장, 단일 DB 트랜잭션)**
   - ① 에이전트 서명 검증 (`AgentIdentity`)
   - ② mandate 검증 (`PolicyEngine`): 소유 일치·미만료·payee 허용·perTxLimit·`spent + amount ≤ totalLimit`
   - ③ 잔고 검증: `balance ≥ amount` (`custody`)
   - → 통과: **잔고 차감 + `mandate.spent` 증가 + `Payment(AUTHORIZED)` + `LedgerEntry` + `OutboxEvent(PaymentAuthorized)`** 를 **한 트랜잭션**으로 커밋
   - → 실패: `Payment(REJECTED, reason)` + `OutboxEvent(PaymentRejected)`
7. **정산 (async)** — outbox 발행자가 `PaymentAuthorized` 소비 → evm-gateway로 x402/EIP-3009 온체인 transfer → 확인 시 `SettlementTx` + `Payment(SETTLED)` → `PaymentSettled`
8. **감사 + 영수증** — audit가 모든 이벤트 소비 → append-only 기록 + 서명된 **VC 영수증** 발급. core가 검증 가능한 receipt 반환.

**격리 불변식**: commerce는 이 흐름에 개입하지 않는다. core는 `payee + ref + amount`만 받으며 커머스 내부를 모른다.

**원자성 경계**: 6단계(승인·차감)는 로컬 트랜잭션으로 원자적. 7단계(온체인 정산)는 그 뒤 async — 잔고는 이미 커스터디에서 차감됐고, 정산 실패 시 보상 크레딧 + `Payment(FAILED)`로 되돌린다 (아래 13 참조).

---

## 8. 신원 & 서명

- **커브**: 사용자·에이전트 키 모두 **secp256k1** (EVM 키와 동일) → DID·온체인 신원·요청 서명키로 3중 활용.
- **서명 포맷**: **EIP-712 typed data**. 구조화·표준·x402/AP2와 결이 맞음. mandate와 payment 요청 각각 typed struct 정의.
- **리플레이 방지**: mandate·payment 요청에 `nonce`. core가 소비된 nonce 재사용을 거부.
- **DID 구현**:
  - `did:key` — secp256k1 공개키를 multibase 인코딩한 자체완결 DID. 외부 해석 불필요, 완전 동작.
  - `did:web` — `https://<domain>/.well-known/did.json`으로 해석되는 DID 문서. `AgentDirectory`가 이 문서를 게시.
  - 후보(did:ethr, did:pkh)는 `DidResolver` 인터페이스 뒤 stub.
- **discovery**: **A2A AgentCard** — 에이전트가 자기 능력·엔드포인트·공개키를 담은 AgentCard를 게시, `AgentDirectory`가 등록·조회.

---

## 9. 위임 (AP2 Mandate)

- **AP2 Mandate**를 위임의 실제 표현으로 채택. 사용자가 발급하는 서명된 검증가능 위임 객체(Intent/Cart mandate 개념).
- 필드: 대상 에이전트, perTxLimit, totalLimit, 유효기간, allowedPayees(또는 카테고리 조건), currency, nonce, 사용자 서명.
- `PolicyEngine`이 결제 승인 시 mandate를 평가 (7단계 ②).
- 후보(UCAN, OAuth2 scopes, W3C VC)는 `MandateService` 인터페이스 뒤 stub 어댑터.

---

## 10. 결제 & 정산 (x402 / 하이브리드 커스터디)

- **하이브리드 커스터디**: 사용자가 테스트 USDC를 플랫폼 depositAddress로 입금 → DB 잔고 크레딧. 정책 검증·차감은 백엔드(`PolicyEngine`/`custody`), 정산은 온체인 transfer.
- **x402 + EIP-3009**: 정산은 x402 흐름(HTTP 402 Payment)으로 표현하고, 실제 온체인 이동은 **EIP-3009 `transferWithAuthorization`**(가스리스 USDC 전송)로 실행 → 위임·서명 결제의 성격에 부합.
- **test USDC**: EIP-3009를 지원하는 mock ERC-20를 anvil 부팅 시 배포. Base Sepolia 전환 시 테스트넷 USDC 사용.
- **evm-gateway** 책임: (a) 예치 트랜잭션 확인/감지 → core에 콜백/조회 API, (b) 정산 transfer 실행 → txHash·상태 반환. web3j는 이 서비스에만 존재.
- 후보(온체인 에스크로 컨트랙트, ERC-4337, 카드/계좌)는 `PaymentRail` 인터페이스 뒤 stub.

---

## 11. Commerce (schema.org / ACP Product Feed)

### 상품 데이터 모델 표준
- **schema.org `Product`/`Offer` 어휘**를 **ACP Product Feed 규약**으로 전달. commerce 헤드라인(ACP)과 자연 결합.
- `Product`(name·category·sku) + `Offer`(price·priceCurrency·availability·seller). `Offer.seller` → `payeeAddress`.
- 타입별 커스텀 속성은 schema.org `additionalProperty`(`PropertyValue{name,value}`)로 표준 안에서 확장.
- 후보: Google Product Taxonomy(카테고리), GS1 GTIN(식별자).

### 상품 타입 (유형/무형 스펙트럼)

| 타입 | schema.org @type | 예시 | additionalProperty |
|---|---|---|---|
| `SUBSCRIPTION` | Product + Offer(UnitPriceSpecification) | Pro 구독권 1개월 | periodDays |
| `CRYPTO_TOKEN` | Product (digital) | 유틸리티 토큰 100개 | chain, tokenAddress, qty |
| `NFT` | Product (digital) | 금 1g NFT (RWA) | collection, tokenId, backedGrams |
| `SERVICE_FEE` | Service | 오픈배지 증명서 발행 수수료 | issuer, **credentialType → Open Badges 3.0** |
| `PHYSICAL` | Product | 실물 상품 | gtin, shippingRequired |

### 필터 API (탐색 데모 중심)
```
GET /catalog?type=&minPrice=&maxPrice=&currency=&tag=&maxBudget=
```
- `type` / `minPrice`·`maxPrice` / `currency` / `tag`(키워드)
- **★ `maxBudget`** — 에이전트가 남은 mandate 한도를 넘겨 "예산 내 상품만" 필터 → **탐색이 위임된 예산에 제약됨** (킬러 데모 훅).

---

## 11-B. 재사용 가능한 결재대행 연동 (mall-agnostic)

commerce-mock은 결재대행의 **한 예시 소비자**일 뿐이다. 결재대행(core-service)의 바깥 계약은 특정 몰에 종속되지 않는 **재사용 가능한 통합**으로 설계해, 어떤 서비스든 바로 붙일 수 있게 한다.

- **공개 통합 API (mall-agnostic)**: register agent · issue mandate · initiate payment · fetch receipt · verify. 깨끗한 리소스 URL(`/agents`, `/mandates`, `/payments`, `/receipts`) — **URL에 `/v1` 버전 프리픽스를 두지 않는다**(낡은 패턴). 하위호환 진화가 기본이고, 진짜 breaking change가 필요할 때만 **헤더 기반 버전**(예: `Agentpay-Version: 2026-07-07`, Stripe 방식)을 도입. OpenAPI(springdoc)로 문서화.
- **`:agentpay-client` SDK**: 외부 서비스가 import해 즉시 연동하는 얇은 Java 클라이언트. MVP의 commerce-mock이 이 SDK로 core와 대화(도그푸딩)해 "서비스 무관"을 증명.
- **웹훅/콜백**: `PaymentSettled`를 payee(몰)에게 웹훅으로 통지 → 외부 몰이 폴링 없이 정산·주문 반영.
- **불변식**: core에는 어떤 몰-특화 로직도 들어가지 않는다. 몰이 아는 것은 공개 API·웹훅 계약뿐. (Phase 6/7에서 구현; 그 전까지 core의 REST를 이 계약 관점으로 설계)

---

## 12. Audit & 영수증

- **outbox → 이벤트 소비**: 모든 상태 변화가 `OutboxEvent`로 기록되고, `@TransactionalEventListener` + 발행자가 audit로 durable 전달 (Modulith 없이 재시작 내성).
- `audit`가 `AuditEvent` append-only 테이블에 기록 → 감사·분쟁대응·거래검증.
- **VC 영수증**: 결제 완료 시 서명된 Verifiable Credential 형태 영수증 발급 (오픈배지 상품의 OB3.0과 같은 VC 실).
- 후보(온체인 앵커링, 외부 SIEM)는 `AuditLog` 인터페이스 뒤 stub.

---

## 13. 에러 처리 & 엣지 케이스

- **정산 실패** (7단계): 잔고는 이미 차감됨 → 보상 크레딧(`LedgerEntry` 역분개) + `mandate.spent` 롤백 + `Payment(FAILED)` + `PaymentFailed` 이벤트. 재시도 정책(N회) 후 최종 실패 처리.
- **리플레이/중복 요청**: nonce 소비 검사 + payment 요청 idempotency key → 동일 요청 재처리 시 기존 결과 반환.
- **mandate 초과/만료**: 승인 트랜잭션에서 `REJECTED(reason)` — 사유를 감사·영수증에 기록.
- **동시성**: 같은 custody account 동시 결제 → 잔고 차감에 낙관적 락(version) 또는 `SELECT ... FOR UPDATE`로 이중지출 방지.
- **예치 확인 지연**: 온체인 confirmations 기준 충족 전까지 잔고 미크레딧.
- **서명 불일치/키 미등록**: identity 검증 실패 → 401/거부 + 감사 기록.

---

## 14. 테스트 전략

- **ArchUnit**: 모듈 경계 규칙 테스트 (예: `identity`가 `payment` 내부 import 금지, web3j는 evm-gateway에만). **CI 필수 게이트.**
- **단위**: PolicyEngine(한도·기간·payee 조합), 서명 검증(did:key/did:web), 필터 로직.
- **통합**: 승인 트랜잭션 원자성(차감+spent+payment 한 커밋), outbox→audit 전달, Testcontainers(Postgres).
- **온체인**: anvil 대상 EIP-3009 정산 e2e (예치→승인→정산→SETTLED).
- **e2e walking skeleton**: 등록→예치→mandate→탐색→결제→정산→영수증 전 흐름 스크립트(agent-cli 사용).

---

## 15. 모듈 / 패키지 구조 (제안)

베이스 패키지 `io.github.wantaekchoi.agentpay` (GitHub `wantaekchoi/agentpay`).

```
:app  (core-service)
  io.github.wantaekchoi.agentpay
    identity/      (Agent, User, AgentIdentity 포트, did:key/did:web 어댑터, A2A AgentCard)
    delegation/    (Mandate, MandateService 포트, PolicyEngine, AP2 어댑터, 후보 stub)
    custody/       (CustodyAccount, Deposit, Ledger, PaymentRail 포트, x402 어댑터)
    payment/       (Payment, 승인 유스케이스 오케스트레이션)
    commerce/      (CommerceGateway 포트 — ACP 어댑터가 commerce-mock 호출)
    audit/         (AuditLog 포트, VC 영수증, 이벤트 리스너)
    shared/        (Outbox, 이벤트, 서명 유틸(EIP-712/secp256k1))
:commerce-mock     (schema.org/ACP Feed 카탈로그, 필터, 주문)
:evm-gateway       (web3j, 예치감지, x402/EIP-3009 정산)
:agent-cli         (키 보관, EIP-712 서명, 탐색·결제 클라이언트)
:agentpay-client   (외부 서비스용 얇은 통합 SDK — commerce-mock이 도그푸딩)
contracts/         (Foundry: mock USDC(EIP-3009))
compose.yml, nginx/
```

ArchUnit 규칙: 각 도메인 패키지는 자기 포트 인터페이스로만 상호 참조. web3j 타입은 `:evm-gateway` 밖으로 노출 금지.

---

## 16. 인프라 (compose)

```
nginx        :80    리버스 프록시, 단일 진입점
core-service :8080  Spring Boot 4.1 / Java 25
commerce-mock:8081  Spring Boot
evm-gateway  :8082  Spring Boot + web3j
postgres     :5432  core_db / commerce_db
anvil        :8545  Foundry 로컬 EVM — 부팅 시 mock USDC(EIP-3009) 배포
```
- **EVM**: `anvil` 기본, env(`EVM_RPC_URL`, `CHAIN_ID`)로 Base Sepolia 전환.
- **사용자 인증**: MVP는 최소화 — dev 유저를 키페어와 함께 시드. 초점은 로그인이 아니라 mandate 서명·검증 계층.
- agent-cli는 상주 서비스 아님(온디맨드 실행).

---

## 17. 주요 설계 결정 & 근거 (ADR 씨앗)

| 결정 | 근거 | 대안 (기각) |
|---|---|---|
| 헥사고날 코어 모놀리스 + ArchUnit | 씨앗의 경계를 컴파일/CI로 강제하되 빌드 단순 | 멀티모듈 Gradle(빌드 복잡), Modulith(Boot4.1 버전 마찰) |
| Modulith 미채택 | 2.0이 Boot 4.0 컴파일 기준, BOM 억지 수정 회피 | Modulith 2.0(신생·마찰), Boot 3.5 다운그레이드(최신 요구 위배) |
| commerce·evm만 서비스 분리 | 분리 지점 = 미래 표준 교체 지점. 결제 원자성은 코어 로컬 트랜잭션 유지 | 풀 마이크로서비스(분산 트랜잭션 위험) |
| 하이브리드 커스터디 | Solidity 없이 빠르게, PaymentRail 뒤에서 에스크로로 교체 가능 | 온체인 에스크로(툴체인 무거움), 순수 시뮬레이션(차별점 약화) |
| 에이전트 = 프로그래밍 클라이언트 | walking skeleton은 결제 코어. LLM은 상관성 낮음 | LLM 주도 에이전트(범위 과대) |
| secp256k1 + EIP-712 | 키 하나로 DID·EVM·서명 3중 활용, x402/AP2 결 | ed25519(EVM 비호환) |
| Boot 4.1 + Java 25 | 최신 프레임워크 + 최신 LTS(둘 다 설치). 컴파일·데몬 모두 25, 비-LTS 26 배제 | Java 21(구 LTS), Boot 3.5(구버전) |
| 결재대행 재사용 API+SDK 분리 | 다른 몰/서비스가 즉시 연동. commerce-mock은 한 소비자 | 커머스에 결제 로직 결합(재사용 불가) |
| API 무버전 URL + 헤더 버전 | `/v1` URL은 낡음. 하위호환 진화 + 필요시 헤더(날짜) 버전 | URL `/v1/`(레거시 패턴) |
| AP2 + x402 짝 | 위임 증명(AP2) + 온체인 실행(x402)이 원래 조합 | — |
| schema.org + ACP Feed | 상품 표준이 commerce 헤드라인(ACP)과 결합 | Google Product Taxonomy(리테일 편향), GS1(과잉) |

---

## 18. 구현 전 검증 필요 항목 (공식 스펙 대조)

아래는 2025년 발표된 신생 스펙들 — **구현 착수 시 공식 문서로 정확한 wire-format을 대조·고정**한다. 어댑터 뒤라 스펙 변동에도 교체가 쉽다.

- **x402**: payment 헤더/facilitator 흐름, EIP-3009 파라미터
- **AP2**: mandate 구조(Intent/Cart), 서명·검증 규격
- **ACP**: Product Feed 스키마, Checkout 엔드포인트
- **A2A**: AgentCard 필드
- **schema.org / Open Badges 3.0**: Product/Offer 프로퍼티, OB3.0 VC 구조
- **버전**: Spring Boot 4.1.x 패치, Java 25 호환, web3j ↔ Spring 7/Jakarta

---

## 19. 다음 단계

1. 이 스펙 사용자 검토
2. `writing-plans` 스킬로 구현 계획 수립 (walking skeleton 우선순위: 서명·신원 → mandate → 예치·잔고 → 승인 트랜잭션 → 정산 → 탐색 → 영수증)
3. 착수 시 `CURRENT_STATE.md` / `NEXT_STEP.md` / `docs/ADR/` 생성 (본 문서 17장이 ADR 씨앗)
