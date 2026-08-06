## 1. Compose 与文档

- [x] 1.1 A：显式映射高德天气开关与 Key，默认关闭且 Key 为空；验证：Compose 配置解析。
- [x] 1.2 A：记录服务器 `.env` 注入和不影响购票主链路的降级边界；验证：部署指南复核。

## 2. 部署与联调

- [ ] 2.1 B/A：在 B 应用服务器被忽略的 `.env` 注入轮换后的实际 Key，并重建 backend；验证：Compose 健康检查。
- [ ] 2.2 D：验证已支付订单出行建议在 Provider 正常时返回 `source=AMAP_WEATHER`、`degraded=false`，Provider 缺失或不可用时降级且不影响购票；验证：联调记录。
