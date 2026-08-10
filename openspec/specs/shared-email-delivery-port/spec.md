# shared-email-delivery-port Specification

## Purpose
TBD - created by archiving change shared-email-delivery-port. Update Purpose after archive.
## Requirements
### Requirement: 调用方只能按用户 ID 投递

系统 SHALL 提供类型化 `EmailDeliveryPort`，命令包含 `deliveryKey/templateCode/recipientUserId/variables/traceId` 且不得包含邮箱。端口 SHALL 在 C 认证模块内部解析账号，并只向存在、正常且邮箱已验证的用户投递。

#### Scenario: 已验证用户正常投递

- **WHEN** 调用方提交合法命令且用户存在、正常、邮箱已验证
- **THEN** C 内部解析邮箱并调用邮件 Provider，调用方和结果看不到完整邮箱

#### Scenario: 用户不存在或不可用

- **WHEN** 用户不存在、邮箱未验证、账号禁用/锁定或角色不可用
- **THEN** 端口返回类型化 `FAILED`，不调用 Provider，不说明或记录完整邮箱

### Requirement: 模板和变量必须使用白名单

系统 SHALL 只接受已登记模板，按模板限制变量名称、数量、单值长度和总长度，并对文本执行安全转义。提醒模板 SHALL 只允许 `movieTitle/cinemaName/startAt/adviceSummary/relativePath`，且 `relativePath` 只能是 `/travel/{taskId}` 形式的站内路径。

#### Scenario: 白名单模板正常渲染

- **WHEN** `VIEWING_REMINDER` 使用全部合法变量且部署已配置模板内容
- **THEN** 系统只替换登记占位符并投递转义后的主题和正文

#### Scenario: 非白名单模板或非法变量

- **WHEN** 模板未登记，或变量包含未允许名称、数量超限、值超长、敏感字段或非法站外路径
- **THEN** 端口返回 `FAILED`，不解析邮箱、不调用 Provider

### Requirement: 投递结果和恢复必须类型化

系统 SHALL 返回 `SENT/FAILED/UNKNOWN`。`UNKNOWN` 不得触发自动重投；调用方只能使用原 `deliveryKey` 调用 query 恢复。相同 `deliveryKey` 的重复 send SHALL 返回首次已知结果，不产生第二次 Mock 投递。

#### Scenario: SMTP 明确成功或失败

- **WHEN** SMTP 明确接受或明确拒绝邮件
- **THEN** 端口分别返回 `SENT` 或 `FAILED`，并提供不含敏感数据的消息标识或错误码

#### Scenario: SMTP 超时或响应丢失

- **WHEN** Provider 无法确认是否已经接受邮件
- **THEN** 端口返回 `UNKNOWN` 且不自动重发；不支持查询的 Provider 对 query 继续返回 `UNKNOWN`

#### Scenario: 重复 deliveryKey

- **WHEN** 调用方再次使用同一 `deliveryKey` 调用 send
- **THEN** Mock Provider 返回首次结果且投递计数不增加；真实 SMTP 仅在已有明确进程内结果时返回该结果，否则保持 `UNKNOWN`

### Requirement: 未配置和日志必须安全

系统 SHALL 在 SMTP、发件人或模板未配置时安全关闭，不得返回伪造 `SENT`。日志、指标和返回值 SHALL 不包含完整邮箱、模板变量值、主题、正文、验证码、Cookie 或 SMTP 密钥。

#### Scenario: SMTP 未配置

- **WHEN** 公共邮件开关关闭或必需 SMTP 配置缺失
- **THEN** 端口返回类型化 `FAILED`，不尝试网络连接

#### Scenario: 敏感信息扫描

- **WHEN** 正常、失败和未知投递完成后扫描日志与返回对象
- **THEN** 不出现测试邮箱、变量值、邮件正文、验证码或密钥

### Requirement: 公共端口不承担 D 的提醒业务

系统 SHALL 只负责基础投递、用户邮箱解析、安全限制、结果查询和必要监控，不访问 D、Agent、订单的 Repository 或 Controller，不修改通知状态、触发时间或 `travel` 数据。

#### Scenario: 架构依赖检查

- **WHEN** 扫描公共邮件端口的生产代码依赖
- **THEN** 只依赖认证公开/内部边界、公共基础和邮件 Provider，不引用 `travel/agent/order` 私有持久化或 Controller

