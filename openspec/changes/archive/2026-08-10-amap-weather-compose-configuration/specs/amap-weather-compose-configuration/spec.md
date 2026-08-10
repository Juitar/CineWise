## ADDED Requirements

### Requirement: backend 显式接收高德天气配置

应用 Compose SHALL 将 `AMAP_WEATHER_ENABLED` 与 `AMAP_WEATHER_KEY` 显式传入 backend 容器。前者默认 `false`，后者默认空；Compose MUST NOT 要求未启用环境提供 Key。

#### Scenario: 未配置高德天气

- **WHEN** 服务器 `.env` 未设置 Key 或开关保持默认
- **THEN** backend 仍可启动
- **AND** 出行建议由 D 的降级策略处理，不影响购票、支付或订单

#### Scenario: 已配置高德天气

- **WHEN** 应用服务器被忽略的 `.env` 设置开关为 `true` 且提供实际 Key
- **THEN** 重建 backend 后容器可读取该配置
- **AND** 实际 Key 不出现在仓库、环境模板、镜像构建参数或部署日志
