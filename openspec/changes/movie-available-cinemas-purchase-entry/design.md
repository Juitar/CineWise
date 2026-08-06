# 设计：影片可售影院入口

## 当前阻塞

`backend/src/main/java/com/miaoyu/ticket/ticketing/api/ShowController.java` 目前只有 `queryAvailableMovies`，前端 `frontend/src/modules/ticketing/api.ts` 只有 `getAvailableMovies`；A 正在提供 `available-cinemas` Controller、Application Service、公开 DTO、OpenAPI 和固定夹具。`cinema-detail-purchase-entry` 明确覆盖相反方向，不覆盖本 change。

## 已确认的安全规则

A 已确认 `GET /api/v1/shows/available-cinemas?movieId=&page=&size=` 是匿名公开只读查询。C 仅在唯一 `applicationSecurityFilterChain` 的现有场次 GET 白名单增加精确路径 `/api/v1/shows/available-cinemas`，与 `/available-movies`、`/available-dates` 保持一致。该修改不放开 POST、PUT、PATCH、DELETE 或其他 `/api/v1/shows/**` 路径，也不修改 JWT、Cookie、CSRF 或其他过滤链配置。

## A/D 必须确认的接口

在实现前书面确认：

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
