package com.miaoyu.ticket.content.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.ErrorCode;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

/**
 * 将 C 或 B 临时提供的地点文本收敛为受控城市名。
 * 原始地点文本只在本方法参数内存在，既不落库、缓存，也不写日志。
 *
 * <p>城市目录随应用版本发布，不在用户请求时访问 NetStart。这样即使第三方城市列表暂时
 * 不可访问，已发布的长沙、杭州目录仍可稳定解析；目录文件自身损坏时，调用方收到 303004，
 * 不会把它误当成用户输入没有匹配城市。</p>
 */
@Service
public class CityResolutionService {

    /** 目录名称带版本，更新必须经过离线核验、测试和应用发布。 */
    private static final String CATALOG = "classpath:content/netstart-cities-v1.json";

    /** 只保留内存中的只读索引或“不可用”标记，不保存加载失败的文件内容。 */
    private final CatalogLoadResult catalogLoadResult;

    /** Spring 创建服务时只读取一次 classpath 文件，因此解析过程没有外部网络请求。 */
    public CityResolutionService(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        // 启动时只加载一次目录；目录故障被保留为受控不可用状态，不能让用户地点原文进入异常日志。
        this.catalogLoadResult = loadCatalog(objectMapper, resourceLoader);
    }

    /**
     * 只返回受控结果，不公开 Provider 城市编号或候选地点。
     * 空白、无命中和多命中属于正常输入，不能借助地址、坐标或相近名称猜测城市。
     */
    public CityResolution resolve(String locationText) {
        CityCatalog catalog = catalogLoadResult.catalog()
                .orElseThrow(() -> new BusinessException(CityResolutionErrorCode.DATA_UNAVAILABLE));
        if (locationText == null || locationText.isBlank()) {
            return CityResolution.unrecognized();
        }
        // 只做 Unicode 兼容归一化和首尾空白清理，避免扩大可匹配的候选范围。
        String normalizedLocation = Normalizer.normalize(locationText, Normalizer.Form.NFKC).strip();
        List<CityEntry> matches = catalog.entries().stream()
                .filter(entry -> normalizedLocation.contains(entry.cityName()))
                .toList();
        if (matches.size() != 1) {
            return matches.isEmpty() ? CityResolution.unrecognized() : CityResolution.selectionRequired();
        }
        return CityResolution.resolved(matches.getFirst().cityName());
    }

    /**
     * 仅供 D 的后续按城市同步使用，调用方只能先取得已受控返回的标准城市名。
     * 此方法不属于 REST DTO，Provider 城市标识不会流向 C、B 或页面状态。
     */
    public Optional<String> findProviderCityId(String cityName) {
        if (cityName == null || cityName.isBlank()) {
            return Optional.empty();
        }
        CityCatalog catalog = catalogLoadResult.catalog()
                .orElseThrow(() -> new BusinessException(CityResolutionErrorCode.DATA_UNAVAILABLE));
        return Optional.ofNullable(catalog.providerCityIdByName().get(cityName));
    }

    /**
     * 启动加载时校验版本、来源检查时间、城市名和 Provider 城市标识唯一性。
     * 不合法时保留不可用状态而非抛出原始解析异常，使 HTTP 层可稳定返回 503/303004。
     */
    private CatalogLoadResult loadCatalog(ObjectMapper objectMapper, ResourceLoader resourceLoader) {
        try (InputStream input = resourceLoader.getResource(CATALOG).getInputStream()) {
            CityCatalogDocument document = objectMapper.readValue(input, CityCatalogDocument.class);
            if (document == null || document.catalogVersion() == null || document.catalogVersion().isBlank()
                    || document.source() == null || document.source().isBlank()
                    || document.checkedAt() == null || document.checkedAt().isBlank()
                    || document.cities() == null || document.cities().isEmpty()) {
                return CatalogLoadResult.unavailable();
            }
            // 后续同步只能从标准城市名取得内部 ci，绝不能信任页面提交的 Provider 标识。
            Map<String, String> providerCityIdByName = document.cities().stream()
                    .filter(entry -> entry.cityName() != null && !entry.cityName().isBlank()
                            && entry.providerCityId() != null && !entry.providerCityId().isBlank())
                    .collect(Collectors.toUnmodifiableMap(
                            CityEntry::cityName, CityEntry::providerCityId, (left, right) -> {
                                throw new IllegalArgumentException("duplicate city name");
                            }));
            // 重复城市名或 ci 都会造成同步范围错误，必须拒绝整份目录。
            if (providerCityIdByName.size() != document.cities().size()
                    || providerCityIdByName.values().stream().distinct().count() != providerCityIdByName.size()) {
                return CatalogLoadResult.unavailable();
            }
            return CatalogLoadResult.available(new CityCatalog(List.copyOf(document.cities()), providerCityIdByName));
        } catch (Exception exception) {
            // 不把文件内容、异常详情或用户输入写入日志，C 只需得到稳定的不可用错误码。
            return CatalogLoadResult.unavailable();
        }
    }

    /** 单条目录只保存标准城市名和 D 内部使用的 Provider 城市标识。 */
    private record CityEntry(String cityName, String providerCityId) { }

    /** JSON 外层记录核验元数据，便于离线更新目录时审查来源和检查日期。 */
    private record CityCatalogDocument(
            String catalogVersion, String source, String checkedAt, List<CityEntry> cities) { }

    /** 不可变目录索引；解析请求只读它，不会把用户输入写回目录。 */
    private record CityCatalog(List<CityEntry> entries, Map<String, String> providerCityIdByName) { }

    /** 用 Optional 区分“目录可用但无城市”与“目录本身不可用”。 */
    private record CatalogLoadResult(Optional<CityCatalog> catalog) {
        static CatalogLoadResult available(CityCatalog catalog) {
            return new CatalogLoadResult(Optional.of(catalog));
        }

        static CatalogLoadResult unavailable() {
            return new CatalogLoadResult(Optional.empty());
        }
    }

    public record CityResolution(Status status, String cityName) {
        public static CityResolution resolved(String cityName) { return new CityResolution(Status.RESOLVED, cityName); }
        public static CityResolution unrecognized() { return new CityResolution(Status.UNRECOGNIZED, null); }
        public static CityResolution selectionRequired() { return new CityResolution(Status.SELECTION_REQUIRED, null); }
    }

    public enum Status { RESOLVED, UNRECOGNIZED, SELECTION_REQUIRED }

    /** 城市目录无法安全读取时，页面必须与正常“无结果”区分并提示稍后重试。 */
    private enum CityResolutionErrorCode implements ErrorCode {
        DATA_UNAVAILABLE;

        @Override public int code() { return 303004; }
        @Override public String message() { return "城市服务暂不可用"; }
        @Override public HttpStatus httpStatus() { return HttpStatus.SERVICE_UNAVAILABLE; }
    }
}
