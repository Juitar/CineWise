package com.miaoyu.ticket.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * 保护用户画像模块的分层边界。
 *
 * <p>画像保存的是个人数据。领域类型一旦依赖 HTTP、ORM 或其他模块的持久化实现，后续很容易为图方便绕开 同意校验或跨模块读取用户资料，因此在基础代码阶段先固定依赖方向。
 */
@AnalyzeClasses(
    packages = "com.miaoyu.ticket.profile",
    importOptions = ImportOption.DoNotIncludeTests.class)
class ProfileModuleArchitectureTest {

  @ArchTest
  static final ArchRule DOMAIN_MUST_NOT_DEPEND_ON_OUTER_PROFILE_LAYERS =
      noClasses()
          .that()
          .resideInAPackage("..profile.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "..profile.api..", "..profile.application..", "..profile.infrastructure..");

  @ArchTest
  static final ArchRule DOMAIN_MUST_NOT_DEPEND_ON_FRAMEWORK =
      noClasses()
          .that()
          .resideInAPackage("..profile.domain..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.springframework..", "com.baomidou..", "jakarta.servlet..");

  @ArchTest
  static final ArchRule APPLICATION_MUST_NOT_DEPEND_ON_API_OR_INFRASTRUCTURE =
      noClasses()
          .that()
          .resideInAPackage("..profile.application..")
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("..profile.api..", "..profile.infrastructure..");
}
