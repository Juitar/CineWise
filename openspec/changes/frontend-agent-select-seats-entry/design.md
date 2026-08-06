## 设计

投影层将完整 `BUSINESS_INTENT/SELECT_SEATS` 卡片收窄为 `AgentDisplayItem`，仅使用已校验为无前导零正十进制字符串的 `showId`、`movieId` 和 `cinemaId` 构造导航地址。视图不读取原始 payload，也不推断或补齐其他业务参数。

导航地址由模块函数分别使用 `encodeURIComponent` 编码三个 ID 后生成。只有 `kind === 'business-intent'` 且三个 ID 都有效时渲染按钮；点击只执行页面跳转。

测试覆盖有效跳转、任一业务 ID 缺少或非十进制时无入口、不出现建单/支付按钮。
