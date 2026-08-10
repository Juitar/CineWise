# 高德天气 Compose 配置

## 背景

D 的出行建议可选使用高德天气 Provider，但应用 Compose 尚未将开关与 Key 传入 backend 容器。

## 范围

- 为 backend 显式映射 `AMAP_WEATHER_ENABLED` 与 `AMAP_WEATHER_KEY`。
- 默认关闭 Provider；Key 为空时不阻止 backend 启动。
- 记录服务器 `.env` 注入与降级边界。

## 非范围

- 不实现或修改 D 的天气查询业务代码、DTO、缓存或降级逻辑。
- 不提交实际 Key，不修改 `.env.server.example`，不修改数据服务器或安全组。
- 不使购票、支付或订单流程依赖天气 Provider。

## Owner 与验收

- A：维护 Compose 与服务器部署注入。
- D：验证已支付订单出行建议的高德来源和降级语义。
