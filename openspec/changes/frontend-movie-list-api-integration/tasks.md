## 1. 契约与范围确认

- [x] 1.1 C 核对当前 `dev` 的 `/api/v1/movies` OpenAPI、`MovieSummary`、分页字段、公开权限和错误码；验证：前端类型、后端响应及消费者夹具字段逐项一致。
- [x] 1.2 C 确认本 change 只实现普通 `/movies` 浏览，不实现影院、详情、固定影院筛影片、场次和未受后端支持的榜单/地区/年份/排序；验证：改动文件和页面入口不越出 proposal。

## 2. 前端 content 模块

- [x] 2.1 C 新增影片列表 API，复用 `apiRequest<T>()` 和现有共享 DTO；验证：测试断言请求路径、`keyword/genre/page/size` 编码，不出现第二套 `fetch` 封装。
- [x] 2.2 C 新增 URL 查询参数规范化和 `useMovieList`，处理首次加载、旧数据失败恢复、错误、手动重试、取消和旧响应丢弃；验证：正常、非法参数、快速切换、卸载取消和失败重试测试通过。
- [x] 2.3 C 新增来源与时效提示映射，分别处理 LIVE、MOCK、CACHE、SNAPSHOT、过期、降级和无法验证来源；验证：组合状态表驱动测试通过，Mock 不显示为实时数据。

## 3. 影片列表页面

- [x] 3.1 C 将 `/movies` 的硬编码影片数组替换为 content Hook 结果，使用字符串 `movieId` 作为 key；验证：源码无硬编码影片成功数据，真实响应字段能渲染。
- [x] 3.2 C 实现关键词、分类、分页 URL 状态，只保留后端已支持的交互；验证：刷新恢复条件、修改筛选重置页码、非法参数不发请求。
- [x] 3.3 C 实现首次加载及筛选、搜索、分页切换 Skeleton，并处理空数据、失败、traceId、手动重试、旧数据失败恢复和离线只读提示；验证：加载期间隐藏旧影片、旧来源和分页，各页面状态组件测试通过，不回填本地演示影片。
- [x] 3.4 C 完成桌面与移动响应式展示、默认海报、图片懒加载和不小于 44px 的移动触控目标；验证：桌面和移动视口组件测试通过。

## 4. 验证与联调

- [x] 4.1 C 运行 `pnpm format:check`、`pnpm lint`、`pnpm typecheck`、`pnpm test` 和 `pnpm build:verify`；验证：记录通过数、失败数和跳过项。
- [x] 4.2 C 执行 `openspec validate frontend-movie-list-api-integration --strict`、`git diff --check` 和 `git status --short --branch`；验证：严格校验通过且仅包含本 change 文件。
- [x] 4.3 C 使用当前 `dev` 后端和前端完成真实 HTTP 联调，验证默认列表、关键词、分类、分页、空结果和后端不可用；验证：浏览器请求实际命中 `/api/v1/movies`，记录响应的 `sourceType/degraded/fallbackType`，页面不把非 LIVE 数据显示为实时数据。
- [x] 4.4 C 将联调结果反馈给 D；只有 OpenAPI 或来源字段实际变化时才由 D 修正后端或夹具；验证：问题明确到请求、字段、响应和负责人，不在前端增加临时兼容分支。

## 5. 2026-08-04 验证记录

- 前端完整检查：`pnpm check` 通过；17 个测试文件、84 个测试全部通过，格式、Lint、TypeScript 和生产构建均通过；构建产物共 56 个带哈希 JS/CSS 文件。新增用例确认已有数据刷新时显示骨架，并隐藏旧影片、旧来源和分页，请求完成后再显示新结果。
- 桌面浏览器：通过 Umi 开发代理实际请求当前 8080 后端；默认列表显示 10 部影片和“演示数据”，选择“科幻”后 URL 写入 `genre=科幻`、HTTP 200、列表变为 2 部且按钮 `aria-pressed=true`。
- 移动浏览器：390×844 视口显示 10 部影片；类型按钮实测高度 44px，390px 宽度使用单列布局。
- 后端直接查询：关键词“山野星光”返回 1 条；`page=2&size=3` 返回 3 条且 total=10；`genre=纪录` 返回 1 条且所有结果均包含该类型。
- 未完成项：查询不存在的关键词 `NO_SUCH_CINEWISE_MOVIE` 当前返回 HTTP 503 / `303004`，没有按已确认规则返回 HTTP 200 空分页。负责人：D。最少修改：内容查询在合法筛选无匹配记录时返回成功空分页，仍保留来源与时效字段；C 不增加临时兼容分支。
- D 确认：问题原因是 `DemoContentProvider` 无匹配时返回空，查询服务把“合法筛选无匹配”误判为“所有来源不可用”。D 将保持接口字段不变，修正为空分页，并补不存在关键词、已有关键词、类型筛选和分页回归测试；完成后提供分支、提交和真实响应样例给 C 复测。
- D 修复验证：提交 `39e728d` 已进入最新 `origin/dev`（验证基线 `3951beb`）。针对 `ContentControllerIntegrationTest`、`ContentQueryServiceTest`、`DemoContentProviderTest` 执行 20 个测试，0 失败、0 错误、0 跳过；本机 Surefire 单独启动测试 JVM 受中文临时目录影响，因此使用 `-DforkCount=0` 完成实际测试。
- 修复后真实 HTTP：不存在关键词返回 HTTP 200、`code=0`、`records=[]`、`total=0`、`page=1`、`size=20`，并保留 `sourceType=MOCK`、`degraded=true`、`fallbackType=MOCK`；已有关键词返回 1 条，科幻类型返回 2 条且类型全部匹配，`page=2&size=3` 返回 3 条且 total=10。
- 修复后浏览器联调：前端实际消费最新后端响应，输入 `NO_SUCH_CINEWISE_MOVIE` 后 URL 保留 keyword，页面显示“没有找到符合条件的影片”，未显示“影片加载失败”。
- 环境说明：8080 后端的 `/actuator/health` 因 Redis 状态返回 503，但 `/api/v1/movies` 可正常查询；该健康状态不冒充整体后端验收通过。
