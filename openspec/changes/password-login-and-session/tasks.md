## 1. 契约与协作确认

- [x] 1.1 C 确认本次只实现用户密码登录、管理员密码登录、`/auth/me`、登出和双端登录页，不包含验证码、注册和密码重置。
- [x] 1.2 C 对照当前代码和前端主设计确认普通 REST 保留唯一原生 `fetch` 客户端，不引入 Umi request 插件。
- [x] 1.3 D 确认 `GET /api/v1/auth/csrf`、`X-XSRF-TOKEN`、内存保存、公共请求层统一加 Header、写请求不自动重放和业务 DTO 不变。
- [x] 1.4 C/D 确认 REST `CurrentUser` 使用 `id/emailMasked`，内部安全上下文继续使用 `userId/role/tokenVersion`，不得直接返回内部 record。
- [x] 1.5 A/B 已审查 CSRF 公共接口、服务端校验、登录/登出换新、`201009` 处理和浏览器写请求接入方式。
- [x] 1.6 C 已确认 `sys_user`、`sys_login_log` 的字段、索引、可空性、状态、30 天登录日志保留期和兼容要求；A 已确认 `sys_login_log` 作为只追加日志不增加 `update_time`。
- [x] 1.7 A 已为 `sys_user`、`sys_login_log` 正式分配 `V006__create_auth_user_and_login_log_tables.sql`；`V005` 已被票务占用，C 不自行生成、修改或执行最终 SQL。
- [x] 1.8 C 确认注册时隐私政策默认未勾选并显式记录版本；后续登录只沿用已有记录，不自动产生或升级隐私同意。

## 2. 后端认证实现

- [ ] 2.1 查阅当前 Spring Security 官方文档，确认 Spring Boot 3.5 对 JWT 编解码、Cookie CSRF Token 和无状态安全链的 API 用法。
- [ ] 2.2 增加认证安全依赖和 `cinewise.auth` 配置，校验 JWT 密钥、有效期、Cookie 属性和本地开发例外。
- [ ] 2.3 实现账号领域状态、认证错误码、邮箱规范化、邮箱脱敏和 BCrypt 密码校验边界。
- [ ] 2.4 实现 `sys_user`、`sys_login_log` 的认证 Repository 接口和 MyBatis 适配，不创建或修改 Flyway SQL。
- [ ] 2.5 实现用户密码登录、管理员密码登录、当前用户查询和登出 Application Service，保证账号状态、角色和 `tokenVersion` 校验。
- [ ] 2.6 实现 JWT 签发、Cookie 写入/清除、JWT Cookie 过滤器和真实 `CurrentUserAccessor`，替换默认拒绝回退实现。
- [ ] 2.7 在现有单一安全链中接入公开登录、CSRF、认证、ADMIN、401/403、`201009` 和 CORS 规则。
- [ ] 2.8 实现登录、管理员登录、`/auth/me`、登出和 CSRF Controller/DTO，并同步 SpringDoc 契约。
- [ ] 2.9 实现不影响登录结果的脱敏登录审计，确保日志不记录完整邮箱、密码、Cookie 或 JWT。
- [ ] 2.10 实现显式开启的认证演示种子，支持本地 `.test` 邮箱和正式演示真邮箱，环境变量变化不得覆盖已有账号。

## 3. 后端测试

- [ ] 3.1 为邮箱规范化、脱敏、密码校验、JWT 声明和 Cookie 属性增加单元测试。
- [ ] 3.2 使用测试 Repository 覆盖 USER/ADMIN 登录成功、错误凭据统一响应、账号禁用和普通用户不能从管理入口升级。
- [ ] 3.3 覆盖 `/auth/me` 的有效、缺失、过期、账号禁用和 `tokenVersion` 不一致场景。
- [ ] 3.4 覆盖 CSRF 缺失/有效、登出清 Cookie、重复登出和旧 JWT 失效场景。
- [ ] 3.5 覆盖登录日志失败不改变认证结果和敏感字段扫描。

## 4. 前端认证实现

- [ ] 4.1 扩展公共 `apiRequest`，实现 CSRF Token 内存管理、并发获取合并、写请求 Header、登录/登出清理、`201009` 换新和并发 401 单次处理，不自动重放写请求。
- [ ] 4.2 创建 `modules/auth` 的 `CurrentUser`、登录 DTO、API 和共享登录 Hook，登录结果未知时只查询 `/auth/me`。
- [ ] 4.3 创建 `shared/auth` 当前用户 Provider、启动恢复、登出清理、角色判断和私有状态清理入口。
- [ ] 4.4 实现安全 `returnUrl` 工具，拒绝协议、双斜线、反斜线、控制字符和越权管理路径。
- [ ] 4.5 实现 PC Web 用户/管理员密码登录视图，使用 Ant Design 和独立 CSS 文件。
- [ ] 4.6 实现移动 H5 用户/管理员密码登录视图，优先使用 antd-mobile、复用同一 Hook，并保证触控目标不小于 44px。
- [ ] 4.7 增加 `/login`、`/admin/login` 和统一 403 路由；未实现的验证码、注册和重置密码入口不得变成可点击死链接。

## 5. 前端测试

- [ ] 5.1 覆盖登录 DTO、CSRF Header、并发 CSRF 获取、并发 401 单次处理和 403 保留会话。
- [ ] 5.2 覆盖登录提交防重复、结果未知查询 `/auth/me`、匿名后允许用户主动重试和密码清理。
- [ ] 5.3 覆盖 `returnUrl` 合法回跳、外部 URL、双斜线、控制字符、USER 管理路径和 ADMIN 默认页。
- [ ] 5.4 覆盖桌面/移动视图切换、字段校验、加载、错误和无障碍名称。
- [ ] 5.5 增加用户登录刷新恢复、登出失效、普通用户访问管理端和移动视口 E2E 场景。

## 6. 文档、迁移与联调验收

- [ ] 6.1 修正认证设计和前端应用设计中残留的 Umi `request` 描述，统一为现有原生 `fetch` 公共客户端。
- [ ] 6.2 修正后端总系分中认证 REST `CurrentUser.userId/email` 和登录详细接口缺少 `clientRequestId` 的旧描述。
- [ ] 6.3 A 完成 `V006` 最终 SQL、静态审查和 AI 只读复核；C 不自行修改或执行迁移。
- [ ] 6.4 A 在独立空 MySQL 8.4 验证迁移、重复执行、字段、索引、约束、字符集和排序规则，并记录证据。
- [ ] 6.5 使用受控测试账号完成用户/管理员真实 MySQL 登录、刷新恢复、登出、401/403、CSRF 和 Cookie 冒烟。
- [ ] 6.6 执行 `openspec validate password-login-and-session --strict`、后端 `mvnw.cmd verify`、前端 `pnpm check` 和相关 E2E，并记录实际结果。
- [ ] 6.7 导出 `/v3/api-docs`，由 A/B/C/D 复核认证响应、Cookie 安全方案、CSRF Header 和公共错误码。
