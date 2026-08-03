package com.miaoyu.ticket.recommendation.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.content.domain.ContentSourceType;
import com.miaoyu.ticket.recommendation.application.FixedRecommendationCatalog;
import com.miaoyu.ticket.recommendation.application.FixedRecommendationCatalogProvider;
import java.io.IOException;
import java.io.InputStream;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * 读取唯一的 fixed-rec-v1 固定推荐目录。
 *
 * <p>目录只保存版本、来源和有效期；可购事实仍只能由 A 的 Application 查询提供，因此这里不会读取或
 * 生成场次、价格、库存数据。</p>
 */
@Component
public class ClasspathFixedRecommendationCatalogProvider implements FixedRecommendationCatalogProvider {

    private static final String RESOURCE_PATH = "classpath:recommendation/fixed-rec-v1.json";
    private static final String VERSION = "fixed-rec-v1";
    private static final String SOURCE = "FIXED_RECOMMENDATION";

    private final ObjectMapper objectMapper;
    private final ResourceLoader resourceLoader;

    /**
     * 使用框架托管依赖，避免读取规则随实例创建方式变化。
     *
     * <p>本类不缓存目录内容。固定目录很小，后续若增加缓存必须在独立 change 中补充失效和版本规则。</p>
     */
    public ClasspathFixedRecommendationCatalogProvider(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        this.objectMapper = objectMapper;
        this.resourceLoader = resourceLoader;
    }

    /** 资源缺失或字段被改坏时快速失败，不能悄悄替换成另一套硬编码候选。 */
    @Override
    public FixedRecommendationCatalog load() {
        // 资源路径固定，避免环境变量或用户输入把推荐规则切换到未经审核的文件。
        // 目录是类路径资源，部署时不会依赖某台机器上的本地文件路径。
        Resource resource = resourceLoader.getResource(RESOURCE_PATH);
        if (!resource.exists() || !resource.isReadable()) {
            // 规则目录不可用时直接失败；调用方不能退回到写死的候选。
            throw new IllegalStateException("Fixed recommendation catalog is unavailable: " + RESOURCE_PATH);
        }
        try (InputStream inputStream = resource.getInputStream()) {
            // 使用应用统一配置的 ObjectMapper，保证 Java 时间和枚举的序列化规则一致。
            // JSON 仅反序列化为目录 DTO，不携带任何用户输入或票务数据。
            FixedRecommendationCatalog catalog = objectMapper.readValue(inputStream, FixedRecommendationCatalog.class);
            // 校验通过后才交给应用服务，防止错误版本进入后续推荐结果。
            validate(catalog);
            return catalog;
        } catch (IOException exception) {
            // 不暴露文件路径或底层内容给调用方，错误由上层转换为稳定业务结果。
            throw new IllegalStateException("Failed to load fixed recommendation catalog", exception);
        }
    }

    private void validate(FixedRecommendationCatalog catalog) {
        // 版本、来源或类型不匹配都表示资源被误替换，必须停止而不是继续返回未知候选。
        // MOCK 类型是当前范围的明确边界，真实 Provider 不能复用此目录伪装为已接入数据。
        if (!VERSION.equals(catalog.version())
                || !SOURCE.equals(catalog.source())
                || catalog.sourceType() != ContentSourceType.MOCK) {
            // 不尝试修正损坏目录，避免在不同环境中产生不可复现的候选规则。
            throw new IllegalStateException("Fixed recommendation catalog is invalid");
        }
    }
}
