package com.parkj3onghoon.gatefuturesbot.architecture

import com.tngtech.archunit.core.importer.ClassFileImporter
import com.tngtech.archunit.core.importer.ImportOption
import com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses
import org.junit.jupiter.api.Test

/**
 * 레이어 경계와 의존성 방향을 테스트로 강제한다.
 * 새 파일이 추가되어도 아키텍처가 침식되지 않도록 자동 검증.
 *
 * 현재는 전통 레이어드(market → client, config → strategy의 편의 변환).
 * Port/Adapter 도입(6단계)되면 더 강화된 룰 추가 예정.
 */
class ArchitectureTest {
    private val importedClasses =
        ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("com.parkj3onghoon.gatefuturesbot")

    @Test
    fun `strategy package must not depend on Gate io SDK`() {
        noClasses()
            .that()
            .resideInAPackage("..strategy..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("io.gate..")
            .because("strategy는 순수 도메인. SDK에 결합 금지 (테스트/백테스트 가능성 확보)")
            .check(importedClasses)
    }

    @Test
    fun `strategy package must not depend on client package`() {
        noClasses()
            .that()
            .resideInAPackage("..strategy..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..client..")
            .because("strategy는 순수 판단. infrastructure(client) 몰라야 함")
            .check(importedClasses)
    }

    @Test
    fun `only client and config packages may import Gate io SDK`() {
        noClasses()
            .that()
            .resideOutsideOfPackages("..client..", "..config..")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("io.gate..")
            .because(
                "SDK 의존은 client(래퍼)와 config(Bean 등록)에만. " +
                    "새 레이어에서 SDK를 직접 쓰려 하면 경고",
            ).check(importedClasses)
    }

    @Test
    fun `model package must not depend on any other project package`() {
        noClasses()
            .that()
            .resideInAPackage("..model..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "..client..",
                "..trading..",
                "..worker..",
                "..strategy..",
                "..ratelimit..",
                "..bootstrap..",
                "..exception..",
            ).because("model은 순수 도메인 모델. 어떤 레이어도 참조하지 않는다")
            .check(importedClasses)
    }

    @Test
    fun `no package depends on worker package`() {
        noClasses()
            .that()
            .resideInAnyPackage(
                "..strategy..",
                "..trading..",
                "..market..",
                "..client..",
                "..ratelimit..",
                "..model..",
                "..exception..",
                "..config..",
                "..bootstrap..",
            ).should()
            .dependOnClassesThat()
            .resideInAPackage("..worker..")
            .because("worker는 최상위 오케스트레이션. 하위가 worker를 참조하면 순환")
            .check(importedClasses)
    }

    @Test
    fun `exception package must remain standalone`() {
        noClasses()
            .that()
            .resideInAPackage("..exception..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "..client..",
                "..trading..",
                "..worker..",
                "..strategy..",
                "..ratelimit..",
                "..bootstrap..",
                "..market..",
                "..config..",
            ).because("exception 계층은 어떤 비즈니스 레이어에도 의존하지 않는다")
            .check(importedClasses)
    }

    @Test
    fun `trading package uses only client and model`() {
        noClasses()
            .that()
            .resideInAPackage("..trading..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "..strategy..",
                "..worker..",
                "..market..",
                "..bootstrap..",
            ).because("trading은 주문 실행 레이어. strategy/worker를 몰라야 한다")
            .check(importedClasses)
    }

    @Test
    fun `ratelimit package is a pure utility`() {
        noClasses()
            .that()
            .resideInAPackage("..ratelimit..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                "..client..",
                "..trading..",
                "..worker..",
                "..strategy..",
                "..market..",
                "..bootstrap..",
                "..config..",
            ).because("RateLimiter는 독립 유틸. 다른 레이어에 의존 금지")
            .check(importedClasses)
    }

    @Test
    fun `trading market worker depend on ExchangePort not client directly`() {
        noClasses()
            .that()
            .resideInAnyPackage("..trading..", "..market..", "..worker..")
            .and()
            .haveSimpleNameNotEndingWith("Adapter")
            .should()
            .dependOnClassesThat()
            .resideInAPackage("..client..")
            .because(
                "Hexagonal: 도메인은 ExchangePort(trading 내 interface)에만 의존. " +
                    "client는 Adapter에 대해서만 참조 가능.",
            ).check(importedClasses)
    }
}
