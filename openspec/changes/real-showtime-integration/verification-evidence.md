# 验证补充记录

日期：2026-08-07

- `ExternalShowtimeSnapshotMySqlIntegrationTest` 在受控 MySQL 测试库通过，覆盖同一日期/影院集合的快照幂等更新、读取和清理。
- 该 MySQL 测试只在 `CINEWISE_SHOWTIME_MYSQL_IT=true` 时执行，不访问 NetStart，不写入 A 的票务表。
- 排期定向测试共 14 个，全部通过。
- `openspec validate real-showtime-integration --strict` 和 `git diff --check` 通过。
- `mvnw -DskipTests checkstyle:check` 仍被 dev 基线既有的 `ContentSyncService.java:219` 长行阻断；排期新增源文件没有 Checkstyle 长行错误。
- 全量 `mvnw verify` 的既有失败来自 dev 基线 Agent 测试和上述旧 Checkstyle 问题，未发现排期定向测试失败。
- 按 D 技能的统计口径，新增排期源文件的中文注释比例均达到 30% 以上；已重新统计并完成收尾。
- 2026-08-07 在独立的 `origin/dev` 工作树运行 `MinimalReadOnlyAgentServiceTest,RankMoviePlanExecutionAdapterTest`，同样得到 3 个失败：两个 `MinimalReadOnlyAgentServiceTest` 和一个 `RankMoviePlanExecutionAdapterTest`。该结果复现了 PR CI 失败，证明不是本 change 引入；本 PR 的排期定向测试保持通过。
- A 已确认：只将 `ACCEPTED` 候选导入本地交易场次；`SANDBOX_REFERENCE` 仅用于按 `durationMinutes` 和 `auditoriumText` 创建本地沙箱影厅、座位、预计结束时间和价格；本地价格由 A 配置，外部价格变化不得覆盖本地场次或订单；`QueryResult` 需要 `truncated`，候选按三元幂等键去重并限制 200 条；拒绝明细最多 200 条并通过独立 `rejectedTruncated` 标记。
