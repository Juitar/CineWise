## MODIFIED Requirements

### Requirement: 公共导航必须使用含义明确且跨平台稳定的图标

系统 SHALL 为用户端桌面侧边栏、移动底栏和管理端侧边栏提供与入口含义一致的 SVG 图标。系统 MUST NOT 在上述导航入口中使用 Emoji，或以同一个图标代替不同入口。

#### Scenario: 打开用户端桌面侧边栏

- **GIVEN** 用户在大于等于 1024px 的视口打开用户端页面
- **WHEN** 侧边栏渲染首页、影片、影院和个人中心入口
- **THEN** 首页显示房屋图标
- **AND** 影片显示影片图标
- **AND** 两个入口不使用同一个图标

#### Scenario: 打开用户端移动底栏

- **GIVEN** 用户在小于 1024px 的视口打开用户端页面
- **WHEN** 底栏渲染首页、影片、影院和个人中心入口
- **THEN** 四个入口分别显示首页、影片、位置和用户图标

#### Scenario: 打开管理端侧栏

- **GIVEN** 管理员进入管理端页面
- **WHEN** 侧栏渲染工作台、订单管理和 Agent 运行记录入口
- **THEN** 每个入口显示稳定的 SVG 图标
- **AND** 不显示 Emoji 图标

### Requirement: 管理端通用展示优先使用 Ant Design 组件

系统 SHALL 使用 Ant Design 通用组件展示管理端可由组件库直接表达的列表、状态、表格、操作入口和日期选择。Ant Design 日期组件 MUST 同时使用中文组件环境和中文 `dayjs` 环境，月份、星期和操作文案不得显示英文。

#### Scenario: 管理员选择订单日期范围

- **GIVEN** 管理员打开订单筛选区域
- **WHEN** 管理员展开日期范围选择器
- **THEN** 系统使用 Ant Design `DatePicker.RangePicker`
- **AND** 月份、星期和操作文案显示中文

#### Scenario: 查看 Agent 运行记录

- **GIVEN** 管理员打开 Agent 运行记录页面
- **WHEN** 选择一条运行记录
- **THEN** 当前记录处于选中状态并显示对应状态标签
- **AND** 工具调用记录以表格展示

### Requirement: 移动用户端通用控件必须使用 antd-mobile

系统 SHALL 在小于 1024px 的移动首页使用 antd-mobile 提供输入、按钮、加载、错误和空态组件。桌面首页 SHALL 继续使用 Ant Design，两个视图 MUST 复用相同的查询状态、重试和跳转行为。

#### Scenario: 移动用户打开首页

- **GIVEN** 用户在小于 1024px 的视口打开首页
- **WHEN** 页面渲染 Agent 输入和内容查询状态
- **THEN** 输入、发送按钮、加载、错误和空态使用 antd-mobile 对应组件
- **AND** 输入内容、重试操作和跳转目标保持不变

### Requirement: 公共图标必须只有一个实现来源

系统 SHALL 将同一含义的公共图标保存在唯一文件中并由所有调用方复用。系统 MUST NOT 使用 Emoji 或普通字符模拟导航、位置、头像下拉和方案信息图标。

#### Scenario: 页面展示首页入口和业务信息图标

- **GIVEN** 用户打开登录、注册、密码重置、首页方案或影院列表
- **WHEN** 页面渲染首页、影院、刷新、偏好、金额、时间、人数或位置图标
- **THEN** 页面使用 `shared/components/icons/layout-icons.tsx` 导出的 SVG 图标
- **AND** 不显示对应 Emoji 图标

#### Scenario: 管理员打开顶栏用户菜单

- **GIVEN** 管理员进入管理端
- **WHEN** 顶栏渲染管理员头像和下拉标记
- **THEN** 头像使用 Ant Design `Avatar`
- **AND** 下拉标记使用公共 SVG 图标
