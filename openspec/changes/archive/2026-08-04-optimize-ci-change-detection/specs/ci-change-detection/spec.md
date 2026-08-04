## Purpose

让前后端 CI 只在相关工程文件变化时执行重型验证，同时确保纯文档 PR 仍能得到明确成功的必需检查结果。

## ADDED Requirements

### Requirement: 前端质量门按变更范围执行

`Frontend Verify` SHALL 始终创建 `verify` job，并仅在 `frontend/**`、前端 workflow 或共用变更检测脚本发生变化时执行依赖安装、静态检查、构建和浏览器测试。

#### Scenario: 纯文档或 OpenSpec 变更

- **WHEN** PR 或 push 只修改 Markdown、`docs/**` 或普通 `openspec/**`
- **THEN** 前端 job 完成范围判断后成功结束，不安装 Node 依赖、不构建前端且不运行浏览器测试

#### Scenario: 前端文件变更

- **WHEN** 变更包含 `frontend/**`
- **THEN** 前端 job 执行现有全部前端质量门和浏览器测试

#### Scenario: 前端 workflow 或检测脚本变更

- **WHEN** 变更包含 `.github/workflows/frontend-verify.yml` 或共用检测脚本
- **THEN** 前端 job 执行完整质量门，避免变更检测逻辑绕过自身验证

### Requirement: 后端质量门按变更范围执行

`Backend Verify` SHALL 始终创建 `verify` job，并仅在 `backend/**`、后端 workflow 或共用变更检测脚本发生变化时执行 Java 与 Maven 验证。

#### Scenario: 仅前端文件变更

- **WHEN** 变更包含前端文件但不包含后端工程、后端 workflow 或共用检测脚本
- **THEN** 后端 job 完成范围判断后成功结束，不安装 Java 且不运行 Maven

#### Scenario: 后端文件变更

- **WHEN** 变更包含 `backend/**`
- **THEN** 后端 job 执行现有完整 Maven Verify

### Requirement: 混合变更执行全部相关质量门

系统 SHALL 分别判断前端和后端范围，不得因一个工程无变化而跳过另一个工程的必要验证。

#### Scenario: 同时修改前后端

- **WHEN** 同一 PR 或 push 同时包含 `frontend/**` 和 `backend/**`
- **THEN** 前端和后端 job 均执行各自完整质量门

### Requirement: 过时 CI 自动取消

前后端 workflow SHALL 以 PR 编号或分支引用建立并发组，并在同组新运行开始时取消旧运行。

#### Scenario: PR 连续推送

- **WHEN** 同一 PR 在上一轮 CI 未结束时推送新提交
- **THEN** GitHub Actions 取消旧运行并只继续验证最新提交

### Requirement: 无法安全判断时执行完整验证

变更检测 SHALL 在首次 push、缺少基准 SHA 或 Git 对象不可用时选择执行完整质量门，不得把判断异常当成“无相关变化”。

#### Scenario: push 前置 SHA 全为零

- **WHEN** GitHub 事件没有可比较的前置提交
- **THEN** 对应 workflow 执行完整质量门并记录回退原因
