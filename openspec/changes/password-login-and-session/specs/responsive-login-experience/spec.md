## Purpose

为 PC Web 和移动 H5 提供共享认证状态与接口逻辑、适配不同屏幕的密码登录页面，以及在弱网、响应丢失和会话失效情况下可安全恢复的交互。

## ADDED Requirements

### Requirement: PC 与移动端共享登录业务逻辑
前端 SHALL 提供 `/login` 和 `/admin/login` 路由。两端 SHALL 共用认证 DTO、API、状态 Hook、错误映射和结果恢复逻辑；桌面端使用 Ant Design，移动端优先使用 antd-mobile，视图以 `1024px` 为断点适配。

#### Scenario: 桌面端打开用户登录
- **WHEN** 视口宽度大于等于 `1024px` 且用户访问 `/login`
- **THEN** 页面展示桌面双栏登录布局和邮箱密码表单

#### Scenario: 移动端打开用户登录
- **WHEN** 视口宽度小于 `1024px` 且用户访问 `/login`
- **THEN** 页面展示移动单列布局，触控目标不小于 44px，且不复制另一套 API 或状态逻辑

### Requirement: 登录页面只提交本次范围字段
密码登录表单 SHALL 只提交 `clientRequestId`、`email` 和 `password`。页面 SHALL 不展示不可用的验证码登录、注册或密码重置操作，也不得把隐私同意勾选作为密码登录条件。

#### Scenario: 用户提交密码登录
- **WHEN** 用户填写合法邮箱和密码并主动提交
- **THEN** 页面为本次操作生成一次稳定 `clientRequestId`，提交期间禁用重复点击，且不把密码写入 URL 或持久化存储

#### Scenario: 展示协议入口
- **WHEN** 登录页面需要展示用户协议或隐私政策说明
- **THEN** 页面可以展示可访问的说明链接，但不会要求已有账号在每次登录时重复提交隐私同意字段

### Requirement: 登录后以当前用户接口建立身份
登录接口明确成功后，前端 SHALL 再调用一次 `/api/v1/auth/me`，并仅以该结果更新全局当前用户。用户登录 SHALL 只进入用户可访问路径，管理员登录 SHALL 只进入管理端路径。

#### Scenario: 登录成功并安全回跳
- **WHEN** `/auth/me` 返回有效用户且 `returnUrl` 是允许的站内相对路径
- **THEN** 前端导航到该路径并清除登录表单中的密码

#### Scenario: 非法回跳地址
- **WHEN** `returnUrl` 包含协议、双斜线、控制字符、反斜线或当前角色无权访问的管理路径
- **THEN** 前端忽略该值并导航到当前角色的安全默认页

### Requirement: 登录响应未知时不重复提交密码
密码登录遇到超时或网络中断时，前端 SHALL 进入结果未知状态并调用 `/api/v1/auth/me` 查询会话；不得自动重发密码登录请求或生成新的 `clientRequestId`。

#### Scenario: 登录成功但响应丢失
- **WHEN** 密码登录在服务端成功后前端没有收到响应，随后 `/auth/me` 返回有效身份
- **THEN** 前端按已登录处理并继续安全回跳，不发送第二次密码

#### Scenario: 查询后仍为匿名
- **WHEN** 登录结果未知且 `/auth/me` 返回 401
- **THEN** 前端清除密码、恢复可提交状态并提示用户主动重试

### Requirement: 应用启动和并发 401 统一处理
应用启动 SHALL 在渲染受保护内容前查询 `/auth/me`。多个并发请求同时返回 401 时，前端 SHALL 只执行一次会话清理和一次登录跳转；403 SHALL 保留当前登录状态。

#### Scenario: 刷新后恢复会话
- **WHEN** 用户刷新页面且认证 Cookie 仍有效
- **THEN** 应用在检查期间显示加载状态，随后恢复用户并渲染允许访问的页面

#### Scenario: 多个请求同时返回 401
- **WHEN** 多个业务请求在同一时间发现会话失效
- **THEN** 前端只清理一次私有状态、只跳转一次登录页并保留一个安全回跳地址

#### Scenario: 已登录用户收到 403
- **WHEN** 用户访问角色不允许的页面或接口
- **THEN** 前端展示无权限状态并保留有效登录会话，不循环跳转登录

### Requirement: 前端不持久化认证秘密
密码、JWT、Cookie 和 CSRF Token SHALL 只存在于必要的组件或运行内存中，不得写入 localStorage、sessionStorage、URL、普通日志、埋点或错误详情。

#### Scenario: 离开登录页面
- **WHEN** 用户登录成功、取消或离开登录页面
- **THEN** 页面清除密码和临时 CSRF Token 引用，不在浏览器持久化存储中留下认证秘密
