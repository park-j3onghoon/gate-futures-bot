plugins {
    kotlin("jvm") version "2.1.20"
    kotlin("plugin.spring") version "2.1.20"
    id("org.springframework.boot") version "3.5.3"
    id("io.spring.dependency-management") version "1.1.7"
    id("jacoco")
    id("org.jlleitschuh.gradle.ktlint") version "12.1.1"
}

group = "com.parkj3onghoon"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-configuration-processor")

    // Kotlin
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // HTTP 클라이언트 (Notification 채널의 외부 API 호출)
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    // Admin API (POST /admin/stop 등 관리자 엔드포인트)
    implementation("org.springframework.boot:spring-boot-starter-web")

    // Gate.io SDK
    implementation("io.gate:gate-api:7.1.8")

    // 캐시 (Caffeine — in-memory, 지표 계산 결과 hash 기반 재사용)
    implementation("com.github.ben-manes.caffeine:caffeine:3.1.8")

    // 이벤트 저장소 (H2 embedded + Spring Data JDBC — 경량)
    implementation("org.springframework.boot:spring-boot-starter-data-jdbc")
    runtimeOnly("com.h2database:h2")

    // Test
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("io.mockk:mockk:1.13.16")
    testImplementation("com.ninja-squad:springmockk:4.0.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("com.tngtech.archunit:archunit-junit5:1.3.0")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required = true
        html.required = true
    }
}

ktlint {
    version.set("1.5.0")
    verbose.set(true)
    outputToConsole.set(true)
    ignoreFailures.set(false)
    filter {
        exclude("**/build/**")
        exclude("**/generated/**")
    }
}

// 빌드 전 lint 자동 실행
tasks.named("check") {
    dependsOn("ktlintCheck")
}
