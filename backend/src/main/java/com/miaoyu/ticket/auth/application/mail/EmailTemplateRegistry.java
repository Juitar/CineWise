package com.miaoyu.ticket.auth.application.mail;

import java.util.Map;
import java.util.Optional;

/** 模板注册表同时完成白名单、变量限制和安全渲染。 */
public interface EmailTemplateRegistry {

    Optional<RenderedEmail> render(String templateCode, Map<String, String> variables);

    record RenderedEmail(String subject, String body) {
    }
}
