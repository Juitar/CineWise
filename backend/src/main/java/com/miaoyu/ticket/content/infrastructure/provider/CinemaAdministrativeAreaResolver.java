package com.miaoyu.ticket.content.infrastructure.provider;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 将已支持城市的影院地址归一为可展示的行政区域。
 *
 * <p>NetStart 只给出地址文本，不能按单个“区”字截取：地址可能写县、县级市，地点名称也可能带“区”字。
 * 因此这里只匹配已登记的行政区名称；未登记的文本宁可保留未知，也不能把商业体名称伪装成行政区。</p>
 */
final class CinemaAdministrativeAreaResolver {

    static final String UNKNOWN_AREA = "未知区域";

    /**
     * 当前 NetStart 只支持长沙和杭州。清单只放市辖区、县和县级市，不把街道、乡镇或商圈作为天气和展示区域。
     * 新增同步城市时必须一并补充其已核对的行政区域，避免按模糊地址做跨城市猜测。
     */
    private static final Map<String, List<String>> AREAS_BY_CITY_CODE = Map.of(
            "430100", List.of(
                    "芙蓉区", "天心区", "岳麓区", "开福区", "雨花区", "望城区", "长沙县", "浏阳市", "宁乡市"),
            "330100", List.of(
                    "上城区", "拱墅区", "西湖区", "滨江区", "萧山区", "余杭区", "临平区", "钱塘区", "富阳区", "临安区",
                    "桐庐县", "淳安县", "建德市"));

    String resolve(String cityCode, String address) {
        if (cityCode == null || address == null) {
            return UNKNOWN_AREA;
        }
        return AREAS_BY_CITY_CODE.getOrDefault(cityCode.trim(), List.of()).stream()
                // 优先最长名称，防止未来同名的短名称抢先命中更具体的行政区域。
                .filter(address::contains)
                .max(Comparator.comparingInt(String::length))
                .orElse(UNKNOWN_AREA);
    }
}
