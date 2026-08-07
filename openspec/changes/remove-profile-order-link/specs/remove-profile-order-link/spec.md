## ADDED Requirements

### Requirement: 个人中心提供订单入口

`/profile` 页面 SHALL 向已登录用户显示“我的订单”链接，并跳转到现有受保护的 `/orders` 页面。该页面作为从个人中心进入订单的下一层页面，个人中心仍 SHALL 提供隐私说明和退出登录操作。

#### Scenario: 已登录用户打开个人中心

- **WHEN** 已认证用户打开 `/profile`
- **THEN** 页面显示跳转 `/orders` 的“我的订单”链接，且仍显示隐私说明链接和退出登录按钮

#### Scenario: 个人资料意外缺失

- **WHEN** `/profile` 已进入页面但 `currentUser` 为空
- **THEN** 页面不显示“我的订单”链接

### Requirement: 桌面侧边栏不显示订单入口

桌面侧边栏 SHALL NOT 显示“我的订单”菜单项。桌面用户菜单和移动端底部导航的订单入口不在本 requirement 的修改范围内。

#### Scenario: 已登录用户浏览桌面侧边栏

- **WHEN** 已认证用户使用桌面布局
- **THEN** 桌面侧边栏不显示“我的订单”菜单项
