## ADDED Requirements

### Requirement: 问题卡必须通过现有消息入口提交答案

系统 SHALL 在合法且未过期的 `QUESTION` 上展示服务端提供的快捷选项，并在 `allowFreeText=true` 时展示自由文本输入。快捷回答 MUST 提交对应 `option.value`，自由文本 MUST 通过现有消息流提交；不得新增回答接口或自行拼接槽位对象。

#### Scenario: 用户选择快捷答案

- **WHEN** 用户点击未过期问题卡中的合法选项
- **THEN** 前端把该选项的 `value` 作为一条新消息提交
- **AND** 本次提交只生成一个 `clientRequestId`

#### Scenario: 用户提交自由文本

- **WHEN** 问题允许自由文本且用户输入非空答案
- **THEN** 前端通过现有消息流提交去除首尾空白后的文本

### Requirement: 问题卡必须阻止失效和重复提交

系统 SHALL 在 Agent 运行中、问题已提交或 `expiresAt` 已到期时禁用该问题卡的选项和输入，不得因重复点击重发答案。

#### Scenario: 问题已过期

- **WHEN** 当前时间不早于问题卡 `expiresAt`
- **THEN** 页面显示问题已过期
- **AND** 不允许提交快捷答案或自由文本

#### Scenario: 答案正在处理

- **WHEN** 用户已经从该卡提交答案且运行尚未结束
- **THEN** 同一卡片和其他旧问题卡均不可再次提交

### Requirement: 完整方案必须按正式字段展示

系统 SHALL 按服务端顺序展示 `PLAN_CARD.plans` 中最多三个方案，并展示存在的影片名、影院名、价格、开场时间、评分、理由、距离、来源、数据时间和有效期。前端 MUST NOT 自行排序、评分或补充缺失值。

#### Scenario: 收到三个完整方案

- **WHEN** `PLAN_CARD` 包含三个合法方案
- **THEN** 页面按原顺序展示三张方案卡及各自理由和动态数据状态

#### Scenario: 收到空方案和放宽建议

- **WHEN** `plans` 为空且存在 `relaxationSuggestion`
- **THEN** 页面明确显示暂无可用方案和服务端提供的放宽建议

### Requirement: 方案数据状态必须限制误导性操作

系统 SHALL 展示顶层及单项的降级、过期和不可购状态。过期或不可购方案 MUST 保持只读，不得生成建单、支付或选座操作。

#### Scenario: 方案已过期或不可购

- **WHEN** 方案的 `expired=true`、`purchaseEligible=false` 或有效期已到
- **THEN** 页面显示对应状态
- **AND** 不从该方案创建业务跳转或写请求

### Requirement: 选座和确认必须复用现有安全入口

系统 SHALL 仅由已校验的 `BUSINESS_INTENT + SELECT_SEATS` 生成本站选座路径，并 SHALL 继续把确认操作交给现有确认回调。卡片不得从自然语言或方案项猜测 ID，也不得重复实现确认请求和结果恢复。

#### Scenario: 合法选座意图和确认卡同时存在

- **WHEN** 页面展示合法选座意图和待确认卡
- **THEN** 选座入口只使用投影中的固定本站路径
- **AND** 确认按钮仍只提交 `actionId + confirmed`
