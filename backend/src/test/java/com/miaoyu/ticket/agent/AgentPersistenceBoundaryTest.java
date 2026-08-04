package com.miaoyu.ticket.agent;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

/** 防止 B 的会话持久化模型和端口依赖其他业务模块的持久化类型。 */
class AgentPersistenceBoundaryTest {

    @Test
    void shouldKeepAgentPersistenceModelAndPortsInsideAgentModule() {
        noClasses()
                .that()
                .resideInAnyPackage(
                        "..agent.domain.persistence..",
                        "..agent.application.persistence..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..ticketing..",
                        "..order..",
                        "..content..",
                        "..profile..",
                        "..recommendation..",
                        "..travel..")
                .check(new ClassFileImporter().importPackages("com.miaoyu.ticket.agent"));
    }
}
