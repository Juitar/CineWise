# 协作确认记录

## 2026-08-02 D 确认

D 已确认：

- `GET /api/v1/auth/csrf` 获取 Token；
- 浏览器写请求统一使用 `X-XSRF-TOKEN`；
- Token 只保存在前端运行内存；
- POST、PUT、PATCH、DELETE 由公共请求层统一添加 Header；
- 超时、断网和 CSRF 失败均不自动重放写请求；
- 各业务模块不读取 Cookie 或 Token，不修改业务 DTO；
- REST `CurrentUser` 使用 `id/role/nickname/emailMasked/emailVerified/status/privacyPolicyVersion`；
- `id` 为十进制字符串，不返回完整邮箱和 `tokenVersion`；
- 后端内部安全上下文继续使用 `userId/role/tokenVersion`，REST 使用独立响应 DTO。

D 要求补充的 CSRF 规则已经写入规格和设计：服务端在 Controller 前比较 Cookie Token 与 `X-XSRF-TOKEN`；登录、登出后旧 Token 失效；CSRF 失败使用 `403/201009`；前端只获取新 Token，不自动重放写请求；权限不足使用 `403/201007`，不刷新 Token。

## 普通 REST 请求层分歧

D 根据《前端应用系统设计》和旧认证设计提出普通 REST 使用 Umi `request`。C 核对当前仓库后保持原生 `fetch`，依据如下：

- `系分文档/前端/妙语购票_前端系统分析设计.md` 第 5.19.2 节明确记录 Umi request 插件已实测撤回，普通 REST 改用唯一原生 `fetch` 客户端；
- `frontend/README.md` 的依赖决策与上述结论一致；
- 当前 `frontend/src/shared/api/client.ts` 已实现并测试原生 `fetch` 客户端；
- 切换到 Umi request 需要增加 `@umijs/plugins` 及无关依赖，没有业务收益，还会与当前代码和锁文件不一致。

因此后续需要修正《前端应用系统设计》《用户认证设计》及仓库 `AGENTS.md` 中残留的 Umi request 描述。此调整只改变公共客户端内部实现说明，不改变 D 或其他模块调用方式、业务 DTO、Cookie、CSRF Header 和错误恢复规则。

## 2026-08-03 A 分配认证迁移版本

A 正式为本次认证迁移分配 Flyway `V006`，预计文件为 `V006__create_auth_user_and_login_log_tables.sql`。该迁移只包含 `sys_user`、`sys_login_log`，不包含演示账号或其他种子数据；`V005` 已由票务迁移占用，不得重复使用。

最终 SQL 审查、AI 只读复核和空 MySQL 8.4 验证由 A 负责。C 在本地编写 SQL 草案并私下交给 A，A 审查通过前不提交、不推送、不执行。C 补充确认：A 的其他确认项按默认同意处理，因此 `sys_login_log` 作为只追加日志不增加 `update_time`。

## 2026-08-03 A 首轮静态审查

A 的结论为“需修改，暂不允许提交、推送或执行”。C 需要为 `sys_login_log` 增加 `idx_login_cleanup_create_time(create_time)` 和登录结果一致性 CHECK：成功日志必须有 `user_id` 且 `failure_code` 为空，失败日志必须有 `failure_code`；同时明确 30 天物理删除是相对于“审计记录不得物理删除”规范的有限保留期例外。

C 修订后只重新私下提交草案。A 负责固化最终 SQL、完成 AI 只读复核和空 MySQL 8.4 验证，并由 A 将验证通过的最终文件直提 `dev`。

## 2026-08-03 C 确认隐私政策处理

注册时隐私政策同意框默认未勾选，用户主动同意后才记录版本和时间。已有账号后续密码登录沿用已记录同意，不把登录自动写成新同意；政策版本更新时必须通过后续独立用例重新主动确认。

## 2026-08-04 C 完成认证文档修正

C 已在 `CineWise-Docs/main` 以提交 `f26a05a` 完成以下修正：

- 《用户认证设计》和《前端应用系统设计》的当前方案统一使用公共原生 `fetch` 客户端 `apiRequest<T>()`，普通 REST 始终携带 `credentials: 'include'`；
- 后端总系分中的认证 REST `CurrentUser` 统一为 `id/role/nickname/emailMasked/emailVerified/status/privacyPolicyVersion`，不返回完整邮箱和 `tokenVersion`；
- 注册、密码登录、验证码登录和管理员登录的详细请求补齐 `clientRequestId`，并修正认证响应示例；
- 本仓库 `AGENTS.md` 同步修正公共请求层说明，避免后续实现重新引入 Umi request 插件。

文档差异检查、Markdown 围栏检查和冲突标记检查均通过，因此任务 6.1、6.2 标记完成。

## 2026-08-04 C 完成 OpenAPI 预检查

C 从当前收尾分支使用测试密钥和内存 H2 启动后端，未读取 `.env`，未连接共享 MySQL/Redis，导出实际 `/v3/api-docs`。候选文件位于本地构建目录 `backend/target/cinewise-auth-openapi.json`，不提交仓库；A 修正意见处理后的最终 SHA-256 为 `4254C9CF1AF84DEDF383D5B1316B28C36FE92364EAFE9651CF95639CFB80A715`。

导出前首次检查发现认证接口只有 `200` 响应，响应 DTO 字段也未标记必填。C 已补充认证 OpenAPI 响应和 Schema 注解，并增加自动化契约测试；修正后的导出结果为：

- `POST /api/v1/auth/login/password`：`200/400/401/403`，请求必填 `clientRequestId/email/password`；
- `POST /api/v1/admin/auth/login`：`200/400/401/403`，请求必填 `clientRequestId/email/password`；
- `GET /api/v1/auth/me`：`200/401`，使用 `cookieAuth`；
- `POST /api/v1/auth/logout`：`200/403`；经 A 复核后明确为 `csrfToken` 必需、`cookieAuth` 可选；
- `GET /api/v1/auth/csrf`：`200`，公开获取 CSRF Token；
- `CurrentUserResponse` 必填字段为 `id/role/nickname/emailMasked/emailVerified/status/privacyPolicyVersion`，不存在 `userId/email/tokenVersion`；
- `cookieAuth` 使用 Cookie `cinewise_access_token`，`csrfToken` 使用 Header `X-XSRF-TOKEN`；
- `201001/201005/201006/201009` 已进入对应 HTTP 错误响应说明。

C 预检查结论为通过。任务 6.7 仍需以下负责人基于同一提交和导出摘要明确回复“确认”或指出问题：

- A：确认订单、支付、退款和管理接口继续使用 `cookieAuth`，浏览器写请求使用 `X-XSRF-TOKEN`，不需要修改 A 的业务 DTO；
- B：确认 Agent POST SSE 和确认写请求继续复用 Cookie/CSRF 规则，会话失效与断流恢复不重发写操作；
- D：确认普通 REST 客户端、`CurrentUserResponse`、401/403 处理以及 D 的业务 DTO 不需要修改。

Cookie 的 `Secure/HttpOnly/SameSite=Lax/Path=/` 属性由 `AuthSecurityAdaptersTest` 和 `AuthControllerIntegrationTest` 验证；OpenAPI 只声明 Cookie 名称与用途，不能替代运行时 `Set-Cookie` 验证。A/B/D 均确认后，C 才可勾选任务 6.7。

## 2026-08-04 D 完成 OpenAPI 复核

D 已确认：普通 REST 请求层、`CurrentUserResponse` 和 401/403 处理不影响 D 的内容、画像、推荐和出行模块；D 现有业务 DTO 不需要调整，后续继续按现有公开接口和错误码联调。

任务 6.7 仍等待 A、B 分别确认，当前不勾选完成。

## 2026-08-04 A 完成 OpenAPI 复核

A 已确认：订单、支付、退款和管理接口继续复用 Cookie 认证；浏览器写请求继续由公共 `apiRequest<T>()` 携带 `X-XSRF-TOKEN`；401、403、超时或断网时不自动重发建单、支付、退票等写请求；`CurrentUserResponse` 不影响 A，A 的业务 DTO 无需修改。

A 指出原 `/api/v1/auth/logout` 同时生成两个独立 Security Requirement 时会被 OpenAPI 解释为二选一，与运行时“CSRF 必需、Cookie 可选”的幂等登出规则不一致。C 已改为只声明 `csrfToken`，并在接口说明及自动化契约测试中明确认证 Cookie 可选。A 的写接口尚未逐项声明 `csrfToken`，由 A 后续补充，不阻断本次认证兼容复核。

任务 6.7 仍等待 B 确认，当前不勾选完成。

## 2026-08-04 B 完成 OpenAPI 复核

B 已确认：后续 Agent POST SSE 和确认写请求使用浏览器 Cookie 与 `X-XSRF-TOKEN`，不读取或自行传递 JWT；遇到 401、403、超时、断网或 SSE 断流时不自动重发原写请求。CSRF 失效只重新获取 Token，仍需用户重新确认；断流恢复只查询已有运行结果或按事件游标续接。

B 已确认现有 `MinimalReadOnlyAgentRequest/Result` 不是 HTTP/SSE DTO，未暴露邮箱、`tokenVersion` 等认证字段，不需要因 `CurrentUserResponse` 调整。当前基准尚未实现 Agent Controller、POST SSE 和确认接口；后续实现必须补充 Cookie/CSRF 与断流不重发测试，本次不把这些接口记为已实现。

## 2026-08-04 完成任务 6.7

A、B、C、D 已完成认证响应、Cookie、CSRF Header、公共错误码和各自业务 DTO 影响复核。C 按 A 的意见修正幂等登出 OpenAPI 安全声明后重新导出 `/v3/api-docs`，最终 SHA-256 为 `4254C9CF1AF84DEDF383D5B1316B28C36FE92364EAFE9651CF95639CFB80A715`；自动化 OpenAPI 契约测试通过，任务 6.7 标记完成。
