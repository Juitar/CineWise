# 画像数据保存同意

## ADDED Requirements

### Requirement: 提供类型化同意快照

系统 MUST 通过 `ProfileDataConsentQuery` 向 D 返回当前同意状态和版本，不得用登录、注册隐私政策同意或个性化开关代替画像数据保存同意。

#### Scenario: 没有同意记录

- **WHEN** D 查询没有独立同意记录的用户
- **THEN** 返回 `granted=false`、`consentVersion=0`、`version=0`、两个时间为空

#### Scenario: 已撤回

- **WHEN** D 查询已撤回的用户
- **THEN** 返回 `granted=false`，保留最近同意版本并返回撤回时间

### Requirement: 原子撤回并可靠投递

系统 MUST 在同一 MySQL 事务中完成同意记录 CAS 撤回和唯一 outbox 写入，并在提交后投递带完整版本字段的撤回事件。

#### Scenario: outbox 写入失败

- **WHEN** 同意记录更新后 outbox 插入失败
- **THEN** 同意记录更新回滚且 D 不收到事件

#### Scenario: 重复撤回

- **WHEN** 用户重复撤回已经撤回的同意
- **THEN** 系统不创建第二个 eventId 或 outbox 记录

### Requirement: 自动重试与人工恢复

系统 MUST 按 1、5、15、60、360 分钟和后续每 6 小时重试原事件，最多自动失败十次；人工恢复 MUST 复用原 eventId。

#### Scenario: 自动重试耗尽

- **WHEN** 原事件自动投递连续失败十次
- **THEN** outbox 进入 `EXHAUSTED` 且停止自动扫描

#### Scenario: 人工恢复失败

- **WHEN** 管理员重投原 `EXHAUSTED` 事件仍失败
- **THEN** 原 outbox 保持 `EXHAUSTED`，eventId 和 retryCount 不变

### Requirement: 个人中心处理未同意结果

系统 MUST 在画像 REST 返回 `202004` 时清空页面画像数据并提示“未开启画像数据使用”，不得把它当网络错误重试或查询同意状态 REST。

#### Scenario: 撤回后页面仍有旧数据

- **WHEN** 页面已有标签或开关数据，下一次画像请求返回 `202004`
- **THEN** 页面立即停止显示旧标签和开关结果
