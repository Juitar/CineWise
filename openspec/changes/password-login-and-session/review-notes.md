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

最终 SQL、静态审查、AI 只读复核和空 MySQL 8.4 验证由 A 负责，C 不自行生成、修改或执行最终迁移。A 仍需确认 `sys_login_log` 作为只追加日志不增加 `update_time` 的例外。

## 2026-08-03 C 确认隐私政策处理

注册时隐私政策同意框默认未勾选，用户主动同意后才记录版本和时间。已有账号后续密码登录沿用已记录同意，不把登录自动写成新同意；政策版本更新时必须通过后续独立用例重新主动确认。
