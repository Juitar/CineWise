## 设计

投影层将完整 `BUSINESS_INTENT/SELECT_SEATS` 卡片收窄为 `AgentDisplayItem`，仅保留 `showId` 供视图导航。视图不读取原始 payload，也不拼接 `movieId`、`cinemaId` 或其他业务参数。

导航地址由模块函数使用 `encodeURIComponent(showId)` 生成。只有 `kind === 'business-intent'` 且存在有效 `showId` 时渲染按钮；点击只执行页面跳转。

测试覆盖有效跳转、缺少 showId 无入口、URL 编码和不出现建单/支付按钮。
