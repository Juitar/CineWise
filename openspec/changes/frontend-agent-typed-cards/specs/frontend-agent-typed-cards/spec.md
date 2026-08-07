## ADDED Requirements

### Requirement: 正式 Agent payload 必须使用类型化组件展示
系统 SHALL 为 `TEXT`、`QUESTION`、`MOVIE_CARD`、`PLAN_CARD`、`BUSINESS_INTENT`、`PROGRESS` 和 `ERROR` 使用与类型对应的展示组件，不得把所有类型继续渲染为无区分的通用字段列表。

#### Scenario: 收到正式卡片事件
- **WHEN** 安全投影输出上述任一类型的展示项
- **THEN** 工作区通过统一注册入口展示对应的标题、正文、状态和允许公开的字段

### Requirement: 卡片组件必须只消费安全投影
系统 SHALL 只向卡片组件传递 `AgentDisplayItem` 或更窄的展示模型，MUST NOT 传递原始事件或 payload，也不得展示 actionId、内部工具参数、订单敏感字段、未校验 URL 或未声明字段。

#### Scenario: 文本包含 HTML 或未声明敏感字段
- **WHEN** 合法展示文本包含 HTML 字符，且原始 payload 还包含未声明字段
- **THEN** 页面把 HTML 当普通文本显示
- **AND** 未声明字段不进入组件属性或页面内容

### Requirement: 问题卡在没有回答接口时只能安全展示
系统 SHALL 展示 `QUESTION` 的问题内容和已校验选项标签；在没有正式回答接口时，MUST NOT 创建可提交选项或请求。

#### Scenario: 收到带选项的问题卡
- **WHEN** `QUESTION` 包含合法 options
- **THEN** 页面以只读方式展示选项标签
- **AND** 点击或键盘操作不会发送回答请求

### Requirement: 动态卡片必须显示数据状态
系统 SHALL 在电影卡和方案卡中展示服务端提供的来源、数据时间、有效期、降级和过期信息，不得自行推断缺失的价格、库存或可购结果。

#### Scenario: 收到降级且已过期的电影卡
- **WHEN** `MOVIE_CARD` 标记为降级且有效期已过
- **THEN** 页面明确显示降级结果和已过期状态
- **AND** 不补充服务端未提供的购票操作

### Requirement: 业务入口和确认操作必须保留现有安全规则
系统 SHALL 只使用投影已校验的 `selectSeatsPath` 生成选座入口，并 SHALL 将确认卡操作交给现有工作区回调。执行中、结果未知和终态仍不得再次提交。

#### Scenario: 展示确认方案卡
- **WHEN** `PLAN_CARD` 带有确认状态
- **THEN** 页面显示对应固定状态和确认操作
- **AND** 只有 `PENDING_CONFIRMATION` 且未提交时按钮可用

### Requirement: 桌面和移动端必须共用同一套卡片
系统 SHALL 在桌面和移动布局中使用同一套类型化卡片组件，只通过响应式样式调整布局，并保证长文本、字段和按钮不溢出容器。

#### Scenario: 在移动视口展示方案卡
- **WHEN** 页面宽度不超过 1023px
- **THEN** 卡片字段和操作区适配窄屏
- **AND** 展示内容和桌面端一致

### Requirement: 未知类型必须安全降级
系统 SHALL 对当前不支持的展示 kind 输出固定安全占位，不得尝试渲染未知 payload。

#### Scenario: 注册入口收到未知 kind
- **WHEN** 展示入口收到当前版本未注册的 kind
- **THEN** 页面显示“卡片暂不可用”
- **AND** 不显示未知数据内容
