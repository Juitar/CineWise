package com.miaoyu.ticket.content.infrastructure.provider;

import com.miaoyu.ticket.content.application.ContentProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * 注册内容模块自己的配置对象。
 *
 * <p>配置注册留在基础设施层，Application 和 Domain 不依赖 Spring 的配置注解。</p>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ContentProperties.class)
public class ContentProviderConfiguration {
}
