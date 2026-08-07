## ADDED Requirements

### Requirement: 个人中心提供本人订单入口

受保护的 `/profile` 页面 SHALL 在认证用户的账号操作区域显示“我的订单”链接，并跳转到现有 `/orders` 页面。页面 SHALL NOT 查询、构造或展示订单数据；认证资料意外缺失时 SHALL NOT 显示该受保护操作。

#### Scenario: 已登录用户从个人中心进入订单列表

- **WHEN** 已认证用户打开 `/profile`
- **THEN** 页面显示“我的订单”链接，且链接地址为 `/orders`

#### Scenario: 个人资料意外缺失

- **WHEN** `/profile` 已进入页面但 `currentUser` 为空
- **THEN** 页面显示现有资料缺失提示，且不显示“我的订单”链接

### Requirement: 公共导航仅向已登录用户显示订单入口

用户端桌面和移动端公共导航 SHALL 仅在认证完成且存在当前用户时提供指向 `/orders` 的订单入口。移动端入口 SHALL 在小于 `1024px` 的布局中可访问。

#### Scenario: 已登录用户使用桌面导航

- **WHEN** 认证用户使用桌面布局
- **THEN** 左侧导航和右上角用户菜单均可进入 `/orders`

#### Scenario: 已登录用户使用移动导航

- **WHEN** 认证用户在小于 `1024px` 的布局中浏览用户端
- **THEN** 底部导航显示可进入 `/orders` 的订单入口

#### Scenario: 未登录用户浏览公共导航

- **WHEN** 当前会话不是认证状态或没有当前用户
- **THEN** 公共导航不显示订单入口

### Requirement: 保持订单页的认证跳转规则

订单入口 SHALL 复用现有 `/orders` 路由和 `RequireAuth` 守卫，不得通过前端入口绕过认证或改变未登录回跳地址。

#### Scenario: 未登录用户直接访问订单页

- **WHEN** 匿名用户打开 `/orders`
- **THEN** 系统继续使用现有 `RequireAuth` 跳转登录页，并保留 `/orders` 作为安全回跳地址
