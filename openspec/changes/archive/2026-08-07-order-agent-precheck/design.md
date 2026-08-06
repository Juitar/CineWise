# 实现设计

## 分层与调用方向

`CreateOrderTool.validate` 位于 order/api，负责把 B 的字符串业务 ID转换为 A 内部 long 并包装安全结果；它调用 `OrderApplicationService.validateOrderSelection`。订单应用服务调用 ticketing 的公开 `SeatLockService.precheckSeats`，不直接访问票务 Repository。

## 预检规则

`SeatLockService.precheckSeats` 复用写建单相同的数量、正数、去重、场次状态和业务时钟规则，仅执行 `findShow/findSeats` 查询。场次必须是 `ON_SALE` 且 `startTime > now`；全部请求座位必须被查回且状态为 `AVAILABLE`。预检不调用 `lockSeat`。

## 错误与隐私

`CreateOrderTool.validate` 将参数异常映射为 100001，领域状态异常保留稳定领域错误码，查询基础设施异常映射为 306003。结果只含 `boolean executable` 与可空整数 `errorCode`，不把异常原文、座位状态、价格或订单对象返回给 B。

## 一致性边界

预检是 advisory read，不持有锁、不承诺后续写入成功。`CreateOrderTool.execute` 和 `OrderCreationTransaction` 保持现有事务、服务端金额和 MySQL 条件更新作为最终权威。预检和写入之间的竞态由写操作正常返回 `SEAT_NOT_LOCKABLE` 处理。

## 数据库与迁移

本 change 只增加查询方法和测试，不修改表结构、Flyway 或种子，不申请迁移版本。
