# 管理端内容同步状态

## ADDED Requirements

### Requirement: 管理员查看真实内容源状态

页面 SHALL 通过模块 Hook 获取内容源状态，并展示接口返回的来源、许可说明、最近成功时间、有效期、数据量、同步状态和失败分类。

#### Scenario: 内容源为空或查询失败

- **WHEN** 接口返回空数组或错误
- **THEN** 页面分别展示空状态或可重试错误，不展示模拟数据

### Requirement: 手动同步可恢复且不重复提交

页面 SHALL 为一次同步生成并复用 `clientRequestId`。POST 超时或断网后 SHALL 进入结果未知，只调用按请求标识查询接口。

#### Scenario: 同步响应丢失

- **WHEN** POST 超时或断网
- **THEN** 页面禁用再次同步并允许按原 `clientRequestId` 查询结果
