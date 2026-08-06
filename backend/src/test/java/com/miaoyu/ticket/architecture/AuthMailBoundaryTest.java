package com.miaoyu.ticket.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** 公共邮件端口只能留在认证模块，不能反向访问 D、Agent 或订单私有实现。 */
@AnalyzeClasses(packages = "com.miaoyu.ticket.auth", importOptions = ImportOption.DoNotIncludeTests.class)
class AuthMailBoundaryTest {

    @ArchTest
    static final ArchRule AUTH_MAIL_MUST_NOT_DEPEND_ON_OTHER_BUSINESS_INTERNALS = noClasses()
            .that()
            .resideInAnyPackage("..auth.application.mail..", "..auth.infrastructure.mail..")
            .should()
            .dependOnClassesThat()
            .resideInAnyPackage(
                    "..travel..",
                    "..agent..",
                    "..order..",
                    "..ticketing..",
                    "..profile..",
                    "..content..",
                    "..recommendation..");
}
