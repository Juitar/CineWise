# 任务

## 1. 前置核验与接口确认

- [x] 1.1 D 用脱敏测试请求取得 NetStart 排期 Provider 的样例，记录请求参数、城市/日期范围、分页、稳定外部影片/影院/场次 ID、时区、价格字段含义、刷新频率、失败表现和来源标识；不得保存 Key、Cookie 或完整原始响应。验证：2026-08-07 已记录于 `provider-evidence.md`；确认 `/cinema/shows`、`cinemaId`、`movies[].id`、`movies[].dur`、`seqNo`、`showDate`、`tm`、`th` 和 `vipPrice`，未发现余座、座位或交易字段。
- [x] 1.2 D 根据 1.1 更新本 change，明确未来窗口、快照 `expiresAt`、查询上限和缓存是否启用；未获得证据的 Provider 行为保持未定，不进入代码。验证：当天至未来 7 天、100 家影院上限、`expiresAt=min(startTime,dataAt+10分钟)` 和只读未过期快照已写入设计与核验记录；`openspec validate real-showtime-integration --strict` 通过。
- [x] 1.3 D 与 A 确认公开 `ExternalShowtimeSnapshot` DTO/Port 的包名、方法签名、逐项隔离结果和 A 的导入返回语义。验证：A 已确认公开入口、导入条件、时间类型、价格规则和不访问对方持久化层。

## 2. D 实现排期候选 Provider 和快照

- [x] 2.1 D 实现 NetStart 排期 Raw Client 和 Provider Adapter，复用限流、超时和一次短重试策略；429、非法响应和身份错误不重试。验证：`NetStartShowtimeProviderTest` 覆盖 `code=0` 成功、字段提取、429 不重试和缺 `seqNo` 隔离；`NetStartRequestLimiter` 已使影片、影院和排期共享本进程 10 req/min 预算。连接超时、5xx 与不可重试 4xx 的 Mock HTTP 回归留在 2.5 一并补齐。
- [x] 2.2 D 实现外部影片/影院/场次 ID、时间和参考标价标准化；隔离缺 ID、无效时间、字段不合格和身份解析失败的候选。验证：`ExternalShowtimeQueryServiceTest` 覆盖未映射影片隔离、`100001` 日期边界、参考标价和 Asia/Shanghai 时间；既有 `ContentIdentityResolutionServiceTest` 覆盖 `303005/303006/303007`，实现不按名称猜测映射。
- [x] 2.3 D 实现自己的排期快照存储、读取和过期处理；Provider 调用在事务外，失败不清除最近成功快照。验证：`ExternalShowtimeSnapshotMySqlIntegrationTest` 已在受控 MySQL 测试库通过，覆盖成功写入、同一日期/影院集合幂等更新、读取和清理后无残留。
- [x] 2.4 D 实现公开 Application DTO/Port，仅返回已映射候选及来源、时间、时效、`durationMinutes`、`auditoriumText` 和降级字段。验证：A 已确认 Port/DTO 字段和导入边界；`snapshots` 可含 `ACCEPTED` 或 `SANDBOX_REFERENCE`，后者只供本地沙箱参考；`rejectedSnapshots` 最多返回 200 条并通过独立 `rejectedTruncated` 标记截断。
- [x] 2.5 D 实现受控刷新和降级：未过期快照可读，Provider 故障有快照时标记 `SNAPSHOT`，无快照时返回 `303004`。验证：`NetStartShowtimeProviderTest` 覆盖 Provider 关闭、429、5xx；`ExternalShowtimeQueryServiceTest` 覆盖网络/超时、无快照、未过期快照和过期快照不回退。

## 3. A 导入和联合验证

- [ ] 3.1 A 在自己的 Application Service 消费 D 的 Port，校验候选并导入本地沙箱场次、影厅、座位和本地价格。验证：D 不写 A 表，A 不把外部余座或标价直接当作交易事实。
- [ ] 3.2 A 保持 `SaleableShowBatchQueryService` 的本地可售规则和 `306003` 语义。验证：导入后的本地候选必须通过 ON_SALE、未开场和余座大于零过滤。
- [ ] 3.3 D、A 在授权的隔离 MySQL/Redis 环境完成受控验证，记录运行 ID、迁移版本、清理范围、快照/导入/查询结果和无残留检查。验证：不使用共享生产数据，不泄漏 Provider 密钥或原始响应。

## 4. 收尾

- [ ] 4.1 D 运行排期 Provider、身份映射、快照和降级的定向测试，以及 `backend/mvnw.cmd verify`。验证：记录通过项和未执行的真实 Provider 验证原因。
- [ ] 4.2 D 运行 `openspec validate real-showtime-integration --strict`、`git diff --check`，检查新增 D 源文件中文注释比例。验证：无格式错误；未完成的 A/C 联调保持未勾选。
