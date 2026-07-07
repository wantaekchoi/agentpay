# CLAUDE.md

`agentpay` — AI 에이전트 결제 플랫폼 MVP(씨앗). 사용자가 에이전트를 등록·예치금 위임하면 에이전트가 대신 탐색·결제. 성격: **제품 씨앗 · 표준 쇼케이스 · walking skeleton=결제·위임 코어**. 상세는 `docs/superpowers/specs/2026-07-07-agent-payment-mvp-design.md`.

## 빌드·검증 (JDK 25 / Boot 4.1 / Gradle 9.6.1)
- 전체 테스트: `./gradlew :app:test`  (단일: `--tests "*SignaturesTest"`)
- 빌드: `./gradlew :app:build`
- **Gradle 데몬·컴파일 모두 JDK 25(LTS) 고정** (`gradle/gradle-daemon-jvm.properties`, `app/build.gradle.kts` toolchain). 비-LTS JDK 26이 PATH 기본이어도 무관.
- 통합테스트는 **Testcontainers(Docker 필요)** — Postgres를 자동 기동. Docker 데몬이 떠 있어야 함.
- 검증 순서: 빌드 → `:app:test`(단위+통합) → ArchUnit 경계(`*ModuleBoundaryTest`).

## 아키텍처 요점
- **헥사고날 코어 모놀리스**. 도메인 코어는 포트(인터페이스)로만 외부와 대화, 표준은 어댑터 교체로 수용.
- 모듈: `shared/`(crypto·did 유틸), `identity/domain`(JPA), `identity/port`(포트), `identity`(서비스), `identity/web`(REST). (향후 `custody`·`delegation`·`payment`·`commerce`·`audit` + 별도 서비스 `commerce-mock`·`evm-gateway`·`agent-cli`·`agentpay-client`.)
- 표준 매트릭스(포트별 실구현+후보): did:key/did:web · A2A AgentCard · AP2 · x402 · ACP+schema.org · VC 영수증. (spec 5장)

## 불변 제약 (YOU MUST)
- **개인키를 DB에 저장하거나 로그에 남기지 않는다.** Agent는 publicKey·address·did만 저장. `KeyPair.toString()`은 privateKey 마스킹.
- **`identity`는 web3j 타입을 직접 참조하지 않는다** — 서명·주소·DID는 `shared`(`Signatures`·`DidKey`) 통해서만. **ArchUnit `*ModuleBoundaryTest`가 `..identity..`→`org.web3j..` 금지를 강제**(위반 시 빌드 실패).
- 공개 API URL은 **무버전 리소스**(`/agents`, `/mandates`…). `/v1` 프리픽스 금지; breaking change는 헤더 버전(날짜).

## Spring Boot 4.x 버전 좌표 (3.x → 4.x에서 바뀐 것)
Boot 4.1은 사내 badge 서버들도 쓰는 표준 베이스라인. 아래는 이 프로젝트 셋업에서 확인한, 3.x와 좌표/기본값이 다른 지점들:
- 웹 스타터는 `spring-boot-starter-webmvc` 사용.
- Flyway autoconfig는 `spring-boot-starter-flyway` 의존성이 있어야 실행(없으면 마이그레이션 skip → 빈 스키마 검증 실패).
- Testcontainers `2.0.5`(Boot BOM 관리): 아티팩트 `org.testcontainers:testcontainers-postgresql`, 클래스 `org.testcontainers.postgresql.PostgreSQLContainer`(**비제네릭**).
- Jackson 3 기본: classic `com.fasterxml.jackson` ObjectMapper 자동빈 없음. `@AutoConfigureMockMvc`는 `spring-boot-webmvc-test` 아티팩트.
- ArchUnit `1.4.2+` (구버전 번들 ASM은 Java 25 바이트코드 major 69를 못 읽음).
- (도구) start.spring.io가 4.1-kotlin 생성 시 500 — Initializr 대신 로컬 gradle로 부트스트랩.

## 테스트 규약
- 통합테스트는 공유 `TestcontainersConfiguration`(`@TestConfiguration` + `@Bean @ServiceConnection PostgreSQLContainer`)을 `@Import`로 재사용(컨테이너 재선언 금지).
- 실동작 검증(실제 키페어·서명·실 Postgres), mock 지양. explicit import(와일드카드 금지), pristine 출력.

## Git
- 리모트: `git@github.com:wantaekchoi/agentpay.git` (SSH). 커밋마다 push.
- 문서 동기화: 작업 종료 시 `docs/CURRENT_STATE.md`·`docs/NEXT_STEP.md` 갱신, 결정은 spec 17장(ADR 씨앗).
