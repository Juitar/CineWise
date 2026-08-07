# Tasks

- [x] 1. C：在 `modules/profile` 增加画像同意和撤回 API；验证：API 单元测试覆盖路径、方法和请求体。
- [x] 2. C：扩展 `useProfile` 的同意状态、写操作和只读恢复；验证：Hook 测试覆盖成功、清理、冲突、结果未知和失败状态。
- [x] 3. C：在个人中心增加独立画像数据使用开关并兼容移动端；验证：页面测试覆盖两个开关、提交禁用和未同意状态。
- [x] 4. C：执行前端格式、Lint、类型、测试和生产构建；验证：105 个测试文件、566 个测试通过，格式、Lint、类型检查、生产构建和 Nginx 隐私检查通过。
- [x] 5. C：执行 `openspec validate profile-consent-toggle --strict`、`git diff --check` 和变更范围核对；验证：严格校验和差异检查通过，只修改 `modules/profile`、个人中心及本 change。
