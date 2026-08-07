## 实现方案

从 `DesktopTopBar` 删除 Ant Design `Input` 及 `SearchIcon`，同时删除仅供该输入框使用的 `.desktop-search-input` 和 `.desktop-search-icon` 样式。检查 `SearchIcon` 没有其他调用方后，从公共布局图标文件删除该图标，并清理测试中的无用模拟。

影片页和影院页的搜索由各自页面实现，不依赖 `DesktopTopBar`，本次不修改这些页面。

## 测试方案

- 顶部栏组件测试断言不显示占位文字为“搜索电影、影院”的输入框。
- 执行相关 Vitest、前端类型检查、OpenSpec 严格校验和 Git 差异检查。

## 回退方式

回退本 change 的顶部栏、样式、公共图标、测试和 OpenSpec 文件即可，不涉及接口和数据。
