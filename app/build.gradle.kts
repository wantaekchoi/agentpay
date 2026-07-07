plugins {
    java
    id("org.springframework.boot") version "4.1.0"
    id("io.spring.dependency-management") version "1.1.7"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.web3j:crypto:4.12.3")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.withType<Test> {
    useJUnitPlatform()
    // Gradle 테스트 워커가 bootstrap classpath를 덧대며 뜨는 벤나인 CDS 경고 제거 → pristine 출력
    jvmArgs("-Xshare:off")
}
