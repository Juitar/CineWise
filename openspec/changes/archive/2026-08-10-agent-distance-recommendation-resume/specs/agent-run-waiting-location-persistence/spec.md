## ADDED Requirements

### Requirement: 等待定位必须是可恢复的非终态

系统 SHALL 将距离推荐等待表示为 `agent_run.status=WAITING_LOCATION`。转换时 MUST 保留 `agent_session.active_run_id`，保持 `finished_at IS NULL`，并且只以 `id + version + expectedStatus` 的 CAS 更新。进入状态的 `agent_run.update_time` 是唯一等待起点；等待期间任何读取、SSE 或上下文创建均不得刷新它。

#### Scenario: 正常进入等待
- **WHEN** 同一运行已保存可执行的 rankMoviePlan 且用户选择距离推荐
- **THEN** B 以预期 RUNNING 状态的 CAS 转换为 WAITING_LOCATION
- **AND** active_run_id 仍指向该 run，且不保存距离上下文或位置资料

#### Scenario: CAS 冲突不覆盖新状态
- **WHEN** 取消、完成、恢复或另一请求已改变同一 run
- **THEN** 等待转换或恢复的 CAS 不得覆盖现有状态
- **AND** 系统重新查询实际状态而不创建新 run 或重放工具

### Requirement: 五分钟等待超时必须使用数据库时间恢复普通推荐

系统 SHALL 使用数据库 `CURRENT_TIMESTAMP(3)` 与 `agent_run.update_time` 判断等待满五分钟。超时恢复 MUST 要求 `status=WAITING_LOCATION`、原 version 和未晚于五分钟截止的 update_time；成功后进入 RUNNING 并继续同一 `runId + planVersion` 的普通推荐。现有 RUNNING 陈旧恢复器不得处理 WAITING_LOCATION；等待恢复器不得终止未超时的合法等待。

#### Scenario: 等待超时恢复
- **WHEN** 数据库时间已超过进入 WAITING_LOCATION 的 update_time 五分钟
- **THEN** B 以 WAITING_LOCATION CAS 恢复 RUNNING 并继续普通推荐
- **AND** 等待时间不会永久占用 active_run_id

#### Scenario: 旧计划不能恢复失效等待
- **WHEN** 陈旧事件或其他 planVersion 尝试恢复已取消、已完成或已恢复的等待 run
- **THEN** CAS 不命中且系统不执行旧 rankMoviePlan

### Requirement: V018 草案必须兼容既有 Agent 运行

系统 SHALL 依赖 A 已发布的前向 Flyway V018 替换 `chk_agent_run_status` 和 `chk_agent_run_completion`，不修改 V008。状态 CHECK MUST 接受 RUNNING、WAITING_LOCATION、COMPLETED、FAILED、CANCELLED；完成时间 CHECK MUST 要求 RUNNING 和 WAITING_LOCATION 的 finished_at 为 NULL，三个终态的 finished_at 非 NULL。迁移 MUST 不要求历史数据回填，且 V018 已冻结不得修改。

#### Scenario: 旧状态保持合法
- **WHEN** MySQL 8.4 对已有 RUNNING、COMPLETED、FAILED 或 CANCELLED 行应用 V018
- **THEN** 旧状态仍通过 CHECK 且不需要修改历史数据
- **AND** B 不修改已由 A 验证并发布的 V018
