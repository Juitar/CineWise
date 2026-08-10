## 设计

投影层将完整 `BUSINESS_INTENT/SELECT_SEATS` 卡片收窄为 `AgentDisplayItem`，仅使用已校验为无前导零、正 Java `long` 范围内的十进制字符串 `showId`、`movieId` 和 `cinemaId` 构造导航地址。校验只做字符串正则、长度和同长度字典序比较，不转换为 JavaScript `number`。视图不读取原始 payload，也不推断或补齐其他业务参数。

导航地址由模块函数分别使用 `encodeURIComponent` 编码三个 ID 后生成。只有 `kind === 'business-intent'` 且三个 ID 都有效时渲染按钮；点击只执行页面跳转。

测试覆盖合法大整数跳转、任一业务 ID 缺少或非法时拒绝且不推进游标，以及不出现建单/支付按钮。
