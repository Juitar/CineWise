## Why

当前 `Frontend Verify` 和 `Backend Verify` 对所有 PR 及所有进入 `main/dev` 的提交执行完整质量门。纯 Markdown 或 OpenSpec 变更也会安装前后端依赖、构建应用并运行浏览器测试，且在 PR 与合并后的 push 阶段重复消耗 Runner 时间。

## What Changes

- 保留前后端 workflow 始终触发和原有 `verify` job 名称，在 job 内先用 Git 判断本次提交范围。
- 仅当前端目录、前端 workflow 或共用检测脚本变化时执行完整前端质量门。
- 仅当后端目录、后端 workflow 或共用检测脚本变化时执行完整后端质量门。
- 纯文档和 OpenSpec 变更只执行轻量 checkout 与范围判断，并让对应 `verify` job 正常成功。
- 前后端 workflow 增加同一 PR/分支的并发取消，后续提交终止过时运行。

## Capabilities

### New Capabilities

- `ci-change-detection`: 前后端 CI 按变更范围执行重型质量门，同时维持稳定的必需检查结果。

### Modified Capabilities

无。

## Impact

- `.github/workflows/frontend-verify.yml`：增加前端范围判断、条件步骤和并发取消。
- `.github/workflows/backend-verify.yml`：增加后端范围判断、条件步骤和并发取消。
- `.github/scripts/`：增加前后端共用的原生 Git 变更检测脚本。
- 不修改前后端代码、API、数据库、Flyway、部署触发范围或现有质量门命令。
