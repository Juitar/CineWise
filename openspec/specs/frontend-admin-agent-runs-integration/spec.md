# frontend-admin-agent-runs-integration Specification

## Purpose
TBD - created by archiving change frontend-admin-agent-runs-integration. Update Purpose after archive.
## Requirements
### Requirement: 前端必须消费 PR #170 的实际脱敏 DTO

前端 SHALL 将 `runId`、可空 `sessionId`、`userDisplay`、`status`、可空 `planId/planVersion`、节点统计、开始结束时间、耗时和可空错误字段映射为明确类型。详情节点 SHALL 接受可空 `targetName/startedAt/finishedAt/durationMs/toolStatus/errorCode/errorSummary/recoveryHint`，且只输出白名单字段。

#### Scenario: 消费 B 的两个固定夹具

- **WHEN** 前端运行时映射读取 PR #170 的列表和详情固定 JSON
- **THEN** 映射成功且所有业务 ID 保持 `string`
- **AND** 响应中的额外敏感字段不会进入页面 DTO

#### Scenario: 可空字段类型错误

- **WHEN** 非空字符串、整数或 ISO 时间字段收到不符合类型的值
- **THEN** 前端将响应作为无效数据处理并显示统一失败状态
- **AND** 不使用 `undefined` 或猜测值继续渲染

### Requirement: 页面必须兼容实际状态和后续新增状态

前端 SHALL 支持 `WAITING_LOCATION`、`RUNNING`、`COMPLETED`、`FAILED`、`CANCELLED`。`WAITING_LOCATION` SHALL 使用非失败样式；计划为空时 SHALL 显示“等待定位后生成计划”。未知状态 SHALL 显示安全的原始状态文本，不能导致渲染失败。

#### Scenario: 等待定位且计划尚未生成

- **WHEN** 运行状态为 `WAITING_LOCATION` 且 `planId/planVersion` 均为 `null`
- **THEN** 列表和详情显示“等待定位”及“等待定位后生成计划”
- **AND** 页面不出现 `undefined`、`null`、`NaN` 或失败样式

#### Scenario: 后端返回未知状态

- **WHEN** 运行或节点状态不是前端已登记值
- **THEN** 页面显示该原始状态的安全文本
- **AND** 列表、详情和筛选页面继续可用

### Requirement: 列表请求必须只发送有效筛选参数

前端 SHALL 通过公共 `apiRequest<T>()` 调用 `GET /api/v1/admin/agent-runs`，只发送非空 `status/userKeyword/startedFrom/startedTo` 和有效 `page/size`。时间 SHALL 使用有效 ISO 8601 字符串。

#### Scenario: 发送完整筛选

- **WHEN** 管理员提交状态、用户关键词、开始结束时间和分页
- **THEN** 请求只包含 PR #170 支持的六个查询参数
- **AND** 时间经过 URL 编码且值仍为 ISO 8601

#### Scenario: 清空筛选

- **WHEN** 筛选输入为空白或用户点击重置
- **THEN** URL 和 HTTP 请求均不包含空的筛选参数
- **AND** 列表回到第一页

### Requirement: 详情查询必须清理旧记录并可恢复

前端 SHALL 将 `runId` 作为字符串并使用 `encodeURIComponent` 请求详情。关闭、切换、刷新或重复请求详情时 SHALL 清除旧记录，并丢弃取消或迟到响应。

#### Scenario: 快速切换详情

- **WHEN** 管理员在第一条详情返回前关闭或打开另一条运行
- **THEN** 第一条迟到响应不会覆盖当前详情
- **AND** 新详情加载期间不显示上一条运行的数据

#### Scenario: 详情错误

- **WHEN** 详情返回 403、404 或 5xx
- **THEN** 页面分别显示无权限、“运行记录不存在或已失效”或统一失败状态
- **AND** 5xx 显示安全的 traceId 并提供重新加载

### Requirement: 页面必须覆盖查询和权限状态

页面 SHALL 区分首次加载、正常空列表、筛选后无数据、已有数据刷新、刷新失败、401、403、详情 404、5xx、翻页和窄屏。首次失败不得显示成空数据；已有列表刷新期间 SHALL 只显示公共骨架屏，不显示刷新提示条或旧列表；刷新失败后 SHALL 恢复旧列表。

#### Scenario: 首次加载和空数据

- **WHEN** 首次请求尚未完成
- **THEN** 页面显示公共加载状态
- **WHEN** 请求成功且记录为空
- **THEN** 页面根据是否有筛选显示普通空态或筛选无结果状态

#### Scenario: 已有列表刷新中

- **WHEN** 页面已有列表且管理员触发刷新，请求尚未完成
- **THEN** 页面显示公共骨架屏
- **AND** 不显示刷新提示条或旧列表

#### Scenario: 401 或 403

- **WHEN** 公共请求层收到 401
- **THEN** 继续执行统一会话清理和安全回跳，不在页面伪装为无数据
- **WHEN** 收到 403
- **THEN** 页面显示无权限状态且不展示运行数据

#### Scenario: 已有列表刷新失败

- **WHEN** 页面已有列表且刷新请求失败
- **THEN** 旧列表继续展示并显示刷新失败、traceId 和重试入口

### Requirement: 页面不得展示 Agent 敏感内容

前端 SHALL 只渲染 DTO 白名单中的脱敏摘要，不得展示模型思维过程、系统提示、原始工具参数或响应、Cookie、JWT、Token、完整用户输入、未脱敏邮箱手机号或精确位置。

#### Scenario: 响应混入敏感字段

- **WHEN** 测试响应额外包含原始 payload、工具参数、Token 或精确坐标
- **THEN** 运行时映射丢弃这些字段
- **AND** 页面文本和错误状态均不出现这些内容

### Requirement: 真实验收必须等待 PR #170 进入可部署基线

自动测试 SHALL NOT 被当作真实 HTTP 验收。只有 PR #170 合入可部署基线后，ADMIN 列表与详情成功、USER 403、匿名认证结果、详情 404、`WAITING_LOCATION` 和敏感字段检查全部通过，才可将真实验收任务标记完成。

#### Scenario: PR #170 尚未合入

- **WHEN** 前端代码和自动测试已通过但 PR #170 不在 `dev`
- **THEN** OpenSpec 仅勾选前端实现和自动测试任务
- **AND** 真实 HTTP、权限和部署验收任务保持未完成

