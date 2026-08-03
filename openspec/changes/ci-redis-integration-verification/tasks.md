## 1. 范围与安全边界

- [x] 1.1 A 确认 Redis CI 只使用一次性容器，不连接共享云端 Redis，不接收生产密码。（验证：proposal、design 和 spec 明确隔离边界）
- [x] 1.2 确认现有快速后端验证保持不变，Redis 集成结果独立展示。（验证：未修改 `backend-verify.yml`）

## 2. CI 实现

- [x] 2.1 新增 Redis 7.4.10 服务、PING 健康检查和最小只读权限。（验证：workflow 静态解析通过）
- [x] 2.2 显式开启 Redis 集成测试并使用 Spring 标准本机连接变量。（验证：workflow 环境变量核对通过）

## 3. 验证与交付

- [x] 3.1 OpenSpec 严格校验、`git diff --check` 和后端完整 `verify` 通过；后端 65 个测试中 60 通过、0 失败、5 个外部环境测试按门控跳过。PR #10 的真实 Redis 测试另在一次性 Redis 7.4.10 上 1/1 通过。
- [x] 3.2 GitHub Actions 的 Redis 服务健康、真实 Redis 测试和完整后端验证通过；PR #11 的 Backend Redis Integration Run 30799769207 共执行 80 个测试，0 失败、0 错误，真实 Redis 用例 1/1 通过。
