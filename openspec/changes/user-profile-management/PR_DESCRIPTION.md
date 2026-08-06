# 用户画像模块 PR 说明

## 修改范围

- 本人画像标签、个性化开关、版本冲突和幂等重放。
- 行为最小记录、支付成功后的受控接入、衰减和缓存摘要。
- 同意撤回后的停用、分期清理、最小审计与指标。
- 推荐读取最小画像摘要，仅在本轮明确影院与长期偏好一致时记录采用证据。

## 已验证

- `mvn verify`
- `openspec validate user-profile-management --strict`
- `git diff --check`

## 待其他负责人确认

- B：`GetProfileSummaryTool` 注册、稳定 `planId`、确认后的对话偏好输入。
- C：正式 `ProfileDataConsentQuery`、撤回通知投递、画像 REST 前端展示。
- A：V010 后的受控 MySQL/Redis 实库验证和迁移授权。

## 隐私边界

- 不读取 `sys_user` Repository，不保存邮箱、完整对话、原始行为 payload 或精确位置。
- 未接入 C 的同意查询时默认拒绝画像写入。
- 推荐结果只返回实际采用的标签类型、值、极性和来源，不返回完整画像。
