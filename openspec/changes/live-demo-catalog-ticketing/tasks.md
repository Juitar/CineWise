## 1. 规格与实现

- [x] 1.1 确认依赖 D 的 `DemoPurchaseCatalog` 公开 Application API
- [x] 1.2 让种子初始化消费全量真实影院目录，并按 D 确认的 `dataAt + 24h` 演示引用窗口判断时效
- [x] 1.3 补充真实沙箱优先、过期目录和重复初始化测试
- [x] 1.4 增加 MySQL 清理-锁座竞争测试，并接入 Backend MySQL Integration 流程
- [x] 1.5 补充保留历史场次不阻塞后续清理的回归测试
- [ ] 1.6 在隔离 MySQL 执行清理-锁座竞争测试，完成后端定向测试、OpenSpec strict 与 `git diff --check`
