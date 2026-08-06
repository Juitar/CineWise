## Purpose

限制当前只读工作区只展示已合入 `dev` 的 B PR #98 卡片协议可以安全确认的内容。

## ADDED Requirements

### Requirement: 当前工作区只能展示安全确认的数据
系统 SHALL 展示 `TEXT`、`QUESTION`、`MOVIE_CARD`、`PLAN_CARD`、`PROGRESS`、`ERROR` 的白名单字段，以及完成状态、安全错误和未知/不完整卡片占位。`card.payload` 不完整时 MUST NOT 从 displayText、自然语言、事件顺序、首页静态数据或其他接口推断详情。

#### Scenario: 推荐卡没有可购场次
- **WHEN** `MOVIE_CARD` 只包含 B 夹具提供的 `movieId/title/source/dataAt/expiresAt/degraded`
- **THEN** 页面显示这些实际字段和降级状态
- **AND** 不补造 `cinemaId/showId/price/startTime/purchaseEligible/missingFactors` 或业务按钮

#### Scenario: 方案卡包含场次标识
- **WHEN** `PLAN_CARD` 提供 `movieId/cinemaId/showId/source/dataAt/expiresAt/degraded`
- **THEN** 页面将全部 ID 保持字符串并显示来源、时间和状态
- **AND** 不把卡片快照当作可直接购买或支付的结果

### Requirement: 位置授权只能只读安全展示
系统 SHALL 按 `LOCATION_PERMISSION.locationAuthorization` 展示等待授权、已授权、已拒绝、已过期和可手动输入状态。系统 MUST NOT 把精确位置、经纬度或地点原文写入 URL、日志、浏览器存储、长期投影或历史卡片，也不得调用确认动作接口提交位置。

#### Scenario: 用户拒绝设备位置
- **WHEN** `authorizationState=DENIED` 且 `deniedAction=USE_MANUAL_INPUT`
- **THEN** 页面显示可手动输入地点的安全提示
- **AND** 不读取设备位置、不发路线请求、不生成确认按钮

### Requirement: 确认动作保持只读
系统 SHALL 只把确认结果用于历史恢复和固定状态说明，不得调用 `POST /api/v1/agent/actions/{actionId}/confirm`，不得生成确认或拒绝按钮。写确认由独立 change `frontend-agent-action-confirmation` 实现。

#### Scenario: 历史包含确认结果
- **WHEN** 恢复结果为 `SUCCEEDED/REJECTED/RESULT_UNKNOWN` 或确认错误码
- **THEN** 页面只显示固定安全状态
- **AND** 不重发确认、不展示完整订单或原始参数

### Requirement: 成功内容不得被后续失败清空
系统 SHALL 保留已经通过校验并展示的文本、进度完成项和占位卡片。后续失败事件只能追加安全错误和更新运行状态，不得清空成功内容。

#### Scenario: 卡片后收到失败事件
- **WHEN** 页面已展示安全卡片占位并随后收到 message.error
- **THEN** 占位仍然存在且页面显示固定失败提示
- **AND** 不展示错误 payload

### Requirement: Agent 输出必须作为不可信文本处理
系统 SHALL 只使用安全 `displayText`、历史消息文本或固定前端文案，通过 React 文本节点展示。页面 MUST NOT 渲染危险 HTML、模型思维过程、系统提示词、原始工具参数、完整第三方响应、Cookie、JWT、验证码、精确位置、完整订单或异常堆栈。

#### Scenario: 文本包含 HTML 和脚本
- **WHEN** displayText 或历史文本包含脚本标签和事件属性
- **THEN** 页面按普通文本转义显示
- **AND** 不执行脚本、不发起额外请求
