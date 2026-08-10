# admin-frontend-feedback Specification

## Purpose
TBD - created by archiving change admin-frontend-feedback-fixes. Update Purpose after archive.
## Requirements
### Requirement: 管理订单筛选控件必须提供稳定表单标识

系统 SHALL 为订单号、用户关键词、订单状态、影片 ID、场次 ID 和下单日期筛选控件提供稳定且唯一的 DOM 标识；文本输入 SHALL 同时提供与查询字段一致的 `name`。补充标识不得改变筛选、查询或重置语义。

#### Scenario: 浏览器识别管理订单筛选字段

- **WHEN** 管理员打开订单筛选区域
- **THEN** 各筛选控件具有稳定且唯一的 `id`
- **AND** 文本输入的 `name` 与订单查询白名单字段一致

### Requirement: 内容同步页面不得误报成功

系统 SHALL 仅在内容同步请求返回真实任务时显示成功提示。已知失败、重复提交门禁或结果未知时 MUST NOT 显示成功提示，并继续使用既有错误或结果恢复状态。

#### Scenario: 服务端接受同步请求

- **WHEN** 管理员提交城市且服务端返回同步任务
- **THEN** 页面显示一次“已触发内容同步指令”提示

#### Scenario: 同步请求失败或结果未知

- **WHEN** 内容同步请求失败或响应结果未知
- **THEN** 页面不显示成功提示
- **AND** 页面保留既有错误或原请求标识恢复入口

