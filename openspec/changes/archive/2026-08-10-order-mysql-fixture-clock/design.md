# 实现设计

测试配置提供 `@Primary Clock.fixed(2026-08-02T00:00:00Z, Asia/Shanghai)`，与固定种子窗口一致。测试 SQL 将 `start_time > CURRENT_TIMESTAMP(3)` 改为参数化的固定业务本地时间。

这样场次选择和订单应用服务的可售校验使用同一业务基准，避免测试依赖运行机器的日期、数据库时钟或执行顺序。该改动只影响测试上下文，不改变生产 Bean。

MySQL 验证仍只连接 `cinewise_ticketing_concurrency_check`；支付、退款并发写入和清理流程保持原样。
