## 1. 工作流调整

- [x] 1.1 将后端和前端 PR 快速检查改为 GitHub 官方 Runner，并保留既有路径识别与快速检查命令。
- [x] 1.2 从前端快速检查中移除 Playwright 与三项浏览器测试。
- [x] 1.3 将独立 MySQL、Redis 工作流改为仅手动触发的 GitHub 官方 Runner 诊断入口。
- [x] 1.4 将 `dev` 部署工作流改为 MySQL、Redis、前端浏览器测试、部署的串行顺序，并删除重复快速检查。

## 2. 文档和验证

- [x] 2.1 更新演示部署指南，删除自建 Runner 和 ESC 自救方案，说明 GitHub 官方 Runner 与 `demo` Environment 要求。
- [x] 2.2 校验工作流 YAML、OpenSpec、变更范围和文本格式。
