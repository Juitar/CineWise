# 公共邮件投递端口使用说明

本说明供 D 后续接入；当前 change 不修改 D 模块。

## 调用方式

D 只注入 `com.miaoyu.ticket.auth.application.mail.EmailDeliveryPort`，调用 `send` 或按原 `deliveryKey` 调用 `query`。命令字段固定为：

- `deliveryKey`：稳定且唯一，最长 160 字符；提醒建议继续使用设计中的 `VIEWING_REMINDER:{taskId}:{taskVersion}:{triggerType}`。
- `templateCode`：本期为 `VIEWING_REMINDER`。
- `recipientUserId`：认证 `sys_user.id` 的十进制字符串，不传邮箱。
- `variables`：只允许 `movieTitle/cinemaName/startAt/adviceSummary/relativePath`。
- `traceId`：调用请求或任务的追踪标识。

`relativePath` 只能是 `/travel/{taskId}`。不要传 Token、验证码、完整订单、精确位置或路线几何。

## 结果处理

- `SENT`：Provider 明确接受，D 可以按自己的通知规则更新状态。
- `FAILED`：Provider 明确拒绝或输入/账号不可用；是否重试由 D 的通知规则决定。
- `UNKNOWN`：结果无法确认。禁止生成新 `deliveryKey` 或直接重投，只能用原 key 调用 `query`；仍未知时保留告警和待处理状态。

C 不负责提醒触发时间、通知记录、重试次数、业务文案或 `travel` 表写入。真实 SMTP 不支持稳定查询时，系统不会宣称严格一次投递。
