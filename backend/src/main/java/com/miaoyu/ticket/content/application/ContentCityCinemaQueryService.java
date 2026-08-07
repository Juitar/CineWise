package com.miaoyu.ticket.content.application;

import com.miaoyu.ticket.common.error.BusinessException;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 将已解析的城市名映射为本地影院 ID。
 * 城市目录可用但没有影院时返回空列表，不调用 A，也不拿其他城市数据替代。
 */
@Service
public class ContentCityCinemaQueryService {

    private final CityResolutionService cityResolutionService;
    private final ContentCityCinemaQueryPort cinemaQueryPort;

    public ContentCityCinemaQueryService(CityResolutionService cityResolutionService,
                                         ContentCityCinemaQueryPort cinemaQueryPort) {
        this.cityResolutionService = cityResolutionService;
        this.cinemaQueryPort = cinemaQueryPort;
    }

    public List<Long> findCinemaIds(String cityName) {
        // 该服务不负责把用户地点文本持久化，城市解析完成后只继续使用规范化城市名。
        // 先经过同一城市目录，避免调用方把别名或未受控城市直接带到数据库查询。
        // RESOLVED 之外均是调用方输入问题，不能静默回退到默认城市。
        CityResolutionService.CityResolution resolution = cityResolutionService.resolve(cityName);
        if (resolution.status() != CityResolutionService.Status.RESOLVED) {
            // 未识别和需用户选择都不能拿默认城市替代，否则会查询到错误影院。
            throw new BusinessException(ContentCityErrorCode.CITY_NOT_RESOLVED);
        }
        // 空集合有明确含义：该城市尚未同步影院；上层据此不调用 A 的可售场次查询。
        return cinemaQueryPort.findActiveCinemaIds(resolution.cityName());
    }

    enum ContentCityErrorCode implements com.miaoyu.ticket.common.error.ErrorCode {
        CITY_NOT_RESOLVED(100001, "城市未解析");

        private final int code;
        private final String message;

        /** 固定错误码让前端区别“没有影院”和“请求城市无法解析”。 */
        ContentCityErrorCode(int code, String message) {
            this.code = code;
            this.message = message;
        }

        /** 错误码遵循公共响应约定，不能由 Controller 临时生成。 */
        @Override public int code() { return code; }
        @Override public String message() { return message; }
        @Override public org.springframework.http.HttpStatus httpStatus() {
            return org.springframework.http.HttpStatus.BAD_REQUEST;
        }
    }
}
