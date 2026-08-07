## REMOVED Requirements

### Requirement: 桌面顶部显示全局搜索框

Web 端桌面顶部 SHALL NOT 显示占位文字为“搜索电影、影院”的全局搜索输入框。影片页和影院页已有的页面内搜索 SHALL 保持可用。

#### Scenario: 用户打开桌面端页面

- **WHEN** 用户打开使用 `DesktopTopBar` 的 Web 端桌面页面
- **THEN** 顶部栏不显示“搜索电影、影院”输入框

#### Scenario: 用户进入已有搜索页面

- **WHEN** 用户进入影片页或影院页
- **THEN** 页面自身已有的搜索入口仍可显示和使用
