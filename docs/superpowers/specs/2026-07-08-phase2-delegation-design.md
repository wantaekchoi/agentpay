# Phase 2: 위임 (AP2 Intent Mandate) — 설계 스펙

- 날짜: 2026-07-08
- 상태: 설계 확정 (구현 계획 대기)
- 선행: Phase 1(Foundation & Identity) 완료. 전체 설계는 `2026-07-07-agent-payment-mvp-design.md` 9장.

## 1. 목적 & Walking Skeleton

이 제품의 핵심 — **사용자가 에이전트에게 결제 권한을 서명으로 위임**하는 계층. Phase 2가 진짜로 관통시키는 줄기:

1. 사용자 등록(공개키) — mandate 발급자가 키페어로 존재
2. 사용자가 **EIP-712로 intent mandate 서명** (한도·기간·허용 payee·통화)
3. `POST /mandates` — 사용자 서명 검증 후 저장
4. **`PolicyEngine`이 (payee, amount, currency)를 mandate에 대해 평가** — 순수 함수, 결제 플로우 없이 단위테스트
5. `POST /mandates/{id}/revoke` — 권한 회수

**실제 결제 실행·spent 증가는 Phase 4.** Phase 2는 위임의 발급·검증·정책평가·회수까지.

## 2. 확정된 설계 결정

| 결정 | 값 | 근거 |
|---|---|---|
| mandate 모델 | **Intent 단일 mandate** (Cart 미포함) | 자율 에이전트(human-not-present) 검증이 MVP 목표. Cart는 후보로 포트 뒤 |
| 허용 대상 | **payee 주소 목록 + allowAny 플래그** | commerce 상품 payeeAddress와 직접 매칭, PolicyEngine 명확 |
| 서명 | **EIP-712 typed data** (Phase 1 EIP-191 → 승격) | 구조화·표준·AP2/x402 결. secp256k1 재사용 |
| 사용자 등록 | **`POST /users` 추가** (에이전트와 대칭) | 발급자가 키페어로 존재해야 함 |
| revoke 인증 | **MVP 단순 상태변경** (prod 서명 revoke는 TODO) | revoke는 권한 축소 방향이라 저위험 |
| 표준 stub | **UCAN 어댑터 1개** (`MandateService` 뒤, UnsupportedOperationException) | 최소1(AP2)+후보 규칙, 포트 교체성 증명 |

## 3. 도메인 모델 (`delegation` 모듈, core_db)

`Mandate` (JPA):

| 필드 | 타입 | 비고 |
|---|---|---|
| id | UUID | |
| userId | UUID | 발급자 (identity.User) |
| agentId | UUID | 수임 에이전트 (identity.Agent) |
| currency | String | 예: "USDC" (토큰 심볼/주소) |
| perTxLimit | BigInteger(numeric) | 건당 한도 |
| totalLimit | BigInteger | 누적 한도 |
| spent | BigInteger | 기본 0 (Phase 4에서 증가) |
| allowedPayees | String[] (또는 별도 테이블) | 허용 payee 주소 |
| allowAnyPayee | boolean | true면 payee 무제한 |
| validFrom / validUntil | timestamp | 유효 기간 |
| nonce | BigInteger | 사용자별 유니크, 리플레이 방지 |
| userSignature | String | EIP-712 서명(hex) |
| status | String | ACTIVE / REVOKED / EXPIRED |
| createdAt | timestamp | |

- Flyway `V3__mandates.sql`. allowedPayees는 정규화 테이블(`mandate_allowed_payees`) 또는 Postgres `text[]`; MVP는 조인 테이블 권장(검증 쉬움).
- 유니크: `(userId, nonce)` — 리플레이 방지.
- `status`는 조회 시점에 `validUntil` 지났으면 EXPIRED로 간주(저장값과 별개로 PolicyEngine이 시간 검사).

## 4. EIP-712 Mandate & 서명 검증

사용자가 서명하는 typed struct:

```
domain: { name: "agentpay", version: "1", chainId: <cfg> }
AgentPaymentMandate {
  address user; address agent;
  string currency;
  uint256 perTxLimit; uint256 totalLimit;
  address[] allowedPayees; bool allowAny;
  uint256 validFrom; uint256 validUntil; uint256 nonce;
}
```

- **검증**: EIP-712 다이제스트 계산 → 서명에서 서명자 주소 복구 → 사용자 등록 주소와 일치 확인.
- **구현 위치**: `shared/crypto`에 EIP-712 유틸 추가(web3j `StructuredDataEncoder`로 `hashStructuredData()`, `Sign.signedMessageHashToKey(hash, sig)`로 복구). `delegation`/`identity`는 web3j 직접참조 안 함 — `shared`의 유틸만 사용.
- Phase 1의 `Signatures`(EIP-191)와 별개 경로. mandate/payment는 EIP-712.

> **검증 필요(plan 시점)**: web3j `StructuredDataEncoder`의 정확한 API(JSON 입력 형식, `hashStructuredData()` 반환), AP2 공개 스펙의 실제 mandate 필드/구조(우리 struct를 AP2 형태에 최대한 맞춤).

## 5. PolicyEngine (재사용 코어)

```
PolicyDecision evaluate(Mandate m, PaymentContext ctx)
  PaymentContext { String payee; BigInteger amount; String currency; Instant now; }
  PolicyDecision { boolean allowed; String reason; }
```

검사 (하나라도 실패 시 `allowed=false` + reason):
1. `m.status == ACTIVE`
2. `ctx.now ∈ [validFrom, validUntil]` (아니면 EXPIRED 사유)
3. `ctx.currency == m.currency`
4. `m.allowAnyPayee || m.allowedPayees.contains(ctx.payee)`
5. `ctx.amount ≤ m.perTxLimit`
6. `m.spent + ctx.amount ≤ m.totalLimit`

순수 함수 — DB·web 의존 없음. Phase 2에서 완전 단위테스트, Phase 4 결제 승인이 호출.

## 6. API (무버전 리소스 URL)

| 메서드 | 경로 | 설명 |
|---|---|---|
| `POST` | `/users` | 사용자 등록 (publicKey) → `{id, address}` |
| `POST` | `/mandates` | mandate 발급(서명 검증) → `{id, status}` |
| `GET` | `/mandates/{id}` | 조회 |
| `GET` | `/mandates?agentId=&userId=` | 목록 |
| `POST` | `/mandates/{id}/revoke` | 회수 → status REVOKED |

- 에러: Phase 1 `ApiExceptionHandler` 재사용 — 서명 불일치/잘못된 입력→400, 미존재→404, nonce 중복→409.
- `POST /mandates` 본문: mandate 필드 + `userSignature`. 서버가 필드로 EIP-712 다이제스트 재구성 → 서명 검증.

## 7. 모듈 / 패키지 구조

```
identity/web/UserController          # POST /users (신규; 등록은 identity 소관)
identity/UserRegistrationService     # 신규
delegation/domain/{Mandate, MandateRepository}
delegation/port/{MandateService, PolicyEngine}   # 인터페이스
delegation/MandateServiceImpl (AP2)  # EIP-712 검증·저장
delegation/DefaultPolicyEngine
delegation/adapter/UcanMandateAdapter  # 후보 stub (UnsupportedOperationException)
delegation/web/MandateController
shared/crypto/TypedData (or Signatures 확장)  # EIP-712 유틸
db/migration/V3__mandates.sql
```

- **ArchUnit 확장**: `..delegation..` → `org.web3j..` 금지(shared 통해서만). ports(`delegation.port`)는 인터페이스.

## 8. 표준 쇼케이스

- 실구현: **AP2 Intent Mandate** (EIP-712 서명 위임).
- 후보 stub: `UcanMandateAdapter implements MandateService` — 존재만 증명(호출 시 `UnsupportedOperationException("UCAN adapter not implemented")`). 포트가 표준 교체 지점임을 데모.

## 9. 테스트 전략

- **PolicyEngine 단위테스트**: 6개 규칙 각각의 통과/거부 조합(한도 경계, 만료, payee 불허, 통화 불일치, 상태 REVOKED).
- **MandateService 통합**(Testcontainers): 유효 서명 발급→저장→조회, 잘못된 서명→거부, nonce 중복→409, revoke→REVOKED.
- **EIP-712 유틸 단위테스트**: 사용자 키로 서명→복구 주소 == 사용자 주소 (라운드트립), 변조 시 불일치.
- **MandateController**(MockMvc): 발급 201/검증실패 400/미존재 404/nonce중복 409/revoke.
- ArchUnit: delegation 경계.

## 10. 범위 밖 (다음 Phase)

- 결제 실행·`spent` 증가 (Phase 4)
- Cart mandate (human-present 승인) — 후보
- 서명 기반 revoke, 카테고리 조건 payee — 백로그
- 온체인 앵커링/커스터디 — Phase 3+

## 11. 다음 단계

1. 이 스펙 검토(사용자)
2. `writing-plans`로 Phase 2 구현 계획 → subagent-driven 실행
3. 착수 시 CURRENT_STATE/NEXT_STEP 갱신
