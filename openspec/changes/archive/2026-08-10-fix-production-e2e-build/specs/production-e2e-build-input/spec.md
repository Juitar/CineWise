## Purpose

保证 `dev` 分支的生产浏览器测试在独立 GitHub Runner 中使用当前提交生成的前端产物，避免因缺少 `dist/` 目录而在测试开始前失败。

## ADDED Requirements

### Requirement: 生产浏览器测试前生成前端产物
系统 SHALL 在执行 `e2e:production` 前，为当前工作流检出的提交生成前端生产产物。

#### Scenario: 新 Runner 执行生产浏览器测试
- **WHEN** `frontend-e2e` job 在没有已有 `dist/` 目录的 GitHub Runner 上运行
- **THEN** 系统先生成生产产物，再启动生产浏览器测试

#### Scenario: 前端构建失败
- **WHEN** 当前提交无法生成前端生产产物
- **THEN** `frontend-e2e` job 失败，后续浏览器测试和部署不得执行
