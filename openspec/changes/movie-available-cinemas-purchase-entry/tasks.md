## 接口已确认，等待 C 完成前端入口

- [x] C 根据 A 于 2026-08-07 的确认，在唯一 `applicationSecurityFilterChain` 将 `GET /api/v1/shows/available-cinemas` 加入精确公开白名单；验证：`CineWiseApplicationTest` 断言匿名 GET 不返回 401/403，且默认未公开路径仍返回 401。
- [x] A 已提供并验证最终 DTO、OpenAPI 注解、固定夹具和 `movieId/page/size` 校验；验证：`AvailableCinemaContractFixtureTest`、`AvailableCinemasIntegrationTest`。
- [x] A 已确认并测试可售时间窗口、`ON_SALE`、余座、排序、分页和 `Asia/Shanghai` 时区；验证：`AvailableCinemaQueryServiceTest` 与 `TicketingQueryMapper` SQL 条件。
- [x] D 已在影院详情购票流程确认中提供影院摘要 Application API、字段来源和时间语义；验证：不暴露持久化对象，内容故障不等于空结果。
- [x] A、D 已在 #124 PR 及相关契约测试中确认非法 ID、影片不存在/下线、内容不可用、票务失败的 HTTP 状态和业务码；验证：固定夹具与 HTTP 测试记录状态码和业务码。

## 接口确认后由 C 实现

- [ ] C 增加确认后的 DTO、API、Hook，复用 `apiRequest<T>()`；验证：请求和字段逐项匹配 OpenAPI。
- [ ] C 为 `/movies` 增加语义化入口，并在既有 `/movies/:movieId` 实现选影院页；验证：点击、键盘及移动 44px 测试。
- [ ] C 处理加载、合法空页、失败、重试、过期、降级、离线和来源时间；验证：合法未知/下线影片显示“暂无可售影院”，`contentExpired=true` 保留场次页入口，空结果不显示购票入口。
- [ ] C 将影院入口限制到 `/shows?movieId={movieId}&cinemaId={cinemaId}`；验证：参数为服务端字符串 ID，现有购票测试不变。
- [ ] C、A 真实 HTTP 联调；验证：记录来源、时间、空结果和错误，不新增静态票务数据。

## 交付检查

- [ ] C 运行 `pnpm check`；改后端时 A 运行相关后端测试。
- [ ] C 运行 `openspec validate movie-available-cinemas-purchase-entry --strict` 和 `git diff --check`。
