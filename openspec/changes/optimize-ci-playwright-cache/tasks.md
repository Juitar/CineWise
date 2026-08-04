## 1. 范围与设计

- [x] 1.1 A 确认第一阶段纯文档 PR 验收通过，并记录 PR #35 的成功与关闭状态。
- [x] 1.2 A 核对现有 Maven、pnpm、Docker BuildKit、timeout 和 concurrency，冻结本次仅优化 Playwright 浏览器缓存。

## 2. 实现

- [x] 2.1 A 为 Frontend Verify 增加 Linux Playwright Chromium 缓存。
- [x] 2.2 A 为 Demo Deploy 前端验证增加相同缓存路径和键规则。

## 3. 静态验证

- [x] 3.1 A 完成 workflow 格式、YAML/actionlint、OpenSpec strict 和 Git 差异检查。

## 4. GitHub 验证

- [ ] 4.1 A 在 PR 首次运行确认 cache miss 时全部前端质量门通过。
- [ ] 4.2 A 在相同锁文件下确认后续运行 cache hit，且浏览器测试未被跳过。
- [ ] 4.3 合并到 dev 后确认 Demo Deploy 前端验证可以恢复缓存并正常完成部署门禁。
