## ADDED Requirements

### Requirement: 个人中心不重复提供订单入口

`/profile` 页面 SHALL NOT 显示“我的订单”链接。用户端公共导航中已有的订单入口 SHALL 保持不变，个人中心仍 SHALL 提供隐私说明和退出登录操作。

#### Scenario: 已登录用户打开个人中心

- **WHEN** 已认证用户打开 `/profile`
- **THEN** 页面不显示“我的订单”链接，且仍显示隐私说明链接和退出登录按钮

#### Scenario: 用户通过公共导航访问订单

- **WHEN** 已认证用户使用桌面侧边栏
- **THEN** 现有“我的订单”入口仍跳转到 `/orders`
