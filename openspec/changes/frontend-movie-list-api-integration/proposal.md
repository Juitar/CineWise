## Why

当前 `/movies` 页面仍使用 TSX 中写死的影片数组，用户看到的内容与后端 `GET /api/v1/movies` 无关。最新 `dev` 已具备公开影片查询、稳定分页、来源与时效字段，前端也已有统一 `apiRequest<T>()`、影片 DTO 和接口契约测试，因此影片列表是当前最少依赖、最快能接入后端真实响应的前端模块。

## What Changes

- 新增前端 content 模块的影片列表 API 和查询 Hook，经唯一公共 `apiRequest<T>()` 调用 `GET /api/v1/movies`。
- 将 `/movies` 页面中的硬编码影片数组替换为后端分页结果，使用 `movieId` 作为稳定键。
- 支持后端已经实现的 `keyword`、`genre`、`page`、`size` 查询；筛选和分页写入 URL，刷新后可恢复。
- 展示加载、空数据、失败、重试和断网保留旧数据状态；切换筛选、搜索或分页时显示卡片骨架，旧数据只用于失败恢复。
- 根据 `source/sourceType/dataTime/expiresAt/isExpired/degraded/fallbackType` 展示数据来源、更新时间、过期和降级提示，不把 Mock、缓存或快照数据写成实时数据。
- 移除或禁用后端尚未支持的“正在热映/即将上映/榜单/地区/年份/排序”交互，避免前端筛出或排序出不存在的业务含义。
- 增加模块、页面和真实后端联调测试，不新增依赖、不修改后端接口。

### 非范围

- 不实现影片详情、影院列表、固定影院筛影片、场次选择、选座、订单、支付或 Agent 页面。
- 不接入或修改 NetStart 等第三方 Provider；页面只消费后端返回的标准化结果。
- 不把 `MOCK`、`CACHE`、`SNAPSHOT` 结果称为实时真实内容。
- 不修改 `GET /api/v1/movies` 的字段、错误码、分页和权限。

## Capabilities

### New Capabilities

- `frontend-movie-list-api-integration`: `/movies` 页面使用后端公开影片列表，并正确处理筛选、分页、来源、加载和异常状态。

### Modified Capabilities

无。

## Impact

- Owner：C；D 只需在后端响应字段变化时复核，本 change 不要求 D 修改内容服务。
- 主要影响 `frontend/src/modules/content/**`、`frontend/src/pages/movies/**` 及其测试。
- 复用 `frontend/src/shared/api/client.ts` 和 `frontend/src/shared/types/api.ts`，不建立第二套请求封装或重复 DTO。
- 依赖已合入 `dev` 的公开 `GET /api/v1/movies`；真实外部 Provider 未完成时，页面会按响应明确显示演示、缓存或快照来源。
- 完成标志：启动当前 `dev` 后端和前端后，`/movies` 的卡片、筛选和分页来自实际 HTTP 响应，关闭后端时页面进入可重试错误状态，源码中不再保留影片硬编码数组。
