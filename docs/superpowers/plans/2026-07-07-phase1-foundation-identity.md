# Phase 1: Foundation & Identity — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 부팅 가능한 Spring Boot 4.1 씨앗 프로젝트 위에서, 에이전트를 secp256k1 공개키로 등록하고 그 에이전트가 서명한 챌린지를 검증하며, A2A AgentCard와 did:web 문서를 노출한다.

**Architecture:** 헥사고날 코어 모놀리스(`:app`) 단일 모듈로 시작한다. `identity` 패키지가 도메인(User·Agent) + 포트(`AgentIdentity`, `AgentDirectory`)를 소유하고, 서명·DID 유틸은 `shared` 패키지에 둔다. 모듈 경계는 ArchUnit 테스트로 강제한다. 영속성은 Postgres + Flyway, 테스트는 Testcontainers.

**Tech Stack:** Java 25, Spring Boot 4.1.x, Gradle (Kotlin DSL), Spring Data JPA, Flyway, PostgreSQL, web3j (crypto), ArchUnit, Testcontainers, JUnit 5.

## Global Constraints

- 베이스 패키지: `io.github.wantaekchoi.agentpay` (GitHub `wantaekchoi/agentpay`).
- 서명 커브: **secp256k1** (EVM 키와 동일). 서명 포맷은 EIP-191 personal_sign(`Sign.signPrefixedMessage`)을 Phase 1에서 사용, EIP-712는 Phase 2+.
- 도메인 코어(`identity`)는 web3j 타입을 직접 참조하지 않는다 — 서명·DID는 `shared`의 유틸/포트를 통해서만.
- append-only·감사 관련 없음(Phase 1). 개인키는 절대 커밋·로그·DB에 저장하지 않는다. Agent에는 **공개키·주소·DID만** 저장.
- **verify-at-task-time (Task 1에서 확정):** Spring Boot 4.x는 `spring-boot-starter-web`을 **`spring-boot-starter-webmvc`** 로 리네임했다. 정확한 4.1.x 스타터 아티팩트 좌표(webmvc·test 스타터), `io.spring.dependency-management` 플러그인 버전, web3j 최신 안정 버전은 Task 1에서 `./gradlew dependencies` 또는 공식 릴리스노트로 확정한다. 아래 build 파일의 버전은 시작점이다.

---

## File Structure

```
settings.gradle.kts
build.gradle.kts
gradle/wrapper/…                     (gradle wrapper)
compose.yml                          (postgres)
app/build.gradle.kts
app/src/main/java/io/github/wantaekchoi/agentpay/
    AgentpayApplication.java
    shared/
        crypto/Signatures.java       (web3j 서명·복구 래퍼)
        crypto/Base58.java           (base58btc 인코더)
        did/DidKey.java              (secp256k1 pubkey → did:key)
    identity/
        domain/User.java
        domain/Agent.java
        domain/AgentRepository.java
        domain/UserRepository.java
        port/AgentIdentity.java      (포트)
        port/AgentDirectory.java     (포트)
        AgentCard.java               (A2A AgentCard 모델)
        AgentRegistrationService.java
        AgentIdentityService.java    (AgentIdentity 구현)
        AgentDirectoryService.java   (AgentDirectory 구현)
        web/AgentController.java
        web/DidWebController.java
app/src/main/resources/
    application.yml
    db/migration/V1__identity.sql
app/src/test/java/io/github/wantaekchoi/agentpay/
    AgentpayApplicationTests.java
    shared/crypto/SignaturesTest.java
    shared/crypto/Base58Test.java
    shared/did/DidKeyTest.java
    identity/IdentityPersistenceTest.java
    identity/AgentIdentityServiceTest.java
    identity/AgentDirectoryServiceTest.java
    identity/web/AgentControllerTest.java
    architecture/ModuleBoundaryTest.java
```

---

### Task 1: 프로젝트 부팅 (Gradle · Spring Boot 4.1 · Java 25)

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `app/build.gradle.kts`
- Create: `app/src/main/java/io/github/wantaekchoi/agentpay/AgentpayApplication.java`
- Create: `app/src/main/resources/application.yml`
- Create: `compose.yml`
- Test: `app/src/test/java/io/github/wantaekchoi/agentpay/AgentpayApplicationTests.java`

**Interfaces:**
- Produces: 부팅 가능한 `AgentpayApplication`, Gradle 모듈 `:app`.

- [ ] **Step 1: Gradle wrapper 생성**

Run: `gradle wrapper --gradle-version 8.14` (없으면 시스템 gradle 사용; 목표는 `./gradlew` 동작)
Expected: `gradlew`, `gradle/wrapper/gradle-wrapper.properties` 생성.

- [ ] **Step 2: settings + build 파일 작성**

`settings.gradle.kts`:
```kotlin
rootProject.name = "agentpay"
include("app")
```

`build.gradle.kts` (루트, 빈 골격):
```kotlin
// 모듈별 build 파일에서 플러그인 적용. 루트는 공통 설정 자리.
allprojects {
    group = "io.github.wantaekchoi"
    version = "0.1.0-SNAPSHOT"
}
```

`app/build.gradle.kts`:
```kotlin
plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

java {
    toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

repositories { mavenCentral() }

dependencies {
    // NOTE(verify): Boot 4.x 스타터명 확정 — web → webmvc
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.test { useJUnitPlatform() }
```

- [ ] **Step 3: 메인 클래스 + 설정 작성**

`AgentpayApplication.java`:
```java
package io.github.wantaekchoi.agentpay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AgentpayApplication {
    public static void main(String[] args) {
        SpringApplication.run(AgentpayApplication.class, args);
    }
}
```

`application.yml`:
```yaml
spring:
  application:
    name: agentpay-core
management:
  endpoints:
    web:
      exposure:
        include: health
```

`compose.yml` (Task 4에서 사용할 postgres 선반영):
```yaml
services:
  postgres:
    image: postgres:17
    environment:
      POSTGRES_DB: core_db
      POSTGRES_USER: agentpay
      POSTGRES_PASSWORD: agentpay
    ports: ["5432:5432"]
```

- [ ] **Step 4: 실패하는 컨텍스트 로드 테스트 작성**

`AgentpayApplicationTests.java`:
```java
package io.github.wantaekchoi.agentpay;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class AgentpayApplicationTests {
    @Test
    void contextLoads() {
    }
}
```

- [ ] **Step 5: 테스트 실행 → 통과 확인**

Run: `./gradlew :app:test`
Expected: PASS (컨텍스트 로드 성공). 스타터 좌표/버전 문제로 FAIL 시 Global Constraints의 verify 항목대로 좌표를 교정 후 재실행.

- [ ] **Step 6: 커밋**

```bash
git add settings.gradle.kts build.gradle.kts app/build.gradle.kts app/src compose.yml gradlew gradle/
git commit -m "feat: bootstrap Spring Boot 4.1 / Java 25 skeleton (:app)"
```

---

### Task 2: 서명 유틸 (`Signatures`) — secp256k1 sign/verify

**Files:**
- Modify: `app/build.gradle.kts` (web3j crypto 의존성 추가)
- Create: `app/src/main/java/io/github/wantaekchoi/agentpay/shared/crypto/Signatures.java`
- Test: `app/src/test/java/io/github/wantaekchoi/agentpay/shared/crypto/SignaturesTest.java`

**Interfaces:**
- Produces:
  - `Signatures.KeyPair generateKeyPair()` — `{ BigInteger privateKey; BigInteger publicKey; String address; }`
  - `String sign(BigInteger privateKey, String message)` — hex 서명(r||s||v)
  - `String recoverAddress(String message, String signatureHex)` — 서명에서 복구한 EVM 주소(0x…, 소문자)

- [ ] **Step 1: web3j 의존성 추가**

`app/build.gradle.kts`의 `dependencies`에 추가 (버전은 verify):
```kotlin
    implementation("org.web3j:crypto:4.12.3")
```

- [ ] **Step 2: 실패하는 테스트 작성**

`SignaturesTest.java`:
```java
package io.github.wantaekchoi.agentpay.shared.crypto;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SignaturesTest {

    @Test
    void signedMessage_recoversToSignerAddress() {
        var kp = Signatures.generateKeyPair();
        String message = "challenge-abc-123";

        String sig = Signatures.sign(kp.privateKey(), message);
        String recovered = Signatures.recoverAddress(message, sig);

        assertThat(recovered).isEqualTo(kp.address());
    }

    @Test
    void tamperedMessage_recoversToDifferentAddress() {
        var kp = Signatures.generateKeyPair();
        String sig = Signatures.sign(kp.privateKey(), "original");

        String recovered = Signatures.recoverAddress("tampered", sig);

        assertThat(recovered).isNotEqualTo(kp.address());
    }
}
```

- [ ] **Step 3: 테스트 실행 → 실패 확인**

Run: `./gradlew :app:test --tests "*SignaturesTest"`
Expected: FAIL — `Signatures` 미존재(compile error).

- [ ] **Step 4: 최소 구현 작성**

`Signatures.java`:
```java
package io.github.wantaekchoi.agentpay.shared.crypto;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

public final class Signatures {
    private Signatures() {}

    public record KeyPair(BigInteger privateKey, BigInteger publicKey, String address) {}

    public static KeyPair generateKeyPair() {
        try {
            ECKeyPair kp = Keys.createEcKeyPair();
            String address = Numeric.prependHexPrefix(Keys.getAddress(kp.getPublicKey()));
            return new KeyPair(kp.getPrivateKey(), kp.getPublicKey(), address.toLowerCase());
        } catch (Exception e) {
            throw new IllegalStateException("keypair 생성 실패", e);
        }
    }

    public static String sign(BigInteger privateKey, String message) {
        ECKeyPair kp = ECKeyPair.create(privateKey);
        Sign.SignatureData sd = Sign.signPrefixedMessage(
                message.getBytes(StandardCharsets.UTF_8), kp);
        byte[] out = new byte[65];
        System.arraycopy(sd.getR(), 0, out, 0, 32);
        System.arraycopy(sd.getS(), 0, out, 32, 32);
        out[64] = sd.getV()[0];
        return Numeric.toHexString(out);
    }

    public static String recoverAddress(String message, String signatureHex) {
        byte[] sig = Numeric.hexStringToByteArray(signatureHex);
        byte[] r = new byte[32];
        byte[] s = new byte[32];
        System.arraycopy(sig, 0, r, 0, 32);
        System.arraycopy(sig, 32, s, 0, 32);
        byte v = sig[64];
        Sign.SignatureData sd = new Sign.SignatureData(v, r, s);
        try {
            BigInteger pubKey = Sign.signedPrefixedMessageToKey(
                    message.getBytes(StandardCharsets.UTF_8), sd);
            return Numeric.prependHexPrefix(Keys.getAddress(pubKey)).toLowerCase();
        } catch (Exception e) {
            throw new IllegalArgumentException("서명 복구 실패", e);
        }
    }
}
```

- [ ] **Step 5: 테스트 실행 → 통과 확인**

Run: `./gradlew :app:test --tests "*SignaturesTest"`
Expected: PASS (2 tests).

- [ ] **Step 6: 커밋**

```bash
git add app/build.gradle.kts app/src/main/java/io/github/wantaekchoi/agentpay/shared/crypto/Signatures.java app/src/test/java/io/github/wantaekchoi/agentpay/shared/crypto/SignaturesTest.java
git commit -m "feat(shared): secp256k1 sign/recover via web3j"
```

---

### Task 3: `Base58` + `DidKey` — did:key 인코딩

**Files:**
- Create: `app/src/main/java/io/github/wantaekchoi/agentpay/shared/crypto/Base58.java`
- Create: `app/src/main/java/io/github/wantaekchoi/agentpay/shared/did/DidKey.java`
- Test: `app/src/test/java/io/github/wantaekchoi/agentpay/shared/crypto/Base58Test.java`
- Test: `app/src/test/java/io/github/wantaekchoi/agentpay/shared/did/DidKeyTest.java`

**Interfaces:**
- Produces:
  - `String Base58.encode(byte[] input)`
  - `String DidKey.encode(BigInteger publicKey)` — `did:key:z…` (secp256k1 multicodec 0xe7 + base58btc)

- [ ] **Step 1: 실패하는 Base58 테스트 작성**

`Base58Test.java` (알려진 벡터):
```java
package io.github.wantaekchoi.agentpay.shared.crypto;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.assertThat;

class Base58Test {
    @Test
    void encodesKnownVector() {
        assertThat(Base58.encode("Hello World!".getBytes(StandardCharsets.UTF_8)))
                .isEqualTo("2NEpo7TZRRrLZSi2U");
    }

    @Test
    void leadingZeroBytesBecomeLeadingOnes() {
        assertThat(Base58.encode(new byte[]{0, 0, 1})).startsWith("11");
    }
}
```

- [ ] **Step 2: 실행 → 실패 확인**

Run: `./gradlew :app:test --tests "*Base58Test"`
Expected: FAIL — `Base58` 미존재.

- [ ] **Step 3: `Base58` 구현**

`Base58.java`:
```java
package io.github.wantaekchoi.agentpay.shared.crypto;

import java.math.BigInteger;

public final class Base58 {
    private static final String ALPHABET =
            "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final BigInteger BASE = BigInteger.valueOf(58);

    private Base58() {}

    public static String encode(byte[] input) {
        if (input.length == 0) return "";
        int zeros = 0;
        while (zeros < input.length && input[zeros] == 0) zeros++;

        BigInteger num = new BigInteger(1, input);
        StringBuilder sb = new StringBuilder();
        while (num.signum() > 0) {
            BigInteger[] dr = num.divideAndRemainder(BASE);
            num = dr[0];
            sb.append(ALPHABET.charAt(dr[1].intValue()));
        }
        for (int i = 0; i < zeros; i++) sb.append(ALPHABET.charAt(0));
        return sb.reverse().toString();
    }
}
```

- [ ] **Step 4: 실행 → 통과 확인**

Run: `./gradlew :app:test --tests "*Base58Test"`
Expected: PASS (2 tests).

- [ ] **Step 5: 실패하는 DidKey 테스트 작성**

`DidKeyTest.java`:
```java
package io.github.wantaekchoi.agentpay.shared.did;

import io.github.wantaekchoi.agentpay.shared.crypto.Signatures;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DidKeyTest {
    @Test
    void encodesSecp256k1PublicKeyAsDidKey() {
        var kp = Signatures.generateKeyPair();
        String did = DidKey.encode(kp.publicKey());

        assertThat(did).startsWith("did:key:z");
        // secp256k1 did:key(멀티코덱 0xe7 + 33바이트 압축키)는 base58btc에서 'zQ3s'로 시작한다.
        assertThat(did).startsWith("did:key:zQ3s");
    }

    @Test
    void isDeterministicForSameKey() {
        var kp = Signatures.generateKeyPair();
        assertThat(DidKey.encode(kp.publicKey())).isEqualTo(DidKey.encode(kp.publicKey()));
    }
}
```

- [ ] **Step 6: 실행 → 실패 확인**

Run: `./gradlew :app:test --tests "*DidKeyTest"`
Expected: FAIL — `DidKey` 미존재.

- [ ] **Step 7: `DidKey` 구현**

`DidKey.java`:
```java
package io.github.wantaekchoi.agentpay.shared.did;

import io.github.wantaekchoi.agentpay.shared.crypto.Base58;
import java.math.BigInteger;
import java.util.Arrays;
import org.web3j.utils.Numeric;

public final class DidKey {
    // secp256k1-pub 멀티코덱 0xe7 의 unsigned-varint 인코딩
    private static final byte[] MULTICODEC_SECP256K1 = {(byte) 0xe7, (byte) 0x01};

    private DidKey() {}

    public static String encode(BigInteger publicKey) {
        byte[] compressed = compress(publicKey);
        byte[] prefixed = new byte[MULTICODEC_SECP256K1.length + compressed.length];
        System.arraycopy(MULTICODEC_SECP256K1, 0, prefixed, 0, MULTICODEC_SECP256K1.length);
        System.arraycopy(compressed, 0, prefixed, MULTICODEC_SECP256K1.length, compressed.length);
        return "did:key:z" + Base58.encode(prefixed);
    }

    // web3j 공개키(64바이트 X||Y 비압축)를 33바이트 압축키로.
    static byte[] compress(BigInteger publicKey) {
        byte[] full = Numeric.toBytesPadded(publicKey, 64);
        byte[] x = Arrays.copyOfRange(full, 0, 32);
        byte yParity = (byte) (((full[63] & 1) == 0) ? 0x02 : 0x03);
        byte[] out = new byte[33];
        out[0] = yParity;
        System.arraycopy(x, 0, out, 1, 32);
        return out;
    }
}
```

- [ ] **Step 8: 실행 → 통과 확인**

Run: `./gradlew :app:test --tests "*DidKeyTest"`
Expected: PASS (2 tests). `zQ3s` 접두 어서션 실패 시 압축키 prefix/멀티코덱 바이트를 점검.

- [ ] **Step 9: 커밋**

```bash
git add app/src/main/java/io/github/wantaekchoi/agentpay/shared/crypto/Base58.java app/src/main/java/io/github/wantaekchoi/agentpay/shared/did/DidKey.java app/src/test/java/io/github/wantaekchoi/agentpay/shared/crypto/Base58Test.java app/src/test/java/io/github/wantaekchoi/agentpay/shared/did/DidKeyTest.java
git commit -m "feat(shared): base58 + did:key encoding for secp256k1"
```

---

### Task 4: identity 영속성 (JPA · Flyway · Testcontainers)

**Files:**
- Modify: `app/build.gradle.kts` (jpa, flyway, postgres, testcontainers)
- Create: `app/src/main/java/io/github/wantaekchoi/agentpay/identity/domain/User.java`
- Create: `app/src/main/java/io/github/wantaekchoi/agentpay/identity/domain/Agent.java`
- Create: `.../identity/domain/UserRepository.java`, `.../identity/domain/AgentRepository.java`
- Create: `app/src/main/resources/db/migration/V1__identity.sql`
- Modify: `app/src/main/resources/application.yml`
- Test: `app/src/test/java/io/github/wantaekchoi/agentpay/identity/IdentityPersistenceTest.java`

**Interfaces:**
- Produces:
  - JPA `User { UUID id; String alias; String publicKey; String address; }`
  - JPA `Agent { UUID id; UUID ownerUserId; String publicKey; String address; String did; String alias; String status; }`
  - `AgentRepository extends JpaRepository<Agent, UUID>`; `UserRepository extends JpaRepository<User, UUID>`

- [ ] **Step 1: 의존성 추가**

`app/build.gradle.kts` `dependencies`:
```kotlin
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.testcontainers:postgresql")
```

- [ ] **Step 2: application.yml에 datasource/flyway 추가**

```yaml
spring:
  application:
    name: agentpay-core
  datasource:
    url: jdbc:postgresql://localhost:5432/core_db
    username: agentpay
    password: agentpay
  jpa:
    hibernate:
      ddl-auto: validate
    open-in-view: false
  flyway:
    enabled: true
management:
  endpoints:
    web:
      exposure:
        include: health
```

- [ ] **Step 3: Flyway 마이그레이션 작성**

`V1__identity.sql`:
```sql
create table users (
    id         uuid primary key,
    alias      varchar(100) not null,
    public_key varchar(200) not null,
    address    varchar(64)  not null unique
);

create table agents (
    id            uuid primary key,
    owner_user_id uuid         not null references users(id),
    public_key    varchar(200) not null,
    address       varchar(64)  not null unique,
    did           varchar(200) not null,
    alias         varchar(100) not null,
    status        varchar(20)  not null
);
```

- [ ] **Step 4: 실패하는 영속성 테스트 작성**

`IdentityPersistenceTest.java`:
```java
package io.github.wantaekchoi.agentpay.identity;

import io.github.wantaekchoi.agentpay.identity.domain.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class IdentityPersistenceTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired UserRepository users;
    @Autowired AgentRepository agents;

    @Test
    void persistsUserAndAgent() {
        User u = new User(UUID.randomUUID(), "alice", "0xpub", "0xuseraddr");
        users.save(u);

        Agent a = new Agent(UUID.randomUUID(), u.getId(),
                "0xagentpub", "0xagentaddr", "did:key:zQ3sTest", "shopper", "ACTIVE");
        agents.save(a);

        assertThat(agents.findById(a.getId())).isPresent()
                .get().extracting(Agent::getOwnerUserId).isEqualTo(u.getId());
    }
}
```

- [ ] **Step 5: 실행 → 실패 확인**

Run: `./gradlew :app:test --tests "*IdentityPersistenceTest"`
Expected: FAIL — `User`/`Agent`/repository 미존재.

- [ ] **Step 6: 엔티티 + 리포지토리 구현**

`User.java`:
```java
package io.github.wantaekchoi.agentpay.identity.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
    @Id private UUID id;
    private String alias;
    @Column(name = "public_key") private String publicKey;
    private String address;

    protected User() {}
    public User(UUID id, String alias, String publicKey, String address) {
        this.id = id; this.alias = alias; this.publicKey = publicKey; this.address = address;
    }
    public UUID getId() { return id; }
    public String getAlias() { return alias; }
    public String getPublicKey() { return publicKey; }
    public String getAddress() { return address; }
}
```

`Agent.java`:
```java
package io.github.wantaekchoi.agentpay.identity.domain;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "agents")
public class Agent {
    @Id private UUID id;
    @Column(name = "owner_user_id") private UUID ownerUserId;
    @Column(name = "public_key") private String publicKey;
    private String address;
    private String did;
    private String alias;
    private String status;

    protected Agent() {}
    public Agent(UUID id, UUID ownerUserId, String publicKey, String address,
                 String did, String alias, String status) {
        this.id = id; this.ownerUserId = ownerUserId; this.publicKey = publicKey;
        this.address = address; this.did = did; this.alias = alias; this.status = status;
    }
    public UUID getId() { return id; }
    public UUID getOwnerUserId() { return ownerUserId; }
    public String getPublicKey() { return publicKey; }
    public String getAddress() { return address; }
    public String getDid() { return did; }
    public String getAlias() { return alias; }
    public String getStatus() { return status; }
}
```

`UserRepository.java` / `AgentRepository.java`:
```java
package io.github.wantaekchoi.agentpay.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID> {}
```
```java
package io.github.wantaekchoi.agentpay.identity.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface AgentRepository extends JpaRepository<Agent, UUID> {}
```

- [ ] **Step 7: 실행 → 통과 확인**

Run: `./gradlew :app:test --tests "*IdentityPersistenceTest"`
Expected: PASS. (Docker 필요 — Testcontainers가 postgres:17 기동)

- [ ] **Step 8: 커밋**

```bash
git add app/build.gradle.kts app/src/main/resources app/src/main/java/io/github/wantaekchoi/agentpay/identity/domain app/src/test/java/io/github/wantaekchoi/agentpay/identity/IdentityPersistenceTest.java
git commit -m "feat(identity): User/Agent JPA entities + Flyway V1"
```

---

### Task 5: `AgentIdentity` 포트 + 등록/검증 서비스

**Files:**
- Create: `.../identity/port/AgentIdentity.java`
- Create: `.../identity/AgentRegistrationService.java`
- Create: `.../identity/AgentIdentityService.java`
- Test: `.../identity/AgentIdentityServiceTest.java`

**Interfaces:**
- Consumes: `Signatures.recoverAddress`, `DidKey.encode`, `AgentRepository`, `UserRepository`.
- Produces:
  - `interface AgentIdentity { boolean verifyChallenge(UUID agentId, String message, String signatureHex); }`
  - `AgentRegistrationService.register(UUID ownerUserId, String publicKeyHex, String alias) -> Agent` (publicKeyHex는 0x-prefixed 128 hex(64바이트); address·did 파생 후 저장)

- [ ] **Step 1: 실패하는 테스트 작성**

`AgentIdentityServiceTest.java`:
```java
package io.github.wantaekchoi.agentpay.identity;

import io.github.wantaekchoi.agentpay.identity.domain.*;
import io.github.wantaekchoi.agentpay.identity.port.AgentIdentity;
import io.github.wantaekchoi.agentpay.shared.crypto.Signatures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigInteger;
import java.util.UUID;
import org.web3j.utils.Numeric;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class AgentIdentityServiceTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired AgentRegistrationService registration;
    @Autowired AgentIdentity agentIdentity;
    @Autowired UserRepository users;

    @Test
    void registersAgentAndVerifiesItsSignature() {
        var ownerKp = Signatures.generateKeyPair();
        User owner = new User(UUID.randomUUID(), "alice", "0xpub", ownerKp.address());
        users.save(owner);

        var agentKp = Signatures.generateKeyPair();
        String pubHex = Numeric.toHexStringWithPrefixZeroPadded(agentKp.publicKey(), 128);
        Agent agent = registration.register(owner.getId(), pubHex, "shopper");

        assertThat(agent.getDid()).startsWith("did:key:zQ3s");
        assertThat(agent.getAddress()).isEqualTo(agentKp.address());

        String challenge = "login-nonce-42";
        String sig = Signatures.sign(agentKp.privateKey(), challenge);
        assertThat(agentIdentity.verifyChallenge(agent.getId(), challenge, sig)).isTrue();
    }

    @Test
    void rejectsSignatureFromWrongKey() {
        var ownerKp = Signatures.generateKeyPair();
        User owner = new User(UUID.randomUUID(), "bob", "0xpub", ownerKp.address());
        users.save(owner);

        var agentKp = Signatures.generateKeyPair();
        String pubHex = Numeric.toHexStringWithPrefixZeroPadded(agentKp.publicKey(), 128);
        Agent agent = registration.register(owner.getId(), pubHex, "shopper");

        var attackerKp = Signatures.generateKeyPair();
        String sig = Signatures.sign(attackerKp.privateKey(), "login-nonce-42");
        assertThat(agentIdentity.verifyChallenge(agent.getId(), "login-nonce-42", sig)).isFalse();
    }
}
```

- [ ] **Step 2: 실행 → 실패 확인**

Run: `./gradlew :app:test --tests "*AgentIdentityServiceTest"`
Expected: FAIL — 포트/서비스 미존재.

- [ ] **Step 3: 포트 + 서비스 구현**

`port/AgentIdentity.java`:
```java
package io.github.wantaekchoi.agentpay.identity.port;

import java.util.UUID;

public interface AgentIdentity {
    boolean verifyChallenge(UUID agentId, String message, String signatureHex);
}
```

`AgentRegistrationService.java`:
```java
package io.github.wantaekchoi.agentpay.identity;

import io.github.wantaekchoi.agentpay.identity.domain.*;
import io.github.wantaekchoi.agentpay.shared.crypto.Signatures;
import io.github.wantaekchoi.agentpay.shared.did.DidKey;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.UUID;

@Service
public class AgentRegistrationService {
    private final AgentRepository agents;
    private final UserRepository users;

    public AgentRegistrationService(AgentRepository agents, UserRepository users) {
        this.agents = agents; this.users = users;
    }

    public Agent register(UUID ownerUserId, String publicKeyHex, String alias) {
        if (!users.existsById(ownerUserId)) {
            throw new IllegalArgumentException("소유자 미존재: " + ownerUserId);
        }
        BigInteger publicKey = Numeric.toBigInt(publicKeyHex);
        String address = Numeric.prependHexPrefix(Keys.getAddress(publicKey)).toLowerCase();
        String did = DidKey.encode(publicKey);
        Agent agent = new Agent(UUID.randomUUID(), ownerUserId,
                Numeric.prependHexPrefix(publicKeyHex.replaceFirst("^0x", "")),
                address, did, alias, "ACTIVE");
        return agents.save(agent);
    }
}
```

`AgentIdentityService.java`:
```java
package io.github.wantaekchoi.agentpay.identity;

import io.github.wantaekchoi.agentpay.identity.domain.Agent;
import io.github.wantaekchoi.agentpay.identity.domain.AgentRepository;
import io.github.wantaekchoi.agentpay.identity.port.AgentIdentity;
import io.github.wantaekchoi.agentpay.shared.crypto.Signatures;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class AgentIdentityService implements AgentIdentity {
    private final AgentRepository agents;

    public AgentIdentityService(AgentRepository agents) { this.agents = agents; }

    @Override
    public boolean verifyChallenge(UUID agentId, String message, String signatureHex) {
        Agent agent = agents.findById(agentId).orElse(null);
        if (agent == null) return false;
        try {
            String recovered = Signatures.recoverAddress(message, signatureHex);
            return recovered.equalsIgnoreCase(agent.getAddress());
        } catch (RuntimeException e) {
            return false;
        }
    }
}
```

- [ ] **Step 4: 실행 → 통과 확인**

Run: `./gradlew :app:test --tests "*AgentIdentityServiceTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/io/github/wantaekchoi/agentpay/identity app/src/test/java/io/github/wantaekchoi/agentpay/identity/AgentIdentityServiceTest.java
git commit -m "feat(identity): agent registration + challenge verification (AgentIdentity port)"
```

---

### Task 6: A2A `AgentCard` + `AgentDirectory`

**Files:**
- Create: `.../identity/AgentCard.java`
- Create: `.../identity/port/AgentDirectory.java`
- Create: `.../identity/AgentDirectoryService.java`
- Test: `.../identity/AgentDirectoryServiceTest.java`

**Interfaces:**
- Consumes: `AgentRepository`.
- Produces:
  - `record AgentCard(String name, String did, String address, List<String> capabilities, String serviceEndpoint)`
  - `interface AgentDirectory { AgentCard cardFor(UUID agentId); }`

- [ ] **Step 1: 실패하는 테스트 작성**

`AgentDirectoryServiceTest.java`:
```java
package io.github.wantaekchoi.agentpay.identity;

import io.github.wantaekchoi.agentpay.identity.domain.*;
import io.github.wantaekchoi.agentpay.identity.port.AgentDirectory;
import io.github.wantaekchoi.agentpay.shared.crypto.Signatures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import org.web3j.utils.Numeric;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Testcontainers
class AgentDirectoryServiceTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired AgentRegistrationService registration;
    @Autowired AgentDirectory directory;
    @Autowired UserRepository users;

    @Test
    void buildsAgentCardFromRegisteredAgent() {
        var ownerKp = Signatures.generateKeyPair();
        User owner = new User(UUID.randomUUID(), "alice", "0xpub", ownerKp.address());
        users.save(owner);
        var agentKp = Signatures.generateKeyPair();
        String pubHex = Numeric.toHexStringWithPrefixZeroPadded(agentKp.publicKey(), 128);
        Agent agent = registration.register(owner.getId(), pubHex, "shopper");

        AgentCard card = directory.cardFor(agent.getId());

        assertThat(card.name()).isEqualTo("shopper");
        assertThat(card.did()).isEqualTo(agent.getDid());
        assertThat(card.address()).isEqualTo(agent.getAddress());
        assertThat(card.capabilities()).contains("payments");
    }
}
```

- [ ] **Step 2: 실행 → 실패 확인**

Run: `./gradlew :app:test --tests "*AgentDirectoryServiceTest"`
Expected: FAIL — 미존재.

- [ ] **Step 3: 구현**

`AgentCard.java`:
```java
package io.github.wantaekchoi.agentpay.identity;

import java.util.List;

public record AgentCard(
        String name,
        String did,
        String address,
        List<String> capabilities,
        String serviceEndpoint) {}
```

`port/AgentDirectory.java`:
```java
package io.github.wantaekchoi.agentpay.identity.port;

import io.github.wantaekchoi.agentpay.identity.AgentCard;
import java.util.UUID;

public interface AgentDirectory {
    AgentCard cardFor(UUID agentId);
}
```

`AgentDirectoryService.java`:
```java
package io.github.wantaekchoi.agentpay.identity;

import io.github.wantaekchoi.agentpay.identity.domain.Agent;
import io.github.wantaekchoi.agentpay.identity.domain.AgentRepository;
import io.github.wantaekchoi.agentpay.identity.port.AgentDirectory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AgentDirectoryService implements AgentDirectory {
    private final AgentRepository agents;

    public AgentDirectoryService(AgentRepository agents) { this.agents = agents; }

    @Override
    public AgentCard cardFor(UUID agentId) {
        Agent a = agents.findById(agentId)
                .orElseThrow(() -> new IllegalArgumentException("agent 미존재: " + agentId));
        return new AgentCard(
                a.getAlias(), a.getDid(), a.getAddress(),
                List.of("payments", "commerce.discovery"),
                "/agents/" + a.getId());
    }
}
```

- [ ] **Step 4: 실행 → 통과 확인**

Run: `./gradlew :app:test --tests "*AgentDirectoryServiceTest"`
Expected: PASS.

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/io/github/wantaekchoi/agentpay/identity/AgentCard.java app/src/main/java/io/github/wantaekchoi/agentpay/identity/port/AgentDirectory.java app/src/main/java/io/github/wantaekchoi/agentpay/identity/AgentDirectoryService.java app/src/test/java/io/github/wantaekchoi/agentpay/identity/AgentDirectoryServiceTest.java
git commit -m "feat(identity): A2A AgentCard + AgentDirectory port"
```

---

### Task 7: REST 컨트롤러 + did:web 문서

**Files:**
- Create: `.../identity/web/AgentController.java`
- Create: `.../identity/web/DidWebController.java`
- Test: `.../identity/web/AgentControllerTest.java`

**Interfaces:**
- Consumes: `AgentRegistrationService`, `AgentIdentity`, `AgentDirectory`, `AgentRepository`, `UserRepository`.
- Produces (HTTP):
  - `POST /agents` body `{ownerUserId, publicKey, alias}` → 201 `{id, did, address}`
  - `GET /agents/{id}/card` → `AgentCard` JSON
  - `POST /agents/{id}/verify` body `{message, signature}` → `{valid: boolean}`
  - `GET /agents/{id}/did.json` → did:web DID Document JSON

- [ ] **Step 1: 실패하는 MockMvc 테스트 작성**

`AgentControllerTest.java`:
```java
package io.github.wantaekchoi.agentpay.identity.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.wantaekchoi.agentpay.identity.domain.*;
import io.github.wantaekchoi.agentpay.shared.crypto.Signatures;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;
import java.util.UUID;
import org.web3j.utils.Numeric;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class AgentControllerTest {

    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:17");

    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ObjectMapper json;

    @Test
    void registerThenVerifyRoundTrip() throws Exception {
        var ownerKp = Signatures.generateKeyPair();
        User owner = new User(UUID.randomUUID(), "alice", "0xpub", ownerKp.address());
        users.save(owner);

        var agentKp = Signatures.generateKeyPair();
        String pubHex = Numeric.toHexStringWithPrefixZeroPadded(agentKp.publicKey(), 128);

        String body = json.writeValueAsString(Map.of(
                "ownerUserId", owner.getId().toString(),
                "publicKey", pubHex,
                "alias", "shopper"));

        String resp = mvc.perform(post("/agents")
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.did").value(org.hamcrest.Matchers.startsWith("did:key:zQ3s")))
                .andReturn().getResponse().getContentAsString();

        String agentId = json.readTree(resp).get("id").asText();

        String challenge = "nonce-xyz";
        String sig = Signatures.sign(agentKp.privateKey(), challenge);
        String verifyBody = json.writeValueAsString(Map.of("message", challenge, "signature", sig));

        mvc.perform(post("/agents/" + agentId + "/verify")
                        .contentType(MediaType.APPLICATION_JSON).content(verifyBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid").value(true));
    }

    @Test
    void servesDidWebDocument() throws Exception {
        var ownerKp = Signatures.generateKeyPair();
        User owner = new User(UUID.randomUUID(), "carol", "0xpub", ownerKp.address());
        users.save(owner);
        var agentKp = Signatures.generateKeyPair();
        String pubHex = Numeric.toHexStringWithPrefixZeroPadded(agentKp.publicKey(), 128);
        String body = json.writeValueAsString(Map.of(
                "ownerUserId", owner.getId().toString(), "publicKey", pubHex, "alias", "shopper"));
        String resp = mvc.perform(post("/agents").contentType(MediaType.APPLICATION_JSON).content(body))
                .andReturn().getResponse().getContentAsString();
        String agentId = json.readTree(resp).get("id").asText();

        mvc.perform(get("/agents/" + agentId + "/did.json"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(org.hamcrest.Matchers.containsString("did:web")))
                .andExpect(jsonPath("$.verificationMethod[0].blockchainAccountId")
                        .value(org.hamcrest.Matchers.containsString(agentKp.address())));
    }
}
```

- [ ] **Step 2: 실행 → 실패 확인**

Run: `./gradlew :app:test --tests "*AgentControllerTest"`
Expected: FAIL — 컨트롤러 미존재(404/compile).

- [ ] **Step 3: 컨트롤러 구현**

`web/AgentController.java`:
```java
package io.github.wantaekchoi.agentpay.identity.web;

import io.github.wantaekchoi.agentpay.identity.AgentCard;
import io.github.wantaekchoi.agentpay.identity.AgentRegistrationService;
import io.github.wantaekchoi.agentpay.identity.domain.Agent;
import io.github.wantaekchoi.agentpay.identity.port.AgentDirectory;
import io.github.wantaekchoi.agentpay.identity.port.AgentIdentity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/agents")
public class AgentController {
    private final AgentRegistrationService registration;
    private final AgentIdentity agentIdentity;
    private final AgentDirectory directory;

    public AgentController(AgentRegistrationService registration,
                           AgentIdentity agentIdentity, AgentDirectory directory) {
        this.registration = registration;
        this.agentIdentity = agentIdentity;
        this.directory = directory;
    }

    public record RegisterRequest(UUID ownerUserId, String publicKey, String alias) {}
    public record RegisterResponse(UUID id, String did, String address) {}
    public record VerifyRequest(String message, String signature) {}

    @PostMapping
    public ResponseEntity<RegisterResponse> register(@RequestBody RegisterRequest req) {
        Agent a = registration.register(req.ownerUserId(), req.publicKey(), req.alias());
        return ResponseEntity.status(201)
                .body(new RegisterResponse(a.getId(), a.getDid(), a.getAddress()));
    }

    @GetMapping("/{id}/card")
    public AgentCard card(@PathVariable UUID id) {
        return directory.cardFor(id);
    }

    @PostMapping("/{id}/verify")
    public Map<String, Boolean> verify(@PathVariable UUID id, @RequestBody VerifyRequest req) {
        return Map.of("valid",
                agentIdentity.verifyChallenge(id, req.message(), req.signature()));
    }
}
```

`web/DidWebController.java`:
```java
package io.github.wantaekchoi.agentpay.identity.web;

import io.github.wantaekchoi.agentpay.identity.domain.Agent;
import io.github.wantaekchoi.agentpay.identity.domain.AgentRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class DidWebController {
    private final AgentRepository agents;

    public DidWebController(AgentRepository agents) { this.agents = agents; }

    // did:web DID Document. did:web:<host>:agents:<id> 규약.
    @GetMapping("/agents/{id}/did.json")
    public Map<String, Object> didDocument(@PathVariable UUID id, HttpServletRequest request) {
        Agent a = agents.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("agent 미존재: " + id));
        String host = request.getServerName() + ":" + request.getServerPort();
        String didWeb = "did:web:" + host + ":agents:" + id;
        Map<String, Object> vm = Map.of(
                "id", didWeb + "#key-1",
                "type", "EcdsaSecp256k1RecoveryMethod2020",
                "controller", didWeb,
                "blockchainAccountId", "eip155:1:" + a.getAddress());
        return Map.of(
                "@context", List.of("https://www.w3.org/ns/did/v1"),
                "id", didWeb,
                "verificationMethod", List.of(vm),
                "authentication", List.of(didWeb + "#key-1"),
                "alsoKnownAs", List.of(a.getDid()));
    }
}
```

- [ ] **Step 4: 실행 → 통과 확인**

Run: `./gradlew :app:test --tests "*AgentControllerTest"`
Expected: PASS (2 tests).

- [ ] **Step 5: 커밋**

```bash
git add app/src/main/java/io/github/wantaekchoi/agentpay/identity/web app/src/test/java/io/github/wantaekchoi/agentpay/identity/web/AgentControllerTest.java
git commit -m "feat(identity): REST agent register/verify/card + did:web document"
```

---

### Task 8: ArchUnit 모듈 경계 테스트

**Files:**
- Modify: `app/build.gradle.kts` (archunit 의존성)
- Test: `.../architecture/ModuleBoundaryTest.java`

**Interfaces:**
- Consumes: 전체 컴파일된 클래스.
- Produces: CI 게이트 — 경계 위반 시 빌드 실패.

- [ ] **Step 1: archunit 의존성 추가**

`app/build.gradle.kts` `dependencies`:
```kotlin
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
```

- [ ] **Step 2: 실패 가능한 경계 테스트 작성**

`ModuleBoundaryTest.java`:
```java
package io.github.wantaekchoi.agentpay.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

class ModuleBoundaryTest {

    private final JavaClasses classes = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("io.github.wantaekchoi.agentpay");

    @Test
    void identityDomainDoesNotDependOnWeb3j() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..identity.domain..")
                .should().dependOnClassesThat().resideInAPackage("org.web3j..");
        rule.check(classes);
    }

    @Test
    void identityDoesNotDependOnWebLayerFromDomain() {
        ArchRule rule = noClasses()
                .that().resideInAPackage("..identity.domain..")
                .should().dependOnClassesThat().resideInAPackage("..identity.web..");
        rule.check(classes);
    }

    @Test
    void portsAreInterfaces() {
        ArchRule rule = com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes()
                .that().resideInAPackage("..identity.port..")
                .should().beInterfaces();
        rule.check(classes);
    }
}
```

- [ ] **Step 3: 실행 → 통과 확인**

Run: `./gradlew :app:test --tests "*ModuleBoundaryTest"`
Expected: PASS (3 rules). 위반이 있으면 위반 클래스를 리팩터해 경계를 지킨다.

- [ ] **Step 4: 전체 테스트 실행**

Run: `./gradlew :app:test`
Expected: PASS (전 태스크의 테스트 모두 green).

- [ ] **Step 5: 커밋**

```bash
git add app/build.gradle.kts app/src/test/java/io/github/wantaekchoi/agentpay/architecture/ModuleBoundaryTest.java
git commit -m "test(arch): ArchUnit module boundary rules for identity"
```

---

## Self-Review

**Spec coverage (Phase 1 범위):**
- 에이전트 등록 + 공개키 신원 검증 → Task 5, 7 ✓
- did:key → Task 3 ✓; did:web → Task 7 (DID Document) ✓
- A2A AgentCard → Task 6 ✓
- secp256k1 + EIP-191 서명 → Task 2 ✓
- 헥사고날 포트(`AgentIdentity`·`AgentDirectory`) → Task 5, 6 ✓
- ArchUnit 경계 강제 → Task 8 ✓
- Postgres + Flyway + Testcontainers → Task 4 ✓
- (Phase 1 제외: mandate·custody·payment·commerce·settlement·audit — Phase 2+)

**Placeholder scan:** 모든 스텝에 실제 코드·명령·기대출력 포함. "verify" 표시는 Global Constraints에 명시한 버전/좌표 확인 항목(플레이스홀더 아님).

**Type consistency 체크:**
- `Signatures.generateKeyPair()` → `KeyPair(privateKey, publicKey, address)` — Task 2 정의, Task 5·6·7 사용 일치 ✓
- `Signatures.sign(BigInteger, String)` / `recoverAddress(String, String)` — 일관 ✓
- `DidKey.encode(BigInteger)` — Task 3 정의, Task 5 사용 ✓
- `AgentRegistrationService.register(UUID, String, String) -> Agent` — Task 5 정의, Task 7 사용 ✓
- `AgentIdentity.verifyChallenge(UUID, String, String)` — Task 5 정의, Task 7 사용 ✓
- `AgentDirectory.cardFor(UUID) -> AgentCard` — Task 6 정의, Task 7 사용 ✓
- `Numeric.toHexStringWithPrefixZeroPadded(publicKey, 128)`(64바이트=128 hex) — Task 5·6·7 테스트에서 일관 사용 ✓

**주의(실행 시 확인):** Task 5의 `register`는 `publicKeyHex`를 `Numeric.toBigInt`로 파싱 후 `Keys.getAddress`로 주소 파생 — 테스트가 넘기는 `toHexStringWithPrefixZeroPadded(pub,128)`와 라운드트립되는지 Task 5 통과로 검증됨.

---

## Execution Handoff

계획을 `docs/superpowers/plans/2026-07-07-phase1-foundation-identity.md`에 저장했습니다. 실행 방식 두 가지:

1. **Subagent-Driven (권장)** — 태스크당 새 서브에이전트 dispatch, 태스크 사이 리뷰, 빠른 반복 (`superpowers:subagent-driven-development`)
2. **Inline 실행** — 이 세션에서 체크포인트 배치 실행 (`superpowers:executing-plans`)

어느 쪽으로 할까요?
