package com.miaoyu.ticket.travel.api;

import com.miaoyu.ticket.agent.domain.tool.ToolContext;
import com.miaoyu.ticket.agent.domain.tool.ToolResult;
import com.miaoyu.ticket.agent.domain.tool.ToolStatus;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.travel.application.FoodSearchResult;
import com.miaoyu.ticket.travel.application.FoodSearchService;
import java.util.Objects;
import org.springframework.stereotype.Component;

/**
 * 查询影院周边餐饮的只读工具，不读取画像、不预订、不创建餐饮交易。
 *
 * <p>半径的默认值和边界检查由 {@code FoodSearchService} 统一执行，工具不能为了提高命中率扩大范围。
 * 查询只使用任务关联的影院区域，不使用用户当前位置；结果按服务端稳定排序并保留来源和时效。
 * 空候选和 Demo 回退都是可展示的成功降级，不允许工具捏造营业状态或餐饮推荐理由。</p>
 */
@Component
public class SearchNearbyFoodTool {
    public static final String TARGET_NAME = "searchNearbyFood";
    private final FoodSearchService foodSearchService;

    public SearchNearbyFoodTool(FoodSearchService foodSearchService) { this.foodSearchService = foodSearchService; }

    public ToolResult<FoodSearchResult> execute(ToolContext context, String taskId, Integer radiusMeters) {
        Objects.requireNonNull(context, "context 不能为空");
        if (!TARGET_NAME.equals(context.targetName())) {
            return new ToolResult<>(ToolStatus.FAILED, null, CommonErrorCode.INVALID_PARAMETER.code(), false, false,
                    "CHECK_TOOL_TARGET", false, null, context.stateVersion(), null, null);
        }
        FoodSearchResult result = foodSearchService.searchMyFood(taskId, radiusMeters);
        return new ToolResult<>(ToolStatus.SUCCESS, result, null, false, false, "RENDER_RESULT", result.degraded(),
                result.fallbackType(), context.stateVersion(), result.dataTime().toInstant(),
                result.expiresAt().toInstant());
    }
}
