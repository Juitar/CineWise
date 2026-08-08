package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.agent.application.AgentCityCodeResolver;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** 内容模块将城市名称解析能力以 Agent 端口形式提供，避免会话持久化层依赖内容实现类型。 */
@Service
public class AgentCityCodeResolverAdapter implements AgentCityCodeResolver {
    private final CityResolutionService cityResolutionService;

    public AgentCityCodeResolverAdapter(CityResolutionService cityResolutionService) {
        this.cityResolutionService = cityResolutionService;
    }

    @Override
    public Optional<String> resolveCityCode(String locationText) {
        CityResolutionService.CityResolution resolved = cityResolutionService.resolve(locationText);
        return resolved.status() == CityResolutionService.Status.RESOLVED
                ? cityResolutionService.findCityCode(resolved.cityName())
                : Optional.empty();
    }
}
