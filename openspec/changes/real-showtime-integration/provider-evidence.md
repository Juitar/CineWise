# NetStart 排期 Provider 核验记录

## 核验时间和范围

- 核验时间：2026-08-07，Asia/Shanghai。
- 来源：`https://apis.netstart.cn/maoyan/`，仅用于学习/演示环境；未使用 Key、Cookie 或任何账号信息。
- 调用：先以 `ci=70` 查询长沙影院，再调用 `GET /cinema/shows?ci=70&cinemaId={externalCinemaId}`。
- 本记录只保留字段名、类型和脱敏后的结论，不保存完整响应、影院名称、地址或精确地点文本。

## 已确认字段

| 用途 | 字段 | 结论 |
| --- | --- | --- |
| 外部影院身份 | `data.cinemaId` | 返回数值影院 ID；与本地影院通过既有 `content_identity_mapping` 映射。 |
| 外部影片身份 | `data.movies[].id` | 返回数值影片 ID；与本地影片通过既有映射关联。 |
| 外部场次身份 | `shows[].plist[].seqNo` | 返回非空序列号，作为 `externalShowId`；缺失时隔离，不拼接名称或时间。 |
| 放映日期和时间 | `shows[].showDate`、`shows[].plist[].tm` | 日期形如 `yyyy-MM-dd`，时间形如 `HH:mm`；按 Asia/Shanghai 转换。 |
| 参考标价 | `shows[].plist[].vipPrice` | 返回数字字符串；只作为 `listedPrice`，不能写为本地交易价格。 |
| 展示资料 | `lang`、`th` | 可选的语言和影厅文本；不进入 A 的本地影厅事实。 |
| 外部余座/座位 | 未发现可用字段 | 不生成、缓存或转发余座、座位图、订单或支付数据。 |

## 运行规则

- D 只查询当天至未来 7 天的业务日期；每次查询最多 100 个本地影院 ID。
- 成功快照的 `expiresAt=min(startTime, dataAt + 10 分钟)`；过期快照不能导入本地票务系统。
- 429、不可重试 4xx、字段不合格和身份解析失败不重试；连接失败或 5xx 最多短重试一次。
- Provider 故障时保留最近成功快照；只有未过期快照可作为降级结果，且 `degraded=true`、`fallbackType=SNAPSHOT`。
