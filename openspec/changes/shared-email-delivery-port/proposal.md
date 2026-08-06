# 公共邮件投递端口

## 背景

认证设计要求 C 提供按 `recipientUserId` 解析已验证邮箱的公共 `EmailDeliveryPort`，供认证和 D 后续提醒能力复用同一 SMTP 配置。当前只有验证码专用 `VerificationEmailSender`，没有跨模块可依赖的类型化端口、模板变量限制和结果查询边界。

## 范围

- C 提供类型化 `EmailDeliveryPort`、命令、结果和 `SENT/FAILED/UNKNOWN` 状态。
- C 在认证模块内部解析 `recipientUserId`，校验账号、已验证邮箱、模板白名单和受控变量。
- C 复用现有 `JavaMailSender`、SMTP 配置和安全日志处理，不建立第二套邮件客户端。
- C 提供稳定 `deliveryKey` 的重复调用和查询语义、Mock/SMTP 实现、单测和给 D 的接口说明。

## 非范围

- 不实现观影提醒触发时间、D 的通知状态、重试、审计、模板业务文案或 `travel` 数据库写入。
- 不修改 D、Agent、订单 Repository、Controller 或表。
- 不保证不支持稳定请求标识/查询的 SMTP 在进程重启后严格去重；此时结果保持 `UNKNOWN`，不得盲目重发。

## Owner 与影响

- Owner：C。
- D：后续只依赖公开端口，提供提醒模板业务内容、`deliveryKey` 和通知状态处理。
- 部署负责人：配置真实 SMTP，并确认 Provider 是否支持稳定请求标识或结果查询。

## 验收结果

已验证且可用的用户可通过类型化端口投递白名单模板；调用方不能传完整邮箱；非法模板/变量被拒绝；重复 `deliveryKey` 不产生第二次 Mock 投递；明确失败、超时未知和查询恢复有明确结果；未配置 SMTP 时安全关闭；实现不访问 D、Agent 或订单的私有层。

