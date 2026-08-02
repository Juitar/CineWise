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
