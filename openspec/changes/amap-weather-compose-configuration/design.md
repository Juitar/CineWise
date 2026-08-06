## 设计

Compose 只通过显式 `environment` 映射将非敏感开关和服务器 `.env` 中的 Key 传入 backend。默认 `AMAP_WEATHER_ENABLED=false`，`AMAP_WEATHER_KEY` 默认空，保证本机、CI 和未配置服务器不会意外调用外部 Provider。

真实 Key 只保留在 B 应用服务器被 Git 忽略的 `.env`；不使用 GitHub Secret、Dockerfile、环境模板或日志传递。天气查询属于 D 的可选出行建议能力，调用失败必须由 D 降级，不能改变 A 的支付、订单或库存权威状态。
