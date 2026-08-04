package com.miaoyu.ticket.auth.application;

/** 演示种子应用命令不依赖配置框架，基础设施负责把环境配置映射到该类型。 */
public record AuthDemoSeedCommand(
        String userEmail,
        String userPassword,
        String userNickname,
        String adminEmail,
        String adminPassword,
        String adminNickname,
        String privacyPolicyVersion) {
}
