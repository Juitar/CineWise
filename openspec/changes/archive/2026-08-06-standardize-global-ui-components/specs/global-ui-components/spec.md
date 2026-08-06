## ADDED Requirements

### Requirement: 公共导航必须使用语义明确且跨平台稳定的图标

系统 SHALL 为用户端桌面侧栏、移动底栏和管理端侧栏提供与入口含义一致的 SVG 图标。系统 MUST NOT 在上述导航入口中使用 Emoji 或以同一个图标代替不同入口。

#### Scenario: 打开用户端移动底栏

- **GIVEN** 用户在小于 1024px 的视口打开用户端页面
- **WHEN** 底栏渲染首页、影片、影院和个人中心入口
- **THEN** 四个入口分别显示首页、影片、位置和用户图标

#### Scenario: 打开管理端侧栏

- **GIVEN** 管理员进入管理端页面
- **WHEN** 侧栏渲染工作台、订单管理和 Agent 运行记录入口
- **THEN** 每个入口显示稳定的 SVG 图标
- **AND** 不显示 Emoji 图标

### Requirement: 密码输入必须复用 Ant Design 密码组件

系统 SHALL 使用 Ant Design `Input.Password` 渲染登录和注册页的密码输入。组件 MUST 保持现有受控值、禁用状态、自动填充和显示/隐藏密码行为。

#### Scenario: 切换密码显示

- **GIVEN** 用户聚焦登录或注册页的密码输入框
- **WHEN** 用户点击密码显示按钮
- **THEN** 密码在掩码和明文之间切换
- **AND** 图标在输入框后缀区域垂直居中

### Requirement: 管理端通用展示优先使用 Ant Design 组件

系统 SHALL 使用 Ant Design `List`、`Tag`、`Table` 和 `Button` 展示 Agent 运行记录中可由通用组件表达的列表、状态、表格和操作入口。时间线等特殊展示可以保留自定义结构和样式。

#### Scenario: 查看运行记录

- **GIVEN** 管理员打开 Agent 运行记录页面
- **WHEN** 选择一条运行记录
- **THEN** 当前记录处于选中状态并显示对应状态标签
- **AND** 工具调用记录以表格展示
