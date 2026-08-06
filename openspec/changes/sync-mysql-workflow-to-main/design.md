## 设计

`main` 保存可被 GitHub Actions UI 发现的手动工作流入口；工作流执行时由 GitHub `workflow_dispatch` 的目标 ref 检出代码。工作流内容与 `dev` 当前版本保持一致，包含空库 Flyway、MySQL 8.4 集成、Agent 持久化和重复初始化验证。

工作流使用 GitHub 托管 `ubuntu-latest` Runner 与一次性 MySQL 服务容器，固定 CI 专用账号和测试密钥，不读取 `demo` Environment Secrets，不连接 A 的共享 MySQL、Redis、MinIO，也不启动部署 job。
