package com.miaoyu.ticket.common.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** OpenAPI 公共元数据与 HttpOnly Cookie 认证声明。 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfiguration {

    @Bean
    public OpenAPI cineWiseOpenApi() {
        SecurityScheme cookieAuth = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.COOKIE)
                .name("cinewise_access")
                .description("由浏览器自动携带的 Secure、HttpOnly JWT Cookie");
        return new OpenAPI()
                .info(new Info()
                        .title("CineWise REST API")
                        .version("v1")
                        .description("业务 ID 与金额均使用字符串；写请求需要 CSRF 与幂等语义。"))
                .components(new Components().addSecuritySchemes("cookieAuth", cookieAuth));
    }
}
