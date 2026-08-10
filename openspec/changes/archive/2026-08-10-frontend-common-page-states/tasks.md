## 1. 规划和边界

- [x] 1.1 C 创建 proposal、spec、design 和 tasks，并确认只统一页面查询状态。（验证：`openspec validate frontend-common-page-states --strict`）
- [x] 1.2 C 核对影片列表现有 Hook、Freshness、URL 与分页规则，确认不修改接口和业务状态。（验证：代码与现有测试检查）

## 2. 公共组件

- [x] 2.1 C 实现独立页面状态组件和导出 API，写明用途与调用限制。（验证：TypeScript、ESLint 和 JSDoc 检查）
- [x] 2.2 C 实现桌面/移动共用样式及不小于 44px 的触控按钮。（验证：CSS 检查和组件结构测试）
- [x] 2.3 C 增加公共组件测试，覆盖全部规定状态、问题编号、重试和危险文本转义。（验证：定向 Vitest）

## 3. 影片列表接入

- [x] 3.1 C 替换影片页首次加载、空数据、失败、刷新和离线快照提示，刷新时保留旧数据。（验证：影片页定向 Vitest）
- [x] 3.2 C 保留 Mock、降级、过期、来源说明、URL 筛选和分页行为。（验证：影片页回归测试）
- [x] 3.3 C 核对没有修改禁止目录和其他页面。（验证：`git status --short` 和变更文件列表）

## 4. 交付检查

- [x] 4.1 C 执行 `pnpm check`、公共组件与影片页定向 Vitest、现有影片浏览器测试。（验证：记录通过数、失败数和跳过数）
- [x] 4.2 C 执行 OpenSpec 严格校验、`git diff --check` 和最终 Git 状态检查。（验证：命令全部通过）
