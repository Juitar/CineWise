## ADDED Requirements

### Requirement: 默认分支提供 MySQL 手动验证入口

系统 SHALL 在默认 `main` 分支保存 `Backend MySQL Integration` 工作流，并仅通过 `workflow_dispatch` 触发；工作流 MUST 使用一次性 MySQL 8.4 服务容器和 GitHub 托管 Runner。

#### Scenario: 验证指定分支提交

- **WHEN** 有权限的成员从 Actions 页面选择目标分支或提交运行工作流
- **THEN** 工作流检出该目标 ref 并执行空库 Flyway、MySQL 8.4 集成和 Agent 持久化验证
- **AND** 工作流不访问共享数据库、不读取部署 Secrets、不部署应用
