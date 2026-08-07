## 实现方案

用户端复用 `shared/components/icons/layout-icons.tsx` 已有的 `HomeIcon`，只修正 `DesktopSidebar` 的导入和首页菜单项配置，不改变图标实现和菜单结构。

管理端日期筛选已经使用 Ant Design `DatePicker.RangePicker`。继续保留该组件，在全局 `AppProviders` 中导入 `dayjs/locale/zh-cn` 并调用 `dayjs.locale('zh-cn')`。Ant Design `ConfigProvider` 继续使用 `antd/locale/zh_CN`，两处配置共同保证日期组件的固定文案和日期库生成的月份、星期均为中文。

本次不涉及请求、状态、并发、恢复和数据迁移。回退时只需恢复首页图标引用和 `dayjs` 中文初始化。

移动首页继续复用当前查询 Hook 和状态，只按 `isMobile` 切换展示组件：输入和发送操作使用 antd-mobile `Input`、`Button`，加载、错误和空态分别使用 antd-mobile `Skeleton`、`ErrorBlock`、`Empty`；桌面视图保留 Ant Design。离线和刷新提示保留现有文字及页面样式，不改变查询和重试行为。

公共图标全部放入 `shared/components/icons/layout-icons.tsx`。删除 `shared/components/icons/index.tsx` 中重复的 `HomeIcon`，认证页改为从统一文件导入。补充刷新、影院、偏好、金额、时间、人数和下拉箭头 SVG，替换首页方案、影院列表和管理端顶栏中的 Emoji 或字符图标。

管理端顶栏继续使用 Ant Design `Dropdown`，触发器内头像改为 Ant Design `Avatar`；按钮语义、退出菜单和加载状态保持不变。

## 测试方案

- 在用户导航组件测试中替换图标为可识别的测试节点，断言桌面侧边栏首页使用 `HomeIcon`，影片使用 `FilmIcon`。
- 增加全局 Provider 测试，断言加载后 `dayjs.locale()` 为 `zh-cn`。
- 补充移动首页测试，断言移动视图渲染 antd-mobile 输入、按钮、加载、错误和空态。
- 补充图标导出和管理端顶栏测试，断言统一 `HomeIcon`、Ant Design `Avatar` 和 SVG 下拉图标生效。
- 静态检查实际页面不再包含本次列出的 Emoji 图标。
- 运行相关 Vitest、TypeScript 类型检查、前端构建和 OpenSpec 严格校验。
