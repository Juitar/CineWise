# payment-pin-input-semantics Specification

## Purpose
TBD - created by archiving change payment-pin-input-semantics. Update Purpose after archive.
## Requirements
### Requirement: 模拟支付六码不使用密码控件语义

系统 SHALL 使用普通文本数字输入呈现模拟支付六码，不得使用 `type="password"`、`Input.Password`、密码字段名称或密码自动填充语义。

#### Scenario: 浏览器凭据管理器

- **WHEN** 用户打开模拟支付页面
- **THEN** 六码输入不被标记为登录或更新密码凭据

### Requirement: 六码只用于本地格式校验

系统 SHALL 只在 `PaymentPanel` 内存中短暂保存六码；仅六位数字可触发无参数 `onPay`，调用前 MUST 清空输入。六码不得进入页面容器、HTTP 请求、日志或持久化存储。

#### Scenario: 六位数字提交

- **WHEN** 用户输入六位数字并确认支付
- **THEN** 组件清空 PIN 后调用一次 `onPay`，调用方不接收 PIN

