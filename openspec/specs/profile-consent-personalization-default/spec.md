# profile-consent-personalization-default Specification

## Purpose
TBD - created by archiving change profile-consent-personalization-default. Update Purpose after archive.
## Requirements
### Requirement: 两个开关说明职责清晰

个人中心 SHALL 说明第一个开关用于记录和管理画像标签，第二个开关用于控制相关功能是否使用画像偏好；文案 MUST NOT 表示两个开关自动联动。

#### Scenario: 用户查看两个开关

- **WHEN** 用户打开已同意画像数据的个人中心
- **THEN** 页面分别展示记录画像标签和使用画像偏好的职责说明

### Requirement: 撤回清理必须匹配事件版本

撤回事件处理器 SHALL 调用 C 提供的版本校验入口。C MUST 在同一事务中锁定同意记录，并且仅当状态仍为撤回且 `consentVersion`、`consentRecordVersion` 都与事件一致时执行画像清理回调。版本不一致时系统 MUST 记录该事件已处理并跳过清理。

#### Scenario: 旧撤回事件晚到

- **WHEN** 用户重新同意后，旧 outbox 撤回事件才到达
- **THEN** 事件被记录为已处理，但不关闭新偏好、不删除新标签、不清理新缓存

#### Scenario: 当前撤回版本匹配

- **WHEN** 同意记录仍为撤回状态且两个版本都匹配事件
- **THEN** 系统在同一事务中执行偏好关闭、标签删除和缓存失效

