# 设计

## 1. 公开边界

在 `auth.application.mail` 提供：

- `EmailDeliveryPort.send(EmailDeliveryCommand)`；
- `EmailDeliveryPort.query(String deliveryKey)`；
- `EmailDeliveryCommand(deliveryKey, templateCode, recipientUserId, variables, traceId)`；
- `EmailDeliveryResult(status, providerMessageId, errorCode)`；
- `DeliveryResultStatus.SENT/FAILED/UNKNOWN`。

D 后续只能依赖这些公开 Application 类型，不能访问认证 Repository 或 Provider Adapter。`recipientUserId` 使用正十进制字符串，C 转为 `sys_user.id` 后读取账号；命令不存在邮箱字段。

## 2. 校验与模板注册

执行顺序为：命令格式→模板注册表→变量名/数量/长度→用户存在性/状态/邮箱验证→安全渲染→Provider 投递。

技术限制：`deliveryKey` 1～160 字符，只允许字母、数字、点、冒号、下划线、短横线；`templateCode` 1～32 位大写下划线；`recipientUserId` 为正十进制字符串；`traceId` 1～64 个安全字符。变量必须是不可变副本，最多 5 项，单值最多 200 字符，总长度不超过 600 字符。

生产注册表只登记认证设计确定的 `VIEWING_REMINDER` 变量名：`movieTitle/cinemaName/startAt/adviceSummary/relativePath`。`relativePath` 必须匹配 `/travel/{taskId}`，其他值执行纯文本转义；变量名白名单会直接拒绝 password、token、code、cookie、secret、精确位置和路线几何等敏感输入。

提醒业务文案不写入代码。部署配置提供 `VIEWING_REMINDER` 的主题和正文模板；未配置时该模板不可投递并安全失败。D 后续负责确认业务文案，C 只替换白名单占位符和执行转义。

## 3. 用户邮箱解析

`UserEmailResolver` 只调用 C 的 `AuthUserRepository.findById`。用户不存在、邮箱未验证、状态不是 `NORMAL` 或角色不可用时返回类型化 `FAILED` 和安全错误码，不抛出完整邮箱。调用方和返回值均看不到邮箱。

## 4. Provider 与结果语义

`EmailProviderPort` 接收 C 内部解析后的邮箱、已渲染主题/正文和稳定 `deliveryKey`。现有验证码发送器与公共端口共用一个 SMTP Provider 适配器和 Spring Boot `JavaMailSender`。

- SMTP 明确接受：`SENT`。
- 参数/认证等明确拒绝：`FAILED`。
- 超时、连接中断、响应丢失或不能确认：`UNKNOWN`。

端口不自动重试。开发/测试 Mock 按 `deliveryKey` 保存首次结果并支持查询；重复 send 返回首次结果且不新增投递。普通 SMTP 没有结果查询能力，进程内只缓存已明确结果用于重复调用；未知或重启后查询保持 `UNKNOWN`。部署验证不得把它描述成严格一次投递。

## 5. 配置、日志与监控

公共端口复用 `spring.mail.*`，新增开关、发件人和模板配置放在 `cinewise.auth.mail-delivery`。开关关闭、SMTP 主机/发件人/模板缺失时安全关闭，不创建看似成功的 Bean。

日志只记录脱敏 `deliveryKey` 摘要、模板码、状态、错误码、耗时和 `traceId`；不记录邮箱、变量值、主题、正文、验证码或 SMTP 配置。单测通过日志捕获扫描敏感测试标识。

## 6. 测试与接口说明

单测覆盖正常用户、用户不存在、未验证、禁用、模板/变量错误、超长或敏感变量、重复 key、明确失败、未知、查询恢复、未配置和日志扫描。ArchUnit/依赖扫描确认不引用 `travel`、`agent`、`order` 的 Repository 或 Controller。

给 D 的说明只描述公开类型、允许变量、结果处理和恢复规则，不要求 D 在本次接入。

