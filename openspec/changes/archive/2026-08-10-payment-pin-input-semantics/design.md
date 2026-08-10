# 实现设计

`PaymentPanel` 使用 Ant Design 普通 `Input`，显式设为 `type="text"`，并使用 `inputMode="numeric"`、`pattern="[0-9]*"` 和 `autoComplete="off"` 提示数字输入及避免自动填充。组件和 CSS 统一使用 PIN 命名，避免密码或账户凭据语义。

本地 state 仅保存六码数字；校验失败不调用 `onPay`，成功时先清空再调用无参数回调。页面容器与支付 API 不接收该值。
