## Why

后端快速 CI 不提供 Redis，新增真实 Redis 集成测试时只能失败或被环境变量跳过，无法在 PR 合并前证明缓存适配器与目标 Redis 版本兼容。

## What Changes

- 新增独立的后端 Redis 集成工作流，在一次性 Redis 7.4.10 容器健康后运行后端验证。
- 在该工作流显式开启 `REDIS_INTEGRATION_ENABLED`，让已有真实 Redis 测试参与质量门。
- 保留现有无外部服务的快速后端验证，不连接共享云端 Redis，也不使用生产密码或数据。

## Capabilities

### New Capabilities

- `ci-redis-integration-verification`: 使用隔离 Redis 容器验证后端 Redis 适配器和降级边界。

### Modified Capabilities

无。

## Impact

- `.github/workflows/`：新增一个 Redis 集成质量门。
- 后端运行代码、REST、数据库、权限和部署配置均不变化。
- CI 时间增加一次后端验证；Redis 容器随 GitHub Runner 销毁。
