## Why

`Demo Deploy` 的前端浏览器测试运行在新的 GitHub Runner 中。PR 的前端构建产物不会自动传到这个 job，因此生产浏览器测试找不到 `frontend/dist/index.html` 并失败。

## What Changes

- 在 `frontend-e2e` job 的生产浏览器测试前生成前端生产产物。
- 不重新执行前端格式、Lint、类型检查或单元测试。

## Capabilities

### New Capabilities

- `production-e2e-build-input`: 确保生产浏览器测试启动前存在当前提交生成的前端生产产物。

### Modified Capabilities

- 无。

## Impact

- 仅影响 `.github/workflows/demo-deploy.yml` 的 `frontend-e2e` job。
- 不修改部署、MySQL、Redis、PR 快检查或业务代码。
