package com.miaoyu.ticket.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "com.miaoyu.ticket", importOptions = ImportOption.DoNotIncludeTests.class)
class ModuleArchitectureTest {

    @ArchTest
    static final ArchRule DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORK = noClasses()
            .that()
            .resideInAPackage("..domain..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework..", "com.baomidou..", "jakarta.servlet..");

    @ArchTest
    static final ArchRule API_MUST_NOT_DEPEND_ON_PERSISTENCE = noClasses()
            .that()
            .resideInAPackage("..api..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..infrastructure.persistence..", "..persistence..", "..mapper..");

    @ArchTest
    static final ArchRule APPLICATION_MUST_NOT_DEPEND_ON_WEB = noClasses()
            .that()
            .resideInAPackage("..application..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("org.springframework.web..", "jakarta.servlet..");

    /** D 推荐只能通过 A 的 application 契约读取票务数据，不能耦合 HTTP 或持久化适配器。 */
    @ArchTest
    static final ArchRule RECOMMENDATION_MUST_NOT_DEPEND_ON_TICKETING_ADAPTERS = noClasses()
            .that()
            .resideInAPackage("..recommendation..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..ticketing.api..", "..ticketing.infrastructure..");

    /** B 只能依赖 A 的公开 Tool/DTO，不能绕过适配器调用订单应用或持久化实现。 */
    @ArchTest
    static final ArchRule AGENT_MUST_NOT_DEPEND_ON_ORDER_INTERNALS = noClasses()
            .that()
            .resideInAPackage("..agent..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage("..order.application..", "..order.infrastructure..");
}
