## 1. 规则确认

- [x] 1.1 用户确认继续演示长沙；验证：公开行政代码为 `430100`，NetStart 内部 ID 为 `70`。

## 2. 后端

- [x] 2.1 D 将每日同步影院查询改为 `430100`；验证：Provider 测试确认标准化和公开查询键不出现 `70`。
- [x] 2.2 D 在 HTTP 适配器映射 `430100 → 70` 并拒绝未知城市；验证：单元测试通过。

## 3. 前端

- [x] 3.1 C 将影院默认城市和页面文案改为长沙 `430100`；验证：URL、API 参数和页面测试通过。

## 4. 验证

- [x] 4.1 运行后端定向测试、前端 content/影院测试、OpenSpec 严格校验和 `git diff --check`。
- [x] 4.2 重启最新版后端执行一次同步；验证：`location=430100` 返回 20 家 LIVE 影院且记录 cityCode 全为 `430100`。

## 5. 验证记录

- 后端定向测试使用 `-DforkCount=0` 执行：`NetStartContentProviderTest` 12 项、`RestClientNetStartRawClientTest` 2 项，共 14 项全部通过。默认 fork 模式受当前中文用户临时目录影响，在执行用例前退出并显示 `Tests run: 0`，不记为测试失败或通过。
- 前端 `pnpm check` 通过：格式、Lint、类型检查、20 个测试文件共 96 项测试、生产构建和哈希资源检查全部通过。
- 后端完整 `mvn verify -DforkCount=0` 已完成 81 个测试文件、290 项测试，0 失败、0 错误、17 项按环境跳过；随后打包因本机正在运行的后端进程占用 `target/cinewise-backend-0.1.0-SNAPSHOT.jar` 而未完成。影院定向测试和代码结果不受影响，PR 的后端 CI 需要完成最终打包检查。
- `openspec validate changsha-cinema-city-code --strict`、`openspec validate frontend-cinema-list-api-integration --strict` 和两个工作树的 `git diff --check` 通过。
- 2026-08-05 重新同步成功；`GET /api/v1/cinemas?location=430100&page=1&size=50` 返回 HTTP 200、20 家影院、`source=NETSTART_MAOYAN`、`sourceType=LIVE`，20 条记录的 `cityCode` 均为 `430100`。
- Redis 当前不可用，页面从 MySQL 真实快照读取，因此返回 `degraded=true/fallbackType=SNAPSHOT`；来源仍为 LIVE，不显示演示数据。
- Playwright 在 390×844 视口验证：页面显示“当前城市：长沙”、20 张影院卡片，请求使用 `location=430100`，接口 HTTP 200，同时显示真实来源、降级和历史快照提示，不显示“演示数据”，搜索框高度 44px。
