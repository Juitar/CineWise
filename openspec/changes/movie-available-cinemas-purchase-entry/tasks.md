## 方案确认

- [x] C 根据 A 于 2026-08-07 的确认，在唯一 `applicationSecurityFilterChain` 将 `GET /api/v1/shows/available-cinemas` 加入精确公开白名单；验证：`CineWiseApplicationTest` 断言匿名 GET 不返回 401/403，且默认未公开路径仍返回 401。
- [x] A、D 提供并确认 DTO、OpenAPI、固定夹具、`movieId/page/size` 校验、来源时间字段、空结果与错误码；验证：A 的契约测试与固定夹具覆盖成功、空结果、`100001`、`303004`、`306003`。

## C 前端实现

- [x] C 在 `modules/ticketing` 增加正式 DTO、`apiRequest<T>()` API 与查询 Hook；验证：请求、字段和错误码与固定夹具一致。
- [x] C 为 `/movies` 卡片增加语义化入口，并新增影片可售影院页与路由；验证：点击和键盘可进入对应 `movieId` 的页面。
- [x] C 展示可售影院、来源、数据时间、过期与降级状态；无结果不显示购票入口；验证：成功、空态、过期与降级测试。
- [x] C 只将影院入口跳转到既有 `/shows?movieId={movieId}&cinemaId={cinemaId}`；验证：保留服务端字符串 ID，不改选座和交易流程。
- [x] C 处理加载、失败、重试、404、`303004`、`306003` 与离线内存快照；验证：页面状态测试。
- [x] C 确保移动端可用、触控目标至少 44px、键盘焦点可见；验证：窄屏与键盘测试。
- [ ] C、A 真实 HTTP 联调；验证：记录来源、时间、空结果和错误，不新增静态票务数据。

## 交付检查

- [ ] C 运行 `pnpm check`；验证：类型、Lint、测试和构建全部通过。
- [x] C 运行 `openspec validate movie-available-cinemas-purchase-entry --strict` 和 `git diff --check`。
