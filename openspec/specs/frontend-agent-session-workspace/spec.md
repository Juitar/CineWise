# frontend-agent-session-workspace Specification

## Purpose
TBD - created by archiving change frontend-agent-readonly-workspace. Update Purpose after archive.
## Requirements
### Requirement: 只有登录用户可以进入 Agent 工作区
系统 SHALL 提供 `/assistant` 和 `/assistant/:sessionId`。匿名用户直接访问或从首页提交 Agent 输入时 MUST 跳转登录并携带安全站内 `returnUrl`，登录前 MUST NOT 创建会话、运行或消息。

#### Scenario: 匿名用户从首页提交
- **WHEN** 匿名用户提交 Agent 输入
- **THEN** 前端跳转登录并保存受控回跳地址
- **AND** 登录前没有 Agent 请求

#### Scenario: 登录用户进入新工作区
- **WHEN** 登录用户访问 `/assistant`
- **THEN** 前端创建本人会话并替换到 `/assistant/:sessionId`
- **AND** sessionId 保持字符串且不写入持久化存储

### Requirement: 工作区必须通过当前 REST 接口管理本人会话
系统 SHALL 通过 `apiRequest<T>()` 和模块 Hook 创建/列出会话、查询消息历史、清空单个/批量会话、查询运行和取消运行。页面 MUST 展示加载、空数据、401、403、404、409 和网络错误，不得直接调用 REST。

#### Scenario: 刷新历史会话
- **WHEN** 登录用户刷新 `/assistant/:sessionId`
- **THEN** 前端查询消息历史并按服务端结果重建
- **AND** 不重发历史消息或创建新运行

#### Scenario: 清空跳过活动会话
- **WHEN** 批量清空返回 `clearedCount` 和 `skippedCount`
- **THEN** 页面展示实际数量并重新查询列表
- **AND** 跳过的会话不显示为已删除

### Requirement: 桌面和移动工作区必须共享业务状态
系统 SHALL 在桌面显示会话侧栏和主对话区，在移动端显示全屏主区和会话抽屉。两端 MUST 复用同一 API、Hook、DTO、reducer 和恢复逻辑，响应式切换不得丢失当前会话、运行、消息、占位卡片和游标。

#### Scenario: 跨断点切换布局
- **WHEN** 视口跨过 1024px 断点
- **THEN** 只改变布局外壳
- **AND** 当前 Agent 状态保持不变

