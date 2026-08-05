## ADDED Requirements

### Requirement: 长沙影院必须使用标准行政代码

系统 SHALL 在公开查询、标准化内容、持久化、快照和 REST 响应中使用长沙行政代码 `430100`。系统 MUST NOT 把 NetStart 城市 ID `70` 暴露为 `cityCode` 或要求前端提交 `70`。

#### Scenario: 同步长沙影院

- **GIVEN** NetStart Provider 已启用
- **WHEN** 每日或启动同步查询长沙影院
- **THEN** HTTP 适配器向 NetStart 发送 `ci=70`
- **AND** 标准化影院、数据库和公开快照使用 `cityCode=430100`

#### Scenario: 前端打开影院列表

- **GIVEN** 用户打开不带城市参数的 `/cinemas`
- **WHEN** 前端加载影院列表
- **THEN** 请求使用 `location=430100`
- **AND** 页面显示当前城市为长沙
- **AND** LIVE 快照存在时展示真实影院而非 Demo

#### Scenario: 未支持的 Provider 城市

- **GIVEN** HTTP 适配器收到不是 `430100` 的影院行政代码
- **WHEN** 准备调用 NetStart
- **THEN** 系统拒绝该调用
- **AND** 不猜测或透传错误的第三方城市 ID
