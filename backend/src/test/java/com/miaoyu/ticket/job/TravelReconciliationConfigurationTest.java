package com.miaoyu.ticket.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

class TravelReconciliationConfigurationTest {

    private static final String PAID_ENABLED =
            "cinewise.transaction.paid-travel-reconciliation.enabled";
    private static final String REFUNDED_ENABLED =
            "cinewise.transaction.refunded-travel-reconciliation.enabled";

    @Test
    void givenProductionDefaults_whenResolveConfiguration_thenEnableBothReconciliationJobs()
            throws IOException {
        PropertySourcesPropertyResolver resolver = resolver("application.yml");

        assertThat(resolver.getProperty(PAID_ENABLED, Boolean.class)).isTrue();
        assertThat(resolver.getProperty(REFUNDED_ENABLED, Boolean.class)).isTrue();
    }

    @Test
    void givenTestProfile_whenResolveConfiguration_thenDisableAutomaticReconciliationJobs()
            throws IOException {
        MutablePropertySources sources = load("application.yml");
        load("application-test.yml").forEach(sources::addFirst);
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(sources);

        assertThat(resolver.getProperty(PAID_ENABLED, Boolean.class)).isFalse();
        assertThat(resolver.getProperty(REFUNDED_ENABLED, Boolean.class)).isFalse();
    }

    /** 只加载指定YAML，不继承开发机环境变量，确保断言针对仓库默认值。 */
    private PropertySourcesPropertyResolver resolver(String resource) throws IOException {
        return new PropertySourcesPropertyResolver(load(resource));
    }

    private MutablePropertySources load(String resource) throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        loader.load(resource, new ClassPathResource(resource)).forEach(sources::addLast);
        return sources;
    }
}
