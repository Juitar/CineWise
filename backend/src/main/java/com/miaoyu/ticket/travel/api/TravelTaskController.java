package com.miaoyu.ticket.travel.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miaoyu.ticket.common.api.Result;
import com.miaoyu.ticket.common.config.ClockConfiguration;
import com.miaoyu.ticket.common.error.BusinessException;
import com.miaoyu.ticket.common.error.CommonErrorCode;
import com.miaoyu.ticket.travel.application.TravelTaskQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** C 页面使用的本人出行 REST 契约；Controller 只做参数校验、应用服务调用和响应映射。 */
/**
 * 本人出行任务的公开 REST 接口。
 * 该层只完成参数检查、调用 D 的应用服务和 DTO 映射。
 * 不访问订单或内容模块的 Controller、Repository、Mapper、Entity。
 * 用户归属由应用层从 CurrentUserAccessor 判断，不能相信请求传入的用户信息。
 * 响应只含页面展示所需摘要，避免输出支付、座位、邮箱和精确位置。
 * 跨模块摘要失败统一映射为 D 的稳定错误码，调用方可以据此决定重试或提示。
 */
@RestController
@RequestMapping("/api/v1/travel/tasks")
public class TravelTaskController {

    // 详情接口返回已取消任务，便于页面显示最终状态。
    // 任务不存在与非本人访问统一由应用层映射为 207001。
    // 摘要依赖不可用时只返回 207004，不伪造影片或影院资料。
    // 建议接口把天气缺失和通用交通建议分开表达。
    // 刷新接口的频率限制和版本冲突不在控制器重复实现。
    // PUT 成功后只返回已提交任务，避免再次读取外部摘要。
    // 类型化字段由服务端一次转换，C 不需要解析 weatherJson。
    // source、dataAt 和 expiresAt 用于页面标识资料时效。
    // degraded 和 fallbackType 用于说明天气或内容降级原因。

    private final TravelTaskQueryService travelTaskQueryService;
    private final ObjectMapper objectMapper;

    @Autowired
    public TravelTaskController(TravelTaskQueryService travelTaskQueryService, ObjectMapper objectMapper) {
        this.travelTaskQueryService = travelTaskQueryService;
        this.objectMapper = objectMapper;
    }

    public TravelTaskController(TravelTaskQueryService travelTaskQueryService) {
        this(travelTaskQueryService, new ObjectMapper());
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "查询本人出行任务详情")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "返回订单、影片和影院公开摘要",
                content = @Content(examples = @ExampleObject(name = "task-detail-success",
                        value = "{\"code\":0,\"data\":{\"taskId\":\"90001\",\"status\":\"READY\","
                                + "\"order\":{},\"movie\":{},\"cinema\":{}}}"))),
        @ApiResponse(responseCode = "404", description = "207001 任务不存在或无权访问"),
        @ApiResponse(responseCode = "503", description = "207004 订单或内容摘要不可用")
    })
    /**
     * 查询当前用户的任务详情。
     *
     * <p>订单、影片和影院信息由应用层通过公开查询口聚合，控制器不接触其他模块的持久化对象。
     * 任务已取消时仍返回详情，让页面可以显示取消结果；任务不存在或不属于当前用户则由应用层统一报 207001。
     * 当聚合依赖不可用时，应用层报 D 自己的 207004，不能补造影片或影院信息。</p>
     */
    public Result<TravelTaskResponse> getTask(@PathVariable String taskId) {
        return Result.success(toResponse(travelTaskQueryService.getMyTaskDetails(taskId)));
    }

    @GetMapping("/by-order/{orderId}")
    /** 按订单查询仍先经过本人任务校验，再复用同一详情聚合，避免暴露订单是否属于他人。 */
    public Result<TravelTaskResponse> getTaskByOrder(@PathVariable String orderId) {
        TravelTaskQueryService.TravelTaskView task = travelTaskQueryService.getMyTaskByOrderId(orderId);
        return Result.success(toResponse(travelTaskQueryService.getMyTaskDetails(task.taskId())));
    }

    @GetMapping("/{taskId}/advice")
    @Operation(summary = "查询本人出行建议")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "返回类型化天气和交通建议",
                content = @Content(examples = @ExampleObject(name = "advice-weather-normal",
                        value = "{\"code\":0,\"data\":{\"available\":true,\"taskId\":\"90001\","
                                + "\"taskStatus\":\"READY\",\"weather\":{\"condition\":\"多云\"},"
                                + "\"advice\":[{\"type\":\"TRANSPORT\",\"text\":\"提前到场\"}],"
                                + "\"source\":\"AMAP_WEATHER\",\"isExpired\":false,\"degraded\":false}}"))),
        @ApiResponse(responseCode = "404", description = "207001 任务不存在或无权访问")
    })
    /**
     * 读取已经生成的建议快照。
     *
     * <p>GET 不主动刷新天气或调用 Provider，避免普通页面刷新产生额外外部请求。
     * 尚未生成时以 {@code available=false} 表示，页面据此展示等待状态。
     * 返回的旧 JSON 字段仅为短期兼容；新页面应只读取类型化天气和建议数组。</p>
     */
    public Result<TravelAdviceResponse> getAdvice(@PathVariable String taskId) {
        // 建议查询只读快照；未生成时返回 available=false，不在 GET 中触发 Provider。
        return Result.success(toAdviceResponse(travelTaskQueryService.getMyAdviceSummary(taskId)));
    }

    @PutMapping("/{taskId}/reminder")
    /**
     * 更新提醒触发时间。
     *
     * <p>请求头和请求体中的版本必须相同，避免旧页面覆盖用户刚修改的时间。
     * 应用层完成状态、归属和版本校验后才会写入；写入成功只返回任务自身的已提交状态。
     * 不在这里再读取订单或内容摘要，防止写入已成功却因摘要暂时不可用被错误地报成 503。</p>
     */
    public Result<TravelTaskUpdateResponse> updateReminder(
            @PathVariable String taskId,
            @RequestHeader("If-Match") String ifMatch,
            @Valid @RequestBody UpdateReminderRequest request) {
        // If-Match 和请求体版本必须同时匹配，防止旧页面覆盖用户刚修改的提醒时间。
        long expectedVersion = parseVersion(ifMatch);
        if (expectedVersion != request.version()) {
            throw new BusinessException(CommonErrorCode.CONFLICT, "If-Match 与请求版本不一致");
        }
        LocalDateTime triggerAt = request.triggerAt().atZoneSameInstant(ClockConfiguration.BUSINESS_ZONE_ID)
                .toLocalDateTime();
        TravelTaskQueryService.TravelTaskView updated =
                travelTaskQueryService.updateMyReminder(taskId, triggerAt, expectedVersion);
        // 写入已经提交后不能再因外部摘要临时不可用而改成 503，否则客户端无法判断提醒是否更新成功。
        return Result.success(toUpdateResponse(updated));
    }

    @PostMapping("/{taskId}/advice/refresh")
    @Operation(summary = "刷新本人出行建议")
    @ApiResponses({
        @ApiResponse(responseCode = "409", description = "207002 已取消；207003 状态或版本冲突"),
        @ApiResponse(responseCode = "429", description = "107001 五分钟内重复刷新")
    })
    /**
     * 主动刷新当前用户的出行建议。
     *
     * <p>取消任务、不可刷新状态、版本冲突和五分钟频率限制都由应用层统一处理。
     * 控制器只把刷新后的快照转成公开 DTO，不保存路线、精确位置或邮件信息。
     * 天气 Provider 不可用时，应用层仍可提供通用交通建议并标明降级原因。</p>
     */
    public Result<TravelAdviceResponse> refreshAdvice(@PathVariable String taskId) {
        // 刷新由 Application Service 统一判断状态、版本和五分钟频率，Controller 不自行放宽条件。
        travelTaskQueryService.refreshMyAdvice(taskId);
        return Result.success(toAdviceResponse(travelTaskQueryService.getMyAdviceSummary(taskId)));
    }

    /**
     * 解析 HTTP 条件请求头中的任务版本。
     *
     * <p>客户端可能按 HTTP 规范传入带引号的 ETag 形式，因此仅移除引号后解析数字。
     * 不能解析时按参数错误处理，而不是让底层异常泄露到公开接口。
     * 版本本身不从请求体单独信任，调用方必须同时满足 If-Match 校验。</p>
     */
    private long parseVersion(String ifMatch) {
        try {
            return Long.parseLong(ifMatch.replace("\"", ""));
        } catch (RuntimeException exception) {
            throw new BusinessException(CommonErrorCode.INVALID_PARAMETER, "If-Match 必须是非负任务版本");
        }
    }

    /**
     * 将应用层详情映射为页面可直接读取的公开 DTO。
     *
     * <p>这里有意不放入价格、座位、库存、支付、退款、邮箱或路线几何等不属于出行页面的数据。
     * 影片海报、影院区域和地址允许为空，页面应按可空字段降级展示。
     * 内容摘要的来源和时间保留在 DTO 中，方便页面标明数据状态。</p>
     */
    private TravelTaskResponse toResponse(TravelTaskQueryService.TravelTaskDetails task) {
        return new TravelTaskResponse(task.taskId(), task.status().name(), task.triggerAt(), task.version(),
                new TravelOrderResponse(task.order().orderId(), task.order().orderNo(), task.order().showId(),
                        task.order().showStartTime()),
                new MovieResponse(task.movie().movieId(), task.movie().title(), task.movie().posterUrl(),
                        task.movie().source(), toOffsetDateTime(task.movie().dataTime())),
                new CinemaResponse(task.cinema().cinemaId(), task.cinema().name(), task.cinema().area(),
                        task.cinema().address(), task.cinema().source(), toOffsetDateTime(task.cinema().dataTime()),
                        toOffsetDateTime(task.cinema().expiresAt()), task.cinema().isExpired()));
    }

    /**
     * 将写入后的最小任务视图映射为更新响应。
     *
     * <p>更新接口不承担详情聚合责任，只回传客户端继续编辑提醒所需的任务标识、状态、时间和版本。
     * 前端若需要订单、影片或影院摘要，应另行调用详情接口。
     * 这样可将写入结果与可变的外部摘要读取结果分开。</p>
     */
    private TravelTaskUpdateResponse toUpdateResponse(TravelTaskQueryService.TravelTaskView task) {
        return new TravelTaskUpdateResponse(
                task.taskId(), task.orderId(), task.status().name(), task.triggerAt(), task.version());
    }

    /**
     * 将当前快照转成类型化建议响应。
     *
     * <p>旧快照中的 JSON 在服务端一次解析，C 不需要再解析字符串字段。
     * 没有可靠天气对象时不产生 WEATHER 建议，避免把通用文案误认为天气事实。
     * TRANSPORT 建议独立保留，使天气降级时页面仍能给出可执行的出行提示。</p>
     */
    private TravelAdviceResponse toAdviceResponse(TravelTaskQueryService.TravelAdviceSummary advice) {
        JsonNode weatherNode = readJson(advice.weatherJson());
        JsonNode adviceNode = readJson(advice.adviceJson());
        WeatherResponse weather = weatherNode == null ? null
                : new WeatherResponse(text(weatherNode, "area"), text(weatherNode, "condition"),
                        text(weatherNode, "risk"));
        List<AdviceItem> items = new ArrayList<>();
        // 没有可靠天气事实时只保留交通建议；降级原因由 fallbackType 明确表达，不能伪装为天气建议。
        if (weather != null) {
            addAdvice(items, adviceNode, "WEATHER", "weatherAdvice");
        }
        addAdvice(items, adviceNode, "TRANSPORT", "transportAdvice");
        OffsetDateTime dataAt = toOffsetDateTime(advice.dataTime());
        return new TravelAdviceResponse(advice.available(), advice.taskId(), advice.taskStatus().name(), weather,
                List.copyOf(items), advice.source(), dataAt, dataAt,
                toOffsetDateTime(advice.expiresAt()), advice.expired(), advice.degraded(), advice.fallbackType(),
                advice.weatherJson(), advice.adviceJson());
    }

    /**
     * 安全解析兼容期内的历史 JSON 快照。
     *
     * <p>快照为空或格式异常时只视为该对象不可用，不能向页面暴露内部解析异常。
     * 类型化字段因此可能为空，页面仍可依据 degraded 和 fallbackType 给出正确说明。
     * 该兼容逻辑可在旧快照淘汰后移除。</p>
     */
    private JsonNode readJson(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        // 兼容旧快照解析失败时只丢弃该对象，不把内部 JSON 解析异常暴露给 C。
        try {
            return objectMapper.readTree(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * 从对象节点读取可选文本字段。
     *
     * <p>不把 JSON 的 null 转成字符串 {@code "null"}，保持公开 DTO 的空值语义。
     * 该方法只服务兼容快照映射，不负责校验 Provider 数据质量。
     */
    private String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    /**
     * 按建议类型加入非空的兼容期文案。
     *
     * <p>每类建议独立判断，天气缺失不会连带丢失通用交通建议。
     * 建议类型由服务端固定，客户端不依赖历史 JSON 的字段名。
     * 空白文案不返回，避免页面生成没有内容的建议卡片。</p>
     */
    private void addAdvice(List<AdviceItem> items, JsonNode node, String type, String field) {
        String value = node == null ? null : text(node, field);
        // 天气缺失不影响通用交通建议，因此每一类建议独立判断是否加入数组。
        if (value != null && !value.isBlank()) {
            items.add(new AdviceItem(type, value));
        }
    }

    /**
     * 将业务时区的本地时间转为带偏移的公开时间。
     *
     * <p>接口统一返回 OffsetDateTime，避免 C 根据浏览器时区猜测场次、提醒或数据有效期。
     * 空时间保持为空，以表达上游摘要没有提供该时间。
     */
    private OffsetDateTime toOffsetDateTime(LocalDateTime time) {
        return time == null ? null : time.atZone(ClockConfiguration.BUSINESS_ZONE_ID).toOffsetDateTime();
    }

    /**
     * 更新提醒时间的请求体。
     * 版本用于和 If-Match 双重校验，触发时间以带时区的格式提交。
     */
    public record UpdateReminderRequest(@NotNull OffsetDateTime triggerAt, @PositiveOrZero long version) {
    }

    /**
     * C 展示任务页面所需的完整聚合结果。
     * 仅包含订单标识和内容摘要，不暴露交易、联系信息或精确位置。
     */
    public record TravelTaskResponse(
            String taskId, String status, OffsetDateTime triggerAt, long version,
            TravelOrderResponse order, MovieResponse movie, CinemaResponse cinema) {
    }

    /** 更新提醒只返回已提交任务本身，详情摘要仍由单独 GET 获取。 */
    public record TravelTaskUpdateResponse(
            String taskId, String orderId, String status, OffsetDateTime triggerAt, long version) {
    }

    /**
     * 任务关联订单的最小公开摘要。
     * 只用于定位场次和展示订单号，不包含价格、座位和支付状态。
     */
    public record TravelOrderResponse(String orderId, String orderNo, String showId, OffsetDateTime showStartTime) {
    }

    /**
     * 影片公开摘要及其数据来源时间。
     * 海报地址可为空，页面需要支持无海报展示。
     */
    public record MovieResponse(String movieId, String title, String posterUrl, String source, OffsetDateTime dataAt) {
    }

    /**
     * 影院公开摘要及其缓存有效期。
     * 区域和地址可为空，不能由 D 根据其他资料猜测补全。
     */
    public record CinemaResponse(String cinemaId, String name, String area, String address, String source,
                                 OffsetDateTime dataAt, OffsetDateTime expiresAt, boolean isExpired) {
    }

    /**
     * 类型化的出行建议响应。
     * 旧 JSON 字段只为历史客户端保留，新页面应使用 weather 和 advice。
     */
    public record TravelAdviceResponse(
            boolean available, String taskId, String taskStatus, WeatherResponse weather, List<AdviceItem> advice,
            String source, OffsetDateTime dataAt, OffsetDateTime dataTime, OffsetDateTime expiresAt, boolean isExpired,
            boolean degraded,
            String fallbackType, @Deprecated String weatherJson, @Deprecated String adviceJson) {
    }

    /**
     * 可用天气事实的最小展示字段。
     * 天气不可用时整个对象为空，而不是构造看似真实的占位数据。
     */
    public record WeatherResponse(String area, String condition, String risk) {
    }

    /**
     * 一条可直接展示的建议。
     * type 由服务端限定为公开枚举值，text 是已清洗的展示文案。
     */
    public record AdviceItem(String type, String text) {
    }
}
