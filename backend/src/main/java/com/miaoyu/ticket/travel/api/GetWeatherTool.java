package com.miaoyu.ticket.travel.api;

import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.travel.application.WeatherObservation;
import com.miaoyu.ticket.travel.application.WeatherQueryService;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * D 提供给 B 的区域天气只读工具，不创建任务或刷新建议快照。
 *
 * <p>区域只允许是已确认的影院行政区，不接收用户精确位置、地址或路线数据。
 * Provider 的缓存、Demo 回退和时效判断全部留在应用服务，工具不能自行拼装天气事实。
 * 返回的降级标识让 B 决定如何展示“演示数据”或“暂不可用”，而不是把它改写成实时天气。
 * 工具不记录 Agent 输入、不调用模型、不发布 SSE，也不会修改任何出行任务状态。</p>
 */
@Component
public class GetWeatherTool {
    public static final String TARGET_NAME = "getWeather";
    private final WeatherQueryService weatherQueryService;

    public GetWeatherTool(WeatherQueryService weatherQueryService) {
        this.weatherQueryService = weatherQueryService;
    }

    /**
     * 只接受已登记的目标名，防止错误路由把任意 Agent 节点带入 D 的外部数据查询。
     * 这里不使用 context 中的用户信息；天气查询只按已登记影院标识取得公开的动态数据。
     */
    public ToolResult<WeatherObservation> execute(ToolContext context, GetWeatherCommand command) {
        Objects.requireNonNull(context, "context 不能为空");
        Objects.requireNonNull(command, "command 不能为空");
        if (!TARGET_NAME.equals(context.targetName())) {
            return failed(context);
        }
        WeatherObservation result = weatherQueryService.query(command.longCinemaId());
        return new ToolResult<>(ToolStatus.SUCCESS, result, null, false, false, "RENDER_RESULT",
                result.degraded(), result.fallbackType(), context.stateVersion(), result.dataTime().toInstant(),
                result.expiresAt().toInstant());
    }

    private ToolResult<WeatherObservation> failed(ToolContext context) {
        return new ToolResult<>(ToolStatus.FAILED, null, CommonErrorCode.INVALID_PARAMETER.code(), false, false,
                "CHECK_TOOL_TARGET", false, null, context.stateVersion(), null, null);
    }

    /** 只接受影院业务 ID，禁止地址、区域名、坐标和 userId 进入 Tool Schema。 */
    public record GetWeatherCommand(String cinemaId) {
        public GetWeatherCommand {
            if (cinemaId == null || !cinemaId.matches("[1-9][0-9]*")) {
                throw new IllegalArgumentException("cinemaId 必须是正整数");
            }
        }

        public long longCinemaId() { return Long.parseLong(cinemaId); }
    }
}
