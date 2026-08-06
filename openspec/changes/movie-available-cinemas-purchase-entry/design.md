# 设计：影片可售影院入口

## A 侧实现状态

`backend/src/main/java/com/miaoyu/ticket/ticketing/api/ShowController.java` 已提供 `available-cinemas` Controller、Application Service、公开 DTO、OpenAPI 注解、固定夹具和契约测试。`cinema-detail-purchase-entry` 明确覆盖相反方向，不覆盖本 change。

最终 A 侧契约如下：

- 请求：`GET /api/v1/shows/available-cinemas?movieId={movieId}&page={page}&size={size}`；`movieId` 为无前导零的正十进制字符串，`page` 默认 1，`size` 默认 20，最大 50。
- 权限：匿名可读；仅该精确 GET 路径公开，其他写方法和未列出的场次路径仍受安全链保护。
- 可售条件：业务时区 `Asia/Shanghai` 未来 7 天窗口内，场次 `status=ON_SALE`、`startTime > dataAt/now`，且至少存在一张 `AVAILABLE` 座位；按最近开场时间、影院 ID稳定排序。
- 成功响应：`Result<PageResult<AvailableCinemaResponse>>`。记录包含 `cinemaId`（字符串）、影院摘要、`availableShowCount`、`nearestStartTime`、`contentSource/contentDataTime/contentExpiresAt/contentExpired`、`scheduleSource/scheduleDataTime`。
- 空结果：返回 HTTP 200、`code=0` 和空 `records`；不生成购票事实。
- 错误：非法 ID/分页参数为 `100001/400`；票务查询不可用为 `306003/503`；D 的内容摘要不可用保持其公开错误码（当前夹具为 `303004/503`），不得转为空结果。
- 购票跳转：C 只能使用返回的字符串 `movieId` 与 `cinemaId` 组合进入 `/shows?movieId={movieId}&cinemaId={cinemaId}`；A 返回的统计和最近开场时间仅用于入口展示，场次页必须重新查询权威场次。

## 已确认的安全规则

A 已确认 `GET /api/v1/shows/available-cinemas?movieId=&page=&size=` 是匿名公开只读查询。C 仅在唯一 `applicationSecurityFilterChain` 的现有场次 GET 白名单增加精确路径 `/api/v1/shows/available-cinemas`，与 `/available-movies`、`/available-dates` 保持一致。该修改不放开 POST、PUT、PATCH、DELETE 或其他 `/api/v1/shows/**` 路径，也不修改 JWT、Cookie、CSRF 或其他过滤链配置。

## A/D 已确认的接口

以下规则已由 A 的 #124 PR、固定夹具/HTTP 测试及 D 的公开端口确认：

1. A 已确认方法和路径为 `GET /api/v1/shows/available-cinemas?movieId=&page=&size=`，并由 C 放入现有匿名 GET 白名单。
2. `movieId` 是否只接受正十进制字符串，以及 `page/size` 的取值范围。
3. 响应是否包含 `cinemaId`、影院摘要、可售场次数量、最近开场时间、内容来源/时间、排期来源/时间，以及沿用哪组公共来源字段。
4. 未来时间窗口、`ON_SALE`、余座条件、排序、分页上限和时区。
5. 匿名权限；非法 ID、影片不存在/下线、内容不可用、票务失败的 HTTP 状态和业务码。
6. 合法但无场次必须成功返回空数组，不能把内容故障伪装成空结果。
7. A 提供票务查询/OpenAPI/契约测试，D 提供影院摘要 Application API 和来源语义，C 仅消费公开 DTO。

## 接口确认后的前端边界

页面只通过 `modules/content`/`modules/ticketing` 的 API、Hook 和公共 `apiRequest<T>()` 查询。入口使用语义化 `Link`；选影院页读取字符串 `movieId`，影院入口只生成 `/shows?movieId=...&cinemaId=...`。Hook 负责取消、竞态、加载、失败和重试；页面区分空、失败、404、过期、降级和离线。无可售影院隐藏购票入口。PC/移动共享实现，触控目标至少 44px，支持键盘焦点。

## 测试与回退

接口确认后测试列表点击、正常列表和参数、空态、加载、失败、重试、404、过期/降级、移动和键盘，再做真实 HTTP 联调。接口若被否决或字段冲突，更新本 change 后停工，不加猜测兼容分支；A 现有购票流程不回退。
