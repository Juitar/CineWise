package com.miaoyu.ticket.agent.application.location;

import com.miaoyu.ticket.agent.application.AgentErrorCode;
import com.miaoyu.ticket.auth.application.CurrentUserAccessor;
import com.miaoyu.ticket.common.error.BusinessException;
import java.math.BigDecimal;
import java.util.Optional;
import org.springframework.stereotype.Service;

/** 当前登录用户主动请求的城市识别；坐标只存在于本方法和外部请求中。 */
@Service
public class BrowserCityResolutionService {

    private static final BigDecimal MIN_LONGITUDE = new BigDecimal("-180");
    private static final BigDecimal MAX_LONGITUDE = new BigDecimal("180");
    private static final BigDecimal MIN_LATITUDE = new BigDecimal("-90");
    private static final BigDecimal MAX_LATITUDE = new BigDecimal("90");
    private static final int MAX_SCALE = 12;

    private final CurrentUserAccessor currentUserAccessor;
    private final BrowserCityResolver cityResolver;

    public BrowserCityResolutionService(CurrentUserAccessor currentUserAccessor, BrowserCityResolver cityResolver) {
        this.currentUserAccessor = currentUserAccessor;
        this.cityResolver = cityResolver;
    }

    public String resolveCurrentUserCity(BigDecimal longitude, BigDecimal latitude) {
        // 即使 Controller 已被安全过滤器保护，也在应用层明确要求当前用户，避免被其他入口误用。
        currentUserAccessor.requireCurrentUserId();
        requireCoordinate(longitude, MIN_LONGITUDE, MAX_LONGITUDE);
        requireCoordinate(latitude, MIN_LATITUDE, MAX_LATITUDE);
        return cityResolver.resolveCity(longitude, latitude)
                .filter(this::isChineseCityName)
                .orElseThrow(() -> new BusinessException(AgentErrorCode.CITY_RESOLUTION_UNAVAILABLE));
    }

    private void requireCoordinate(BigDecimal value, BigDecimal minimum, BigDecimal maximum) {
        if (value == null || value.compareTo(minimum) < 0 || value.compareTo(maximum) > 0
                || Math.max(value.stripTrailingZeros().scale(), 0) > MAX_SCALE) {
            throw new BusinessException(AgentErrorCode.CITY_RESOLUTION_UNAVAILABLE);
        }
    }

    private boolean isChineseCityName(String city) {
        return Optional.ofNullable(city)
                .map(String::trim)
                .filter(value -> value.matches("[\\p{IsHan}]{2,}"))
                .isPresent();
    }
}
