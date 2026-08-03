## Context

现有 `backend-verify.yml` 负责快速编译、单元/上下文测试和静态检查，不启动外部服务。Redis 集成测试通过环境变量显式开启，以避免普通开发环境缺少 Redis 时产生偶发失败。项目现在需要让这些测试在 CI 中有稳定、隔离且可重复的执行环境。

## Decisions

### 1. Redis 集成验证使用独立工作流

保留快速后端验证，并新增 `backend-redis-integration.yml`。独立结果便于区分代码质量失败与 Redis 集成失败，也避免把共享云端服务变成 PR 的外部依赖。

### 2. 使用与项目一致的固定 Redis 版本

工作流启动 `redis:7.4.10-alpine`，通过 `redis-cli ping` 健康检查后再运行 Maven。CI 容器不保存业务数据，任务结束后自动销毁。

### 3. 只通过 Spring 标准环境变量连接本机服务

工作流设置 `REDIS_INTEGRATION_ENABLED=true`、`SPRING_DATA_REDIS_HOST=127.0.0.1` 和 `SPRING_DATA_REDIS_PORT=6379`。不读取 `.env`、GitHub Secret 或云端 Redis 地址。

### 4. 后端变更触发完整 Redis 验证

当前项目规模下，Redis 容器启动成本较低，后端变更统一执行完整 `verify`。这样现有和未来由相同环境门控制的 Redis 集成测试都会自动参与，不维护容易遗漏的测试类名单。

## Risks / Trade-offs

- 完整验证会与快速后端 CI 重复部分工作，但结果边界清楚，且避免指定测试类后新增用例未被执行。
- CI Redis 不启用密码；这里只验证缓存读写与故障边界，带密码的部署配置继续由 Compose 健康检查覆盖。
- Redis 集成测试尚未进入目标分支时，工作流仍会验证容器健康和后端基线；对应业务 PR 合入后测试会自动启用。

## Rollback Plan

若该工作流自身不稳定，回退新增 workflow 和本 change 即可；现有快速后端 CI 不受影响。
