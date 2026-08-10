# Design

## 前端

两个开关职责独立：第一个控制画像标签记录和管理，第二个控制相关功能是否使用画像偏好。重新同意画像数据后只刷新页面数据，不自动开启第二个开关。

## 撤回事件

`ProfileConsentWithdrawalHandler` 调用 C 提供的 `ProfileDataConsentWithdrawalGuard`。C 在同一事务中对同意记录加行锁，只有状态仍为撤回且 `consentVersion`、`consentRecordVersion` 都与事件一致时，才执行 D 提供的清理回调。

版本不一致时只写幂等记录，不关闭偏好、不删除标签、不清缓存。这样旧撤回事件晚到时不会清理用户重新同意后的新状态。
