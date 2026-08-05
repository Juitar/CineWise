## Context

最新 `dev` 中，后端 `ContentController` 已公开 `GET /api/v1/movies`，支持 `keyword/genre/page/size`，返回 `ContentPageResponse<MovieSummary>`。安全链允许匿名 GET；前端已有 `apiRequest<T>()`、`MovieSummary`、`ContentPageResponse<T>` 和内容响应契约测试，但 `/movies` 仍直接渲染硬编码数组。

当前最短路径不是补全全部内容与购票页面，而是先交付一个公开、只读、无需登录、无需写操作恢复的影片列表。这样可以先验证浏览器、Nginx、公共请求层、统一响应和内容来源展示，再复用相同做法接影院和场次。

## Goals / Non-Goals

**Goals:**

- 让 `/movies` 使用实际 HTTP 响应，不再依赖页面硬编码数据。
- 保持页面层只读取路由和组合视图，请求与竞态处理放入 content 模块。
- 只提供后端现有查询能力，来源、时效和降级文案与 C/D 已确认规则一致。
- 不增加依赖，在现有 React、Umi、Ant Design、antd-mobile 和 Vitest 范围内完成。

**Non-Goals:**

- 不实现影院、详情、固定影院筛影片和后续购票路由。
- 不为未实现的上映状态、榜单、地区、年份和排序编造前端逻辑。
- 不修改后端、数据库、缓存、Provider 或统一响应格式。

## Decisions

### 1. 新建 content 模块 API 与查询 Hook

在 `frontend/src/modules/content/` 中增加影片列表 API、查询参数解析和 `useMovieList`。API 只负责把 `MovieListQuery` 传给 `apiRequest<ContentPageResponse<MovieSummary>>()`；Hook 管理加载、旧数据失败恢复、错误、重试、AbortController 和最新查询标识。

页面不直接调用 `fetch` 或 `apiRequest`。本次不引入 TanStack Query 等新依赖，避免为了一个只读列表扩大安装和缓存改造范围。

### 2. URL 是筛选和分页的唯一可分享状态

`keyword/genre/page/size` 从 URL 读取并校验。用户改变关键词或分类时将 `page` 重置为 1；翻页只改变 `page/size`。页面局部只保留输入框草稿和本次已加载结果，不复制一份全局 content store。

### 3. 保留现有视觉骨架，但删除虚假的可操作含义

复用现有 `/movies` CSS 和卡片布局，将卡片数据改为 DTO 映射。没有 `posterUrl` 时使用项目本地默认海报或纯样式占位；图片设置固定尺寸、懒加载和替代文本。

现有“正在热映、即将上映、口碑榜、想看榜、地区、年份、综合排序”没有对应后端字段。本次从可操作区域移除或明确设为不可用，不发送无效参数，也不在浏览器对分页结果做业务筛选或排序。

### 4. 来源提示由纯函数统一计算

在 content 模块中用纯函数把 Freshness 转成可展示提示列表：

- `LIVE + 未过期 + 未降级`：显示来源和格式化后的更新时间；
- `isExpired=true`：追加“数据已过期，仅供参考”；
- `degraded=true`：追加“当前为降级数据”和 MOCK/CACHE/SNAPSHOT 对应类型；
- 字段缺失、枚举未知、时间非法：追加“来源尚未验证”。

各条件独立判断，不使用互斥 `else`，避免过期降级数据只显示一个风险。时间只用于展示，不转换业务 ID，不根据前端时钟改写后端 `isExpired`。

### 5. 旧请求通过取消和查询键双重保护

每次 URL 条件变化时取消上一请求，同时保存规范化查询键。即使运行环境无法及时取消已发出的响应，也只接受与当前查询键一致的结果。组件卸载时取消请求。

首次加载以及筛选、搜索、分页切换时都显示卡片 Skeleton，并隐藏旧卡片、旧来源、空状态和分页，避免新查询条件与旧结果同时出现。Hook 继续在内存中保留旧数据，但只在刷新失败后恢复旧卡片和来源提示；无旧数据时显示错误区与手动重试。离线只保留当前页面内存数据，不写 localStorage、IndexedDB 或 Service Worker。

### 6. 先做消费者测试，再做真实 HTTP 冒烟

模块测试覆盖请求参数、URL 校验、来源提示和竞态；页面测试覆盖加载、成功、空、错误、重试、分页和移动视口；最后启动当前 `dev` 后端与前端，用浏览器实际访问 `/movies`，确认请求命中 `/api/v1/movies` 且页面没有硬编码影片。

真实 Provider 是否启用不影响本 change 验收。若后端返回 `MOCK`，验收重点是页面明确显示演示/降级来源；`LIVE` 且未过期、未降级时按实时来源展示；`LIVE + CACHE/SNAPSHOT` 保留原始来源和更新时间，同时明确显示降级与缓存/快照，不表述为实时数据。

## Risks / Trade-offs

- [外部真实 Provider 尚未完成] → 先接后端真实 HTTP 响应，并如实展示 MOCK/CACHE/SNAPSHOT；不把“接后端”写成“已接实时外部数据”。
- [当前原型包含后端未支持的筛选] → 本次移除或禁用，等 available-movies 或新字段正式实现后再增加。
- [没有通用查询缓存库] → 用小范围 Hook 管理只读列表；后续多个内容页面复用时再在独立 change 评估缓存方案。
- [页面现有 CSS 与真实字段数量不同] → 保持卡片核心布局，缺图和可空字段有稳定占位，不为了视觉重构拖慢联调。

## Migration Plan

1. 在 content 模块增加 API、参数规范化、来源提示和查询 Hook，并先写单元测试。
2. 替换 `/movies` 硬编码数组，接入 URL 筛选、分页和页面状态。
3. 补充桌面/移动页面测试，执行前端完整检查。
4. 启动当前 `dev` 后端与前端，记录实际请求、响应来源和浏览器页面结果。
5. 若联调失败，只回退本 change 的前端文件；后端接口、数据库和 Provider 无需回退。

## Open Questions

无阻塞决定。影院列表、影片详情、固定影院筛影片和真实 Provider 展示复用将在后续独立 change 处理。
