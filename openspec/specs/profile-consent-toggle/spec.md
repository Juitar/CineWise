# profile-consent-toggle Specification

## Purpose
TBD - created by archiving change profile-consent-toggle. Update Purpose after archive.
## Requirements
### Requirement: 展示独立的画像数据使用开关

系统 MUST 在个人中心展示独立于个性化设置的画像数据使用开关，且不得把注册隐私政策同意或个性化开关当作该状态。

#### Scenario: 用户未同意画像数据使用

- **WHEN** 画像 REST 返回 `202004`
- **THEN** 页面显示未选中的“使用用户画像”开关和“未开启画像数据使用”，且不显示个性化开关或旧标签

#### Scenario: 用户已同意画像数据使用

- **WHEN** 画像 REST 成功返回标签和偏好
- **THEN** 页面显示已选中的“使用用户画像”开关，并展示个性化开关和当前标签

### Requirement: 用户可以开启和撤回画像数据使用

系统 MUST 只通过 C 已提供的同意写接口开启或撤回画像数据使用，并在成功后更新页面状态。

#### Scenario: 开启成功

- **WHEN** 未同意用户打开“使用用户画像”开关且同意写入成功
- **THEN** 页面重新读取现有画像 REST，并按最新标签和个性化结果展示

#### Scenario: 关闭成功

- **WHEN** 已同意用户关闭“使用用户画像”开关且撤回成功
- **THEN** 页面立即清空标签和个性化结果，显示“未开启画像数据使用”

### Requirement: 写操作不得自动重复

系统 MUST 在画像同意写操作结果未知或冲突时只读取现有画像 REST 判断最新状态，不得自动重发同意或撤回请求，也不得新增同意状态查询 REST。

#### Scenario: 撤回响应超时

- **WHEN** 撤回请求超时且结果未知
- **THEN** 页面只读取一次现有画像 REST，并按成功或 `202004` 恢复开关状态

#### Scenario: 无法确认最新状态

- **WHEN** 写操作失败且现有画像 REST 也无法读取
- **THEN** 页面显示状态暂时无法确认并禁用画像数据使用开关

### Requirement: 防止两个画像开关并发提交

系统 MUST 在画像数据使用或个性化设置任一请求提交期间禁用两个开关。

#### Scenario: 画像同意正在提交

- **WHEN** 用户开启画像数据使用的请求尚未完成
- **THEN** 页面不能再次提交画像同意，也不能同时修改个性化设置

