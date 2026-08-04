## 1. 范围与设计

- [x] 1.1 A 确认第一阶段纯文档 PR 验收通过，并记录 PR #35 的成功与关闭状态。
- [x] 1.2 A 核对现有 Maven、pnpm、Docker BuildKit、timeout 和 concurrency，冻结本次仅优化 Playwright 浏览器缓存。

## 2. 实现

- [x] 2.1 A 为 Frontend Verify 增加 Linux Playwright Chromium 缓存。
- [x] 2.2 A 为 Demo Deploy 前端验证增加相同缓存路径和键规则。

## 3. 静态验证

- [x] 3.1 A 完成 workflow 格式、YAML/actionlint、OpenSpec strict 和 Git 差异检查。

## 4. GitHub 验证

- [x] 4.1 A 在 Frontend Verify run `30891682543` 第一次执行确认 Playwright primary key cache miss，完整前端质量门成功，job 结束时保存 `Linux-playwright-chromium-56f19650b0a27b79b631df632d5d7250b2d656a458a1ed9d4026b128e02dba5c`。
- [x] 4.2 A 在同一 run 第二次执行确认恢复上述 primary key；`playwright install --with-deps chromium` 正常执行且未重新下载 Chromium，生产 E2E 2/2、生产 Nginx 冒烟 4/4 通过，开发 E2E 最终成功但记录 `1 flaky, 3 passed`。
- [ ] 4.3 合并到 dev 后确认 Demo Deploy 前端验证可以恢复缓存并正常完成部署门禁。
