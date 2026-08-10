## 实现方案

从首页 JSX 中删除 `personalized-banner` 节点，并移除仅供该节点使用的 `personalized-*` 样式。`RobotIcon`、`DesktopButton` 和 `MobileButton` 仍被首页其他真实功能使用，因此保留现有导入和组件。

## 测试方案

- 分别模拟移动端和 PC 端，验证首页不显示静态卡片标题和按钮。
- 执行首页组件测试、前端类型检查、代码检查、格式检查和生产构建。
- 执行 OpenSpec 严格校验和 Git 差异检查。

## 回退方式

回退本 change 的首页 JSX、CSS、测试和 OpenSpec 文件即可，不涉及接口和数据。
