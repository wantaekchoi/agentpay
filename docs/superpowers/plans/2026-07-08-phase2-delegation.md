# Phase 2: 위임 (AP2 Intent Mandate) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax.

**Goal:** 사용자가 EIP-712로 서명한 intent mandate(한도·기간·허용 payee)를 검증·저장하고, PolicyEngine이 (payee, amount, currency)를 그 mandate에 대해 평가하며, revoke까지 되는 위임 계층.

**Architecture:** 새 `delegation` 모듈(헥사고날). `shared/crypto`에 EIP-712 유틸 추가. 서명·검증은 shared 통해서만(ArchUnit 강제). 결제 실행·spent 증가는 범위 밖(Phase 4).

**Tech Stack:** Phase 1과 동일 — Java 25, Spring Boot 4.1.0, Gradle 9.6.1, Postgres/Flyway, Testcontainers, web3j(crypto, `StructuredDataEncoder`), ArchUnit.

## Global Constraints
- 베이스 패키지 `io.github.wantaekchoi.agentpay`.
- 서명: **EIP-712 typed data**(secp256k1). mandate 검증은 shared 유틸로만 — `delegation`/`identity` 프로덕션 코드는 `org.web3j` 직접 import 금지(ArchUnit `..delegation..`·`..identity..` → `org.web3j..` 금지).
- 개인키는 저장/로그 금지. Mandate엔 userSignature(공개)만.
- 무버전 리소스 URL. 에러는 기존 `web/ApiExceptionHandler` 재사용(400/404/409).
- 통합테스트는 공유 `TestcontainersConfiguration`을 `@Import`로 재사용.
- 금액은 `BigInteger`(wei 단위 정수), 통화는 문자열 심볼.
- **verify-at-task-time:** web3j `StructuredDataEncoder(jsonString)`·`hashStructuredData()`·`Sign.signedMessageHashToKey(byte[],SignatureData)`의 정확한 시그니처를 Task 1에서 실제 jar로 확인(Phase 1 방식). AP2 공개 스펙의 mandate 필드는 우리 struct를 최대한 맞추되 어댑터 뒤라 교체 가능.

---

## File Structure
```
app/src/main/java/io/github/wantaekchoi/agentpay/
  shared/crypto/Eip712Mandate.java           # EIP-712 typed-data 빌드·서명·복구
  delegation/domain/Mandate.java             # JPA
  delegation/domain/MandateRepository.java
  delegation/port/MandateService.java        # 포트
  delegation/port/PolicyEngine.java          # 포트 (+ PaymentContext, PolicyDecision)
  delegation/Ap2MandateService.java          # MandateService 구현(EIP-712)
  delegation/DefaultPolicyEngine.java
  delegation/adapter/UcanMandateAdapter.java # 후보 stub
  delegation/web/MandateController.java
  identity/UserRegistrationService.java       # 신규
  identity/web/UserController.java            # POST /users
app/src/main/resources/db/migration/V3__mandates.sql
app/src/test/java/io/github/wantaekchoi/agentpay/
  shared/crypto/Eip712MandateTest.java
  delegation/MandatePersistenceTest.java
  delegation/DefaultPolicyEngineTest.java
  delegation/Ap2MandateServiceTest.java
  identity/web/UserControllerTest.java
  delegation/web/MandateControllerTest.java
  architecture/ModuleBoundaryTest.java        # 규칙 추가
```

---

### Task 1: EIP-712 mandate 유틸 (`shared/crypto/Eip712Mandate`)

**Files:** Create `shared/crypto/Eip712Mandate.java`; Test `shared/crypto/Eip712MandateTest.java`.

**Interfaces — Produces:**
- `record MandateData(String user, String agent, String currency, BigInteger perTxLimit, BigInteger totalLimit, List<String> allowedPayees, boolean allowAny, long validFrom, long validUntil, BigInteger nonce)`
- `static String Eip712Mandate.typedDataJson(MandateData d, long chainId)` — 결정론적 EIP-712 JSON(types/primaryType=`AgentPaymentMandate`/domain{name:"agentpay",version:"1",chainId}/message)
- `static String Eip712Mandate.sign(MandateData d, long chainId, BigInteger privateKey)` — hex 서명 (테스트/클라이언트용)
- `static String Eip712Mandate.recoverSigner(MandateData d, long chainId, String signatureHex)` — 서명자 주소(소문자 0x)

핵심: sign·recover 모두 **같은 `typedDataJson`** 을 쓰므로 필드 순서/타입이 자동 일치.

- [ ] **Step 1: 실패 테스트**
```java
package io.github.wantaekchoi.agentpay.shared.crypto;
import org.junit.jupiter.api.Test;
import java.math.BigInteger; import java.util.List;
import static org.assertj.core.api.Assertions.*;

class Eip712MandateTest {
  private Eip712Mandate.MandateData sample(String user, String agent){
    return new Eip712Mandate.MandateData(user, agent, "USDC",
      BigInteger.valueOf(100), BigInteger.valueOf(1000),
      List.of("0x1111111111111111111111111111111111111111"), false,
      1000L, 2000L, BigInteger.ONE);
  }
  @Test void signThenRecover_returnsSignerAddress(){
    var kp = Signatures.generateKeyPair();
    var d = sample(kp.address(), "0x2222222222222222222222222222222222222222");
    String sig = Eip712Mandate.sign(d, 31337L, kp.privateKey());
    assertThat(Eip712Mandate.recoverSigner(d, 31337L, sig)).isEqualTo(kp.address());
  }
  @Test void tamperedField_recoversDifferentAddress(){
    var kp = Signatures.generateKeyPair();
    var d = sample(kp.address(), "0x2222222222222222222222222222222222222222");
    String sig = Eip712Mandate.sign(d, 31337L, kp.privateKey());
    var tampered = sample(kp.address(), "0x3333333333333333333333333333333333333333");
    assertThat(Eip712Mandate.recoverSigner(tampered, 31337L, sig)).isNotEqualTo(kp.address());
  }
}
```
- [ ] **Step 2: 실행 → 실패 확인** — `./gradlew :app:test --tests "*Eip712MandateTest"` → FAIL(미존재).
- [ ] **Step 3: 구현** — web3j `StructuredDataEncoder`로 EIP-712 JSON을 해시, `Sign`으로 서명/복구:
```java
package io.github.wantaekchoi.agentpay.shared.crypto;
import java.math.BigInteger; import java.util.List; import java.util.stream.Collectors;
import org.web3j.crypto.*; import org.web3j.utils.Numeric;

public final class Eip712Mandate {
  private Eip712Mandate(){}
  public record MandateData(String user, String agent, String currency,
      BigInteger perTxLimit, BigInteger totalLimit, List<String> allowedPayees,
      boolean allowAny, long validFrom, long validUntil, BigInteger nonce){}

  public static String typedDataJson(MandateData d, long chainId){
    String payees = d.allowedPayees().stream()
        .map(p -> "\"" + p + "\"").collect(Collectors.joining(","));
    // EIP-712 JSON. 필드 순서는 types 정의 순서와 일치해야 함.
    return "{"
      + "\"types\":{"
      +   "\"EIP712Domain\":[{\"name\":\"name\",\"type\":\"string\"},{\"name\":\"version\",\"type\":\"string\"},{\"name\":\"chainId\",\"type\":\"uint256\"}],"
      +   "\"AgentPaymentMandate\":["
      +     "{\"name\":\"user\",\"type\":\"address\"},{\"name\":\"agent\",\"type\":\"address\"},"
      +     "{\"name\":\"currency\",\"type\":\"string\"},"
      +     "{\"name\":\"perTxLimit\",\"type\":\"uint256\"},{\"name\":\"totalLimit\",\"type\":\"uint256\"},"
      +     "{\"name\":\"allowedPayees\",\"type\":\"address[]\"},{\"name\":\"allowAny\",\"type\":\"bool\"},"
      +     "{\"name\":\"validFrom\",\"type\":\"uint256\"},{\"name\":\"validUntil\",\"type\":\"uint256\"},{\"name\":\"nonce\",\"type\":\"uint256\"}"
      +   "]},"
      + "\"primaryType\":\"AgentPaymentMandate\","
      + "\"domain\":{\"name\":\"agentpay\",\"version\":\"1\",\"chainId\":" + chainId + "},"
      + "\"message\":{"
      +   "\"user\":\"" + d.user() + "\",\"agent\":\"" + d.agent() + "\","
      +   "\"currency\":\"" + d.currency() + "\","
      +   "\"perTxLimit\":" + d.perTxLimit() + ",\"totalLimit\":" + d.totalLimit() + ","
      +   "\"allowedPayees\":[" + payees + "],\"allowAny\":" + d.allowAny() + ","
      +   "\"validFrom\":" + d.validFrom() + ",\"validUntil\":" + d.validUntil() + ",\"nonce\":" + d.nonce()
      + "}}";
  }

  private static byte[] digest(MandateData d, long chainId){
    try { return new StructuredDataEncoder(typedDataJson(d, chainId)).hashStructuredData(); }
    catch (Exception e){ throw new IllegalArgumentException("EIP-712 인코딩 실패", e); }
  }

  public static String sign(MandateData d, long chainId, BigInteger privateKey){
    Sign.SignatureData sd = Sign.signMessage(digest(d, chainId), ECKeyPair.create(privateKey), false);
    byte[] out = new byte[65];
    System.arraycopy(sd.getR(),0,out,0,32); System.arraycopy(sd.getS(),0,out,32,32); out[64]=sd.getV()[0];
    return Numeric.toHexString(out);
  }

  public static String recoverSigner(MandateData d, long chainId, String signatureHex){
    byte[] sig = Numeric.hexStringToByteArray(signatureHex);
    if (sig.length != 65) throw new IllegalArgumentException("서명 길이 오류");
    byte[] r=new byte[32], s=new byte[32];
    System.arraycopy(sig,0,r,0,32); System.arraycopy(sig,32,s,0,32);
    Sign.SignatureData sd = new Sign.SignatureData(sig[64], r, s);
    try {
      BigInteger pub = Sign.signedMessageHashToKey(digest(d, chainId), sd);
      return Numeric.prependHexPrefix(Keys.getAddress(pub)).toLowerCase();
    } catch (Exception e){ throw new IllegalArgumentException("서명 복구 실패", e); }
  }
}
```
> **verify-at-task-time:** 실제 web3j 4.12.3에서 `StructuredDataEncoder(String)`·`hashStructuredData()`·`Sign.signMessage(byte[],ECKeyPair,boolean)`·`Sign.signedMessageHashToKey(byte[],SignatureData)` 시그니처를 jar로 확인하고 필요 시 조정(테스트가 gate). `signMessage`의 3번째 인자 `false`=해시 재적용 안 함(digest 그대로 서명).
- [ ] **Step 4: 실행 → 통과** — 2 tests PASS.
- [ ] **Step 5: 커밋** — `feat(shared): EIP-712 mandate typed-data sign/recover`

---

### Task 2: delegation 도메인 (Mandate JPA + V3)

**Files:** Create `delegation/domain/Mandate.java`, `delegation/domain/MandateRepository.java`, `db/migration/V3__mandates.sql`; Test `delegation/MandatePersistenceTest.java`.

**Interfaces — Produces:**
- `enum MandateStatus { ACTIVE, REVOKED, EXPIRED }` (in domain)
- JPA `Mandate` (아래 컬럼), getters + `revoke()` (status=REVOKED) + `addSpent(BigInteger)` (Phase 4 대비; 지금은 미사용이라도 정의).
- `MandateRepository extends JpaRepository<Mandate, UUID>` + `List<Mandate> findByAgentId(UUID)` + `List<Mandate> findByUserId(UUID)` + `boolean existsByUserIdAndNonce(UUID, BigInteger)`.

- [ ] **Step 1: V3 마이그레이션**
```sql
create table mandates (
    id            uuid primary key,
    user_id       uuid         not null references users(id),
    agent_id      uuid         not null references agents(id),
    currency      varchar(32)  not null,
    per_tx_limit  numeric(78,0) not null,
    total_limit   numeric(78,0) not null,
    spent         numeric(78,0) not null default 0,
    allow_any_payee boolean     not null default false,
    valid_from    bigint       not null,
    valid_until   bigint       not null,
    nonce         numeric(78,0) not null,
    user_signature varchar(200) not null,
    status        varchar(20)  not null,
    created_at    timestamptz  not null default now(),
    constraint uq_mandate_user_nonce unique (user_id, nonce)
);
create table mandate_allowed_payees (
    mandate_id uuid not null references mandates(id) on delete cascade,
    payee      varchar(64) not null,
    primary key (mandate_id, payee)
);
```
- [ ] **Step 2: 실패 테스트** — `MandatePersistenceTest`(`@SpringBootTest @Import(TestcontainersConfiguration.class)`): User+Agent 저장 후 Mandate(allowedPayees 1개, status ACTIVE) 저장→`findByAgentId`로 조회, allowedPayees 왕복 확인. 실행 → FAIL.
- [ ] **Step 3: 구현** — `Mandate` 엔티티는 Phase 1 `Agent` 패턴을 그대로 따른다(protected no-arg + all-args 생성자 + getters, `@Column(name=...)` snake_case). `allowedPayees`는 `@ElementCollection @CollectionTable(name="mandate_allowed_payees") @Column(name="payee") Set<String>`. `perTxLimit`/`totalLimit`/`spent`/`nonce`는 `BigInteger`(`numeric(78,0)`). `status`는 `@Enumerated(EnumType.STRING)`. `revoke()`/`addSpent(BigInteger)` 메서드 포함.
- [ ] **Step 4: 실행 → 통과.**
- [ ] **Step 5: 커밋** — `feat(delegation): Mandate JPA entity + Flyway V3`

---

### Task 3: PolicyEngine (재사용 코어)

**Files:** Create `delegation/port/PolicyEngine.java`(+`PaymentContext`,`PolicyDecision`), `delegation/DefaultPolicyEngine.java`; Test `delegation/DefaultPolicyEngineTest.java`.

**Interfaces — Produces:**
- `record PaymentContext(String payee, BigInteger amount, String currency, Instant now)`
- `record PolicyDecision(boolean allowed, String reason)`
- `interface PolicyEngine { PolicyDecision evaluate(Mandate mandate, PaymentContext ctx); }`
- `@Service DefaultPolicyEngine implements PolicyEngine`

검사 순서(첫 실패 반환): status ACTIVE → now∈[validFrom,validUntil] → currency 일치 → allowAny||allowedPayees.contains(payee) → amount≤perTxLimit → spent+amount≤totalLimit. 통과 시 `PolicyDecision(true,"OK")`.

- [ ] **Step 1: 실패 테스트** — 각 규칙별 케이스:
```java
// 예시 골격 (AssertJ). Mandate는 테스트 헬퍼로 생성(builder 또는 생성자).
@Test void allows_whenAllConditionsMet(){ assertThat(engine.evaluate(m, ctx).allowed()).isTrue(); }
@Test void rejects_whenRevoked(){ ... status REVOKED → allowed=false, reason contains "REVOKED" }
@Test void rejects_whenExpired(){ ... now > validUntil }
@Test void rejects_whenCurrencyMismatch(){ ... }
@Test void rejects_whenPayeeNotAllowed(){ ... allowAny=false, payee 목록 밖 }
@Test void allows_whenAllowAny_regardlessOfPayee(){ ... }
@Test void rejects_whenAmountExceedsPerTxLimit(){ ... }
@Test void rejects_whenSpentPlusAmountExceedsTotal(){ ... spent=900,total=1000,amount=200 }
@Test void allows_atExactBoundaries(){ amount==perTxLimit, spent+amount==totalLimit → true }
```
- [ ] **Step 2: 실행 → 실패.**
- [ ] **Step 3: 구현** `DefaultPolicyEngine.evaluate`:
```java
public PolicyDecision evaluate(Mandate m, PaymentContext ctx){
  if (m.getStatus() != MandateStatus.ACTIVE) return new PolicyDecision(false, "mandate not ACTIVE: " + m.getStatus());
  long now = ctx.now().getEpochSecond();
  if (now < m.getValidFrom() || now > m.getValidUntil()) return new PolicyDecision(false, "mandate expired/not-yet-valid");
  if (!m.getCurrency().equals(ctx.currency())) return new PolicyDecision(false, "currency mismatch");
  if (!m.isAllowAnyPayee() && !m.getAllowedPayees().contains(ctx.payee())) return new PolicyDecision(false, "payee not allowed");
  if (ctx.amount().compareTo(m.getPerTxLimit()) > 0) return new PolicyDecision(false, "amount exceeds perTxLimit");
  if (m.getSpent().add(ctx.amount()).compareTo(m.getTotalLimit()) > 0) return new PolicyDecision(false, "exceeds totalLimit");
  return new PolicyDecision(true, "OK");
}
```
- [ ] **Step 4: 실행 → 통과** (경계 케이스 포함).
- [ ] **Step 5: 커밋** — `feat(delegation): PolicyEngine mandate evaluation`

---

### Task 4: MandateService (AP2) + UCAN stub

**Files:** Create `delegation/port/MandateService.java`, `delegation/Ap2MandateService.java`, `delegation/adapter/UcanMandateAdapter.java`; Test `delegation/Ap2MandateServiceTest.java`.

**Interfaces — Produces:**
- `record IssueMandateCommand(UUID userId, UUID agentId, String currency, BigInteger perTxLimit, BigInteger totalLimit, List<String> allowedPayees, boolean allowAny, long validFrom, long validUntil, BigInteger nonce, String userSignature)`
- `interface MandateService { Mandate issue(IssueMandateCommand cmd); Mandate get(UUID id); void revoke(UUID id); }`
- `@Service Ap2MandateService implements MandateService`
- `Consumes:` `Eip712Mandate`(Task 1), `UserRepository`/`AgentRepository`(user·agent 존재·주소 조회), `MandateRepository`, chainId(설정값 `agentpay.chain-id`, 기본 31337).

`issue` 로직: user·agent 존재 확인(없으면 `NotFoundException`) → `existsByUserIdAndNonce`면 `DataIntegrityViolation`/409 유발(또는 명시적 예외) → `MandateData`를 user·agent **주소**로 구성 → `Eip712Mandate.recoverSigner(...)` == user.address 아니면 `IllegalArgumentException`("서명 불일치") → Mandate(status ACTIVE, spent 0) 저장. `revoke`: 조회(없으면 NotFound) → `mandate.revoke()` 저장.

`UcanMandateAdapter implements MandateService`: 모든 메서드 `throw new UnsupportedOperationException("UCAN adapter not implemented")`. `@Component` 아님(또는 `@ConditionalOnProperty`로 비활성) — 기본은 Ap2가 주입되게. 존재만으로 포트 교체성 증명.

- [ ] **Step 1: 실패 테스트**(`@SpringBootTest @Import(TestcontainersConfiguration.class)`): user·agent 등록 → 유효 서명으로 issue→저장/ACTIVE; 잘못된 서명(다른 키)→ `IllegalArgumentException`; 같은 (user,nonce) 재발급→ 예외; revoke→ status REVOKED. 서명은 `Eip712Mandate.sign(...)`로 생성(테스트가 클라이언트 역할).
- [ ] **Step 2: 실행 → 실패.**
- [ ] **Step 3: 구현** (위 로직). chainId는 `@Value("${agentpay.chain-id:31337}")`.
- [ ] **Step 4: 실행 → 통과.**
- [ ] **Step 5: 커밋** — `feat(delegation): AP2 MandateService (EIP-712 verify) + UCAN stub`

---

### Task 5: 사용자 등록 (`POST /users`)

**Files:** Create `identity/UserRegistrationService.java`, `identity/web/UserController.java`; Test `identity/web/UserControllerTest.java`.

**Interfaces — Produces:**
- `UserRegistrationService.register(String publicKeyHex, String alias) -> User` (address 파생은 `Signatures.addressFromPublicKey`, publicKey 검증은 Phase 1 register와 동일 규칙 — `shared` 통해서만).
- `POST /users` {publicKey, alias} → 201 {id, address}. `RegisterUserRequest`에 `@NotBlank`+`@Pattern("^0x[0-9a-fA-F]{128}$")` publicKey, `@NotBlank` alias.

Phase 1 `AgentController`/`AgentRegistrationService` 패턴을 그대로 따른다(단 agent가 아닌 user, ownerUserId 없음).

- [ ] **Step 1: 실패 MockMvc 테스트** — 유효 publicKey로 POST /users → 201 + `$.address`; 잘못된 publicKey → 400. 실행 → FAIL.
- [ ] **Step 2: 구현.**
- [ ] **Step 3: 실행 → 통과.**
- [ ] **Step 4: 커밋** — `feat(identity): POST /users registration`

---

### Task 6: MandateController (REST)

**Files:** Create `delegation/web/MandateController.java`; Test `delegation/web/MandateControllerTest.java`.

**Produces (HTTP):**
- `POST /mandates` (body = IssueMandateCommand 필드) → 201 {id, status}
- `GET /mandates/{id}` → mandate 요약 JSON
- `GET /mandates?agentId=&userId=` → 목록
- `POST /mandates/{id}/revoke` → 200 {id, status:"REVOKED"}
- 검증 실패→400, 미존재→404, nonce중복→409 (기존 ApiExceptionHandler). `@Valid` on request.

- [ ] **Step 1: 실패 MockMvc 테스트**(`@Import(TestcontainersConfiguration.class)`): user·agent 등록(POST /users, POST /agents) → `Eip712Mandate.sign`로 서명 생성 → POST /mandates 201 → GET /mandates/{id} 필드 확인 → POST revoke → status REVOKED. 잘못된 서명 → 400. 미존재 revoke → 404. 실행 → FAIL.
- [ ] **Step 2: 구현** — 컨트롤러는 얇게, `MandateService`에 위임. 응답 DTO는 개인정보 없이 요약(id, userId, agentId, currency, limits, status, allowedPayees, validUntil).
- [ ] **Step 3: 실행 → 통과.**
- [ ] **Step 4: 커밋** — `feat(delegation): REST mandate issue/get/list/revoke`

---

### Task 7: ArchUnit — delegation 경계

**Files:** Modify `architecture/ModuleBoundaryTest.java`.

- [ ] **Step 1: 규칙 추가** — `..delegation..` → `org.web3j..` 금지(shared 통해서만); `..delegation.domain..` → `..delegation.web..` 금지; `..delegation.port..` 는 인터페이스. 실행 → 통과해야 함(위반 시 실제 누수 → 리팩터).
- [ ] **Step 2: 전체 스위트** `./gradlew :app:test` → 전부 green.
- [ ] **Step 3: 커밋** — `test(arch): delegation module boundary rules`

---

## Self-Review

**Spec 커버리지:** 사용자 등록(T5) · EIP-712 서명검증(T1,T4) · Mandate 저장(T2) · PolicyEngine 6규칙(T3) · issue/get/list/revoke(T4,T6) · UCAN stub(T4) · ArchUnit 경계(T7). ✓ 결제 실행·spent 증가는 명시적으로 범위 밖(Phase 4).

**Placeholder scan:** T1/T3 완전 코드. T2/T4/T5/T6는 정확한 필드·시그니처·SQL·테스트 케이스 명시 + Phase 1 동형 패턴 참조(엔티티/컨트롤러 boilerplate). "verify-at-task-time"은 web3j API 확인 항목(플레이스홀더 아님).

**타입 일관성:** `MandateData`(T1) ↔ `IssueMandateCommand`(T4) ↔ Mandate 컬럼(T2) ↔ PolicyEngine 필드(T3) — currency/perTxLimit/totalLimit/allowedPayees/allowAny/validFrom/validUntil/nonce 일치. `PolicyEngine.evaluate(Mandate, PaymentContext)` T3 정의, Phase 4가 소비. `Signatures.addressFromPublicKey`(Phase 1 하드닝에서 추가됨) T5 사용.

**주의:** EIP-712 검증의 핵심은 sign·verify가 **동일 `typedDataJson`** 을 쓰는 것(T1). 클라이언트(테스트)와 서버가 같은 유틸을 쓰므로 필드 순서/타입 자동 일치. 실 프론트엔드가 붙으면 JSON 형식을 스펙으로 고정해야 함(Phase 6/클라이언트 작업).

## Execution Handoff
계획을 `docs/superpowers/plans/2026-07-08-phase2-delegation.md`에 저장. subagent-driven-development로 T1→T7 실행(태스크당 구현→리뷰→수정), 완료 후 whole-branch 리뷰 → main 머지.
