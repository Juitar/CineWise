## 1. 计划

- [x] 1.1 Owner：C；确认 B 的 `SELECT_SEATS` 外层字段和 A 的选座路由只要求 `showId`。

## 2. 实现

- [x] 2.1 Owner：C；在安全投影中保存已校验的 `showId`，提供只读导航地址。
- [x] 2.2 Owner：C；在 Agent 工作区展示选座入口，不生成任何写操作按钮。

## 3. 验证

- [x] 3.1 Owner：C；补充投影、页面和 URL 编码测试。
- [x] 3.2 Owner：C；运行 `pnpm check`、生产浏览器测试、OpenSpec 严格校验和 Git 检查。
