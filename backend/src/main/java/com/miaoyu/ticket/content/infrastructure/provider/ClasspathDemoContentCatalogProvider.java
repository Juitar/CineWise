package com.miaoyu.ticket.content.infrastructure.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.content.application.DemoContentCatalog;
import com.miaoyu.ticket.content.application.DemoContentCatalogProvider;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * 从 classpath 读取 D 维护的唯一 Demo 内容清单。
 *
 * <p>资源文件只记录跨环境稳定的来源 ID 与内容字段，不能记录数据库雪花 ID。这样空库首次
 * 初始化可以生成本库的 ID，而重复初始化会按来源 ID 查回既有记录，A 的场次种子始终拿到
 * 当前数据库中的实际引用。</p>
 *
 * <p>这里在应用启动前完成版本、来源和唯一性校验；发现资源损坏时直接失败，不能悄悄退回
 * 另一套 Java 常量数据，否则会产生两套影片、影院内容。</p>
 *
 * <p>该实现只负责种子目录读取，尚不承担查询回退、缓存、快照或真实外部 Provider 调用。</p>
 */
@Component
public class ClasspathDemoContentCatalogProvider implements DemoContentCatalogProvider {

    /** 唯一允许的第一版资源，后续版本必须显式新增而非覆盖含义。 */
    private static final String RESOURCE_PATH = "classpath:content/demo-content-v1.json";

    /** 本次 D 接管的目录版本，用于阻止误加载其他演示数据。 */
    private static final String CATALOG_VERSION = "demo-content-v1";

    /** Mock 内容与内容种子使用的统一来源名称。 */
    private static final String CONTENT_SOURCE = "DEMO_CONTENT";

    /** Spring Boot 提供的统一 JSON 映射器，避免模块自行创建不同配置的 ObjectMapper。 */
    private final ObjectMapper objectMapper;

    /** 通过 Spring 资源抽象读取 classpath，业务层不处理文件系统路径。 */
    private final ResourceLoader resourceLoader;

    public ClasspathDemoContentCatalogProvider(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    /**
     * 读取并校验 Demo 目录。
     *
     * <p>每次加载都返回不可修改的列表；Provider 不写数据库，也不生成场次、票价、座位或库存。</p>
     *
     * @return 可供内容种子使用的唯一 Demo 目录
     */
    @Override
    public DemoContentCatalog load() {
        Resource resource = resourceLoader.getResource(RESOURCE_PATH);
        if (!resource.exists() || !resource.isReadable()) {
            throw new IllegalStateException("Demo content catalog is unavailable: " + RESOURCE_PATH);
        }
        try (InputStream inputStream = resource.getInputStream()) {
            DemoContentCatalog catalog = objectMapper.readValue(inputStream, DemoContentCatalog.class);
            validate(catalog);
            return catalog;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to load demo content catalog", exception);
        }
    }

    /**
     * 资源错误必须在写入种子前暴露，避免靠数据库唯一键发现一半数据已经写入的问题。
     */
    private void validate(DemoContentCatalog catalog) {
        if (!CATALOG_VERSION.equals(catalog.version())) {
            throw new IllegalStateException("Unexpected demo content catalog version");
        }
        if (!CONTENT_SOURCE.equals(catalog.source()) || catalog.sourceType() != ContentSourceType.MOCK) {
            throw new IllegalStateException("Demo content catalog source is invalid");
        }
        if (catalog.movies().size() != 10 || catalog.cinemas().size() != 4) {
            throw new IllegalStateException("Demo content catalog must contain 10 movies and 4 cinemas");
        }
        ensureUniqueMovieIds(catalog);
        ensureUniqueCinemaIds(catalog);
    }

    /** 固定影片来源 ID 是重复初始化的业务键，重复值会让同一影片被错误覆盖。 */
    private void ensureUniqueMovieIds(DemoContentCatalog catalog) {
        Set<String> sourceMovieIds = new HashSet<>();
        catalog.movies().forEach(movie -> {
            if (!sourceMovieIds.add(movie.sourceMovieId())) {
                throw new IllegalStateException("Duplicate demo movie source ID");
            }
        });
    }

    /** 固定影院来源 ID 同样必须唯一，保证场次种子始终关联同一家影院。 */
    private void ensureUniqueCinemaIds(DemoContentCatalog catalog) {
        Set<String> sourceCinemaIds = new HashSet<>();
        catalog.cinemas().forEach(cinema -> {
            if (!sourceCinemaIds.add(cinema.sourceCinemaId())) {
                throw new IllegalStateException("Duplicate demo cinema source ID");
            }
        });
    }
}
