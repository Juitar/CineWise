## 方案确认（当前只能停在这里）

- [x] C 根据 A 于 2026-08-07 的确认，在唯一 `applicationSecurityFilterChain` 将 `GET /api/v1/shows/available-cinemas` 加入精确公开白名单；验证：`CineWiseApplicationTest` 断言匿名 GET 不返回 401/403，且默认未公开路径仍返回 401。
- [ ] A、D 在 A PR 提供并确认最终 DTO、OpenAPI、固定夹具和 `movieId/page/size` 校验；验证：字段、范围与确认记录一致。
- [ ] A 确认可售时间窗口、`ON_SALE`、余座、排序、分页和时区；验证：边界及空结果契约测试。
- [ ] D 确认影院摘要 Application API、字段来源和时间语义；验证：不暴露持久化对象，内容故障不等于空结果。
- [ ] A、D 确认非法 ID、影片不存在/下线、内容不可用、票务失败的 HTTP 状态和业务码；验证：契约测试记录状态码和业务码。

## 接口确认后由 C 实现

- [ ] C 增加确认后的 DTO、API、Hook，复用 `apiRequest<T>()`；验证：请求和字段逐项匹配 OpenAPI。
- [ ] C 为 `/movies` 增加语义化入口和选影院页；验证：点击、键盘及移动 44px 测试。
- [ ] C 处理加载、空态、失败、重试、404、过期、降级、离线和来源时间；验证：状态测试覆盖，空结果不显示购票入口。
- [ ] C 将影院入口限制到 `/shows?movieId={movieId}&cinemaId={cinemaId}`；验证：参数为服务端字符串 ID，现有购票测试不变。
- [ ] C、A 真实 HTTP 联调；验证：记录来源、时间、空结果和错误，不新增静态票务数据。

## 交付检查

- [ ] C 运行 `pnpm check`；改后端时 A 运行相关后端测试。
- [ ] C 运行 `openspec validate movie-available-cinemas-purchase-entry --strict` 和 `git diff --check`。
