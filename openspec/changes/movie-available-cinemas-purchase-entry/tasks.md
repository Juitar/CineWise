## 接口确认

- [x] C 根据 A 于 2026-08-07 的确认，在唯一 `applicationSecurityFilterChain` 将 `GET /api/v1/shows/available-cinemas` 加入精确公开白名单；验证：`CineWiseApplicationTest`。
- [x] A、D 已提供并确认 DTO、OpenAPI、固定夹具、参数范围、来源时间、空结果和错误码；验证：`AvailableCinemaContractFixtureTest`、`AvailableCinemasIntegrationTest`、`AvailableCinemaQueryServiceTest`。

## C 前端实现

- [x] C 在 `modules/ticketing` 增加 DTO、`apiRequest<T>()` API 与查询 Hook；验证：请求和字段与正式夹具一致。
- [x] C 为 `/movies` 卡片增加语义化入口，并在 `/movies/:movieId` 实现选影院页；验证：点击、键盘和移动端测试。
- [x] C 处理合法空页、加载、失败、重试、非契约 404、`303004`、`306003`、过期、非实时来源、离线和来源时间；验证：页面状态测试。
- [x] C 只将影院入口跳转到 `/shows?movieId={movieId}&cinemaId={cinemaId}`；验证：服务端字符串 ID 和跳转参数测试。
- [ ] C、A 真实 HTTP 联调；验证：记录来源、时间、空结果和错误，不新增静态票务数据。

## 交付检查

- [ ] C 运行完整 `pnpm check`；当前格式、Lint、类型检查、定向测试和构建已通过，但全量 Vitest 受 60 秒执行上限影响未完成。
- [x] C 运行 `openspec validate movie-available-cinemas-purchase-entry --strict` 和 `git diff --check`。
