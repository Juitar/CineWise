## 1. 公共组件修复

- [x] 1.1 C 将桌面侧边栏首页入口改为 `HomeIcon`。（验证：用户导航组件测试）
- [x] 1.2 C 在全局 Provider 初始化 `dayjs` 中文环境，保留 Ant Design 中文 `ConfigProvider`。（验证：Provider 测试）

## 2. 验证

- [x] 2.1 C 补充首页图标和日期环境回归测试并运行相关 Vitest。
- [x] 2.2 C 运行前端类型检查和构建。
- [x] 2.3 C 运行 `openspec validate fix-global-navigation-and-calendar-locale --strict`、`git diff --check` 并核对变更文件。

## 3. 移动端和图标统一

- [x] 3.1 C 将移动首页输入、按钮、加载、错误和空态改为 antd-mobile 组件，保持原查询和跳转行为。（验证：首页组件测试）
- [x] 3.2 C 删除重复 `HomeIcon` 并统一认证页和导航引用。（验证：类型检查和图标引用检查）
- [x] 3.3 C 将管理端顶栏改为 Ant Design `Avatar` 和 SVG 下拉图标，保持退出行为。（验证：管理端布局测试）
- [x] 3.4 C 将首页方案和影院列表的 Emoji 替换为公共 SVG 图标。（验证：首页和影院组件测试、静态搜索）

## 4. 扩展验证

- [x] 4.1 C 运行相关 Vitest、全量前端测试、类型检查、Lint、格式检查和构建。
- [x] 4.2 C 运行 OpenSpec 严格校验、`git diff --check` 并核对最终变更文件。
