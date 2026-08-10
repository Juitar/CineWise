# Design

## 前端

`useProfile.setConsentEnabled(true)` 在同意接口成功后只重新读取画像页面，不调用 `updateMyPersonalization(true)`。第一个开关的说明写明它控制画像标签的记录和管理；第二个开关的说明写明它只控制相关功能是否使用画像偏好，两个开关保持独立。

## 撤回事件

`ProfileConsentWithdrawalHandler` 处理撤回事件前通过 `ProfileDataConsentQuery` 查询当前同意状态。若当前状态已经是 `granted=true`，说明该 outbox 事件已经过期，只写入幂等记录并跳过 `disable`、`softDeleteAll` 和缓存清理；若仍未同意，则沿用原有清理流程。

这只处理“旧撤回事件晚于重新同意到达”的最小场景，不改变同意记录、画像偏好和标签的表结构。
