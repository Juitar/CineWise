package com.miaoyu.ticket.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 单独保护 travel 的分层方向。
 *
 * <p>全局架构测试覆盖通用限制；这里补充 D 模块自身规则，防止后续事件、接口或持久化
 * 实现反向渗入领域基础类型。</p>
 */
@AnalyzeClasses(packages = "com.miaoyu.ticket.travel", importOptions = ImportOption.DoNotIncludeTests.class)
class TravelModuleArchitectureTest {

    @ArchTest
    static final ArchRule DOMAIN_MUST_NOT_DEPEND_ON_OUTER_TRAVEL_LAYERS = noClasses()
            .that()
            .resideInAPackage("..travel.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..travel.api..", "..travel.application..", "..travel.infrastructure..");

    @ArchTest
    static final ArchRule DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORK = noClasses()
            .that()
            .resideInAPackage("..travel.domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "com.baomidou..", "jakarta.servlet..");

    @ArchTest
    static final ArchRule APPLICATION_MUST_NOT_DEPEND_ON_API_OR_INFRASTRUCTURE = noClasses()
            .that()
            .resideInAPackage("..travel.application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..travel.api..", "..travel.infrastructure..");
}
