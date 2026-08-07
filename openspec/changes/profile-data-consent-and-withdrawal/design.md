# 设计

## 查询与事件类型

最终查询签名为 `ProfileDataConsentSnapshot findByUserId(long userId)`。快照字段：`granted` 是当前是否允许保存画像；`consentVersion` 是每次重新同意递增的业务版本；`version` 是同意记录 CAS 版本；`grantedAt` 是最近同意时间；`withdrawnAt` 是最近撤回时间。

没有记录固定返回 `false, 0, 0, null, null`。撤回后保留原 `consentVersion`、最近同意时间，设置撤回时间并递增 `version`。

撤回事件字段为 `eventId`、`userId`、`consentVersion`、`consentRecordVersion`、`occurredAt`、`traceId`，时间统一使用 `Instant`。D 按 `eventId` 去重。

## 分层与事务

`ProfileDataConsentService` 位于 auth application，只依赖同意仓储、outbox 仓储和投递应用服务。MyBatis Mapper、仓储实现、Spring 事件发布器和调度任务位于 auth infrastructure。

撤回应用方法使用本地 `@Transactional`：先按 `recordVersion` CAS 更新 `sys_profile_data_consent`，再插入具有唯一 `eventId` 和 `(userId, consentRecordVersion)` 的 outbox。任何异常都使两步一起回滚。事务提交后注册的回调只触发首次投递；即使进程在回调前退出，定时扫描仍会从 outbox 恢复。

## 重试与人工恢复

首次提交后立即投递。每次自动失败增加 `retryCount`，下一次分别安排在 1、5、15、60、360 分钟后，之后固定 6 小时。第十次失败标记 `EXHAUSTED`。管理员人工恢复只读取原 `EXHAUSTED` 行并发布原事件，成功改为 `DELIVERED`；失败保持原状态和计数。

## 前端

个人中心只组合 `useProfile`。API、页面内存数据和错误处理位于 `modules/profile`。`202004` 是明确的隐私结果：立即把 profile 置空并提示“未开启画像数据使用”，不自动重试，也不调用同意状态接口。409 只刷新最新读取结果，不重发原写请求。

## 验证

- 单元测试覆盖首次/重复同意、撤回、重复撤回、CAS 冲突、outbox 失败、重试间隔、耗尽、人工恢复和事件字段。
- H2/MySQL 集成测试覆盖同事务回滚和唯一约束；Redis 与 D 的消费者测试覆盖缓存清除和 Redis 不可用。
- 前端测试覆盖 `202004` 清除、401/403/409/422/5xx 和移动布局。
