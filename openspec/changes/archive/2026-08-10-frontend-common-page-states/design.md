## 组件边界

公共组件位于 `shared/components/page-state`，只负责查询状态的固定结构和安全文案。组件不得依赖 `modules`、`features` 或 `pages`，不得发送请求、读取 store、处理 Cookie/JWT，也不得接收任意 JSX 作为内容。

对外按具体状态导出独立组件：

- `PageLoading`：页面首次加载的 Skeleton。
- `PageEmpty`：空数据及可选调整条件按钮。
- `PageError`：固定安全标题、调用方提供的短说明、可选问题编号和重试按钮。
- `PageForbidden`：403 无权限说明及可选返回按钮，不提供登录动作。
- `PageNotFound`：404 资源不存在说明及可选返回列表按钮。
- `PageRefreshingNotice`：有旧数据时的刷新提示。
- `PageRefreshErrorNotice`：刷新失败但继续展示旧数据的提示、可选问题编号和重试按钮。
- `PageStaleNotice`：只显示“已失效”或“待校验”，业务页面自行决定是否禁用操作。

操作只接受按钮文案和回调，不接受任意 React 节点。`PageError` 不接收服务端错误正文或错误对象，避免堆栈、超长原文和服务端 HTML 进入页面。React 默认文本转义作为最后一道保护。

## 页面状态处理

首次加载时 `data` 为空，影片页只显示 `PageLoading`，不显示旧卡片。已有数据且 `isRefreshing=true` 时保留卡片、来源说明和分页，并显示 `PageRefreshingNotice`。刷新失败且仍有数据时显示 `PageRefreshErrorNotice`，明确旧数据仍在展示；首次请求失败则显示 `PageError`。

离线内存快照使用 `PageStaleNotice status="pending-validation"`。服务端 `isExpired`、Mock、降级和来源时间仍由现有 `getFreshnessNotices` 计算和展示，公共组件不重复解释业务 Freshness 字段。

## 样式与响应式

公共状态使用单一 DOM 结构和独立 `PageState.css`。桌面和移动通过媒体查询调整间距，不建立两套组件。所有公共操作按钮最小高度和最小宽度均为 44px。

影片卡片、筛选和分页样式继续留在影片页 CSS；只删除已迁移到公共组件的状态专用样式。

## 测试方案

- 公共组件 Vitest 覆盖 loading、empty、error、traceId、retry、403、404、stale、refreshing、刷新失败保留旧数据、危险文本转义，以及桌面/移动共用结构标记。
- 影片页 Vitest 覆盖正常列表、首次加载、空结果、首次失败、手动重试、旧数据刷新、刷新失败、来源说明、URL 筛选和分页。
- 运行现有生产构建影片浏览器测试，确认 `/movies` 路由与真实构建懒加载不受影响。

## 回退方式

回退公共组件目录及影片页对应代码、样式、测试即可。没有接口、缓存、业务状态或数据迁移需要回退。
