# 后端骨架说明

## 1. 骨架范围

本骨架是四位成员共同开发的唯一 Spring Boot 应用。它冻结公共技术基线、模块落位、依赖方向、环境配置、数据库迁移入口、质量门禁和部署入口，不提前实现其他负责人的领域业务。

```text
com.miaoyu.ticket
├── common                 A：统一响应、错误、trace、配置、调度、持久层插件
├── auth                   C：认证、JWT Cookie、SecurityContext、邮件公共端口
├── profile                D：画像与行为摘要
├── content                D：影片影院数据与内容摘要端口
├── ticketing              A：影厅、场次、座位与锁座
├── order                  A：订单、Mock 支付、电子票与退票
├── agent                  B：计划、执行、确认、SSE 与轨迹
├── agent.tool.ticketing   A/B：类型化票务工具适配
├── recommendation         D：过滤、评分与解释
├── travel                 D：出行任务、提醒、路线与餐饮
├── admin                  A/C/D：管理查询、配置和审计
└── job                    A 维护调度基础，各领域提供可重复执行 Job
```

每个领域模块采用 `api/application/domain/infrastructure`。Controller、SSE 适配器、Agent Tool Adapter、事件监听器与 Job 只能调用 Application Service；事务在 Application Service 内定义；Domain 不依赖 Spring、Servlet 或 MyBatis；Infrastructure 实现 Repository、Mapper、缓存和外部 Provider。

## 2. 已冻结公共契约

- REST 统一使用 `Result<T>` 与 `PageResult<T>`；成功 `code=0`，失败为六位数值 `ABBCCC`。
- `X-Trace-Id` 在请求、响应、日志、工具和任务中贯通；不合法的调用方 traceId 会被替换。
- 业务 ID 内部使用 `Long` 雪花 ID，对外 DTO 必须声明为 `String`；禁止全局把所有 Long 序列化为字符串。
- 金额内部使用 `BigDecimal`，对外由 DTO/Assembler 输出两位小数字符串。
- 业务时间统一通过注入的 `Clock` 获取，时区为 `Asia/Shanghai`；禁止散落调用 `now()`。
- C 实现 `CurrentUserAccessor` 并从 Spring Security 上下文返回身份；其他模块不得解析 JWT 或信任请求体 `userId`。
- 认证完成前 `SecuritySkeletonConfiguration` 仅开放健康检查和 dev/demo OpenAPI，其余请求默认拒绝。
- 调度使用 4 线程 `ThreadPoolTaskScheduler`；进程锁不是幂等依据，Job 必须使用数据库条件更新、版本 CAS 或唯一约束。
- 非核心异步任务使用有界 `applicationTaskExecutor`；队列满时明确拒绝，不允许静默丢弃或无界扩张。
- 外部 Provider 复用 `externalRestClient` 的连接/读取超时基线；业务写操作不得在客户端层自动重试。
- 云端 MinIO 仅用于海报和演示附件，不是应用启动或交易主链路依赖。

## 3. 数据库迁移协作

Flyway 脚本统一放在 `backend/src/main/resources/db/migration`。对应feature OpenSpec建立后，A可以生成用于代码审查的标准格式迁移草案；相关Owner确认且A明确授权前，草案不得在任何数据库执行。没有feature OpenSpec或仍存在字段冲突的表不得提前生成迁移。

运行环境中的`FLYWAY_ENABLED`默认且当前必须为`false`。只有确认门完成、正式迁移通过空MySQL评审后，才可在受控迁移步骤中临时设为`true`；不得在连接云数据库时依靠应用误启动隐式建表。

新增脚本流程：

1. 领域Owner先在对应详设和OpenSpec change确认表、索引、生命周期、兼容和回滚方案。
2. A核对全局雪花ID、`DATETIME(3)`、命名和跨模块逻辑关联规则。
3. 向A申请下一个迁移版本，禁止多人自行占号；迁移草案必须在文件头标明未执行状态。
4. 相关Owner确认后由A授权进入空MySQL验证；已经共享或执行的脚本不得改名或改内容。
5. 先在空MySQL集成环境验证，再运行重复初始化和旧应用兼容检查。

跨模块使用逻辑关联，不建立物理外键，也不得通过 Mapper 修改其他模块表。

## 4. 分支与 OpenSpec 建议

公共骨架对应 `foundation-contracts-and-auth-shell`。后续按总体系分冻结的 change 推进：

1. Wave 0：`foundation-contracts-and-auth-shell`
2. Wave 1：`content-and-show-selection-flow`、`agent-runtime-and-workspace` 的只读基础
3. Wave 2：`ticketing-transaction-flow`、`travel-reminder-experience`
4. Wave 3：`admin-observability-and-release`

公共 API、事件、工具、权限、状态或数据迁移变化必须先更新对应 change 的 `proposal/spec/design/tasks`，随后同步 OpenAPI/Schema、消费者夹具和测试。

## 5. 验证命令

```powershell
Set-Location backend
.\mvnw.cmd verify # macOS/Linux 使用 cd backend && bash ./mvnw verify
Set-Location ..
docker compose config
docker compose up -d --build --wait
```

`verify` 包含编译、单元/上下文测试、ArchUnit、Checkstyle、SpotBugs 和 JaCoCo 报告。本机 H2 测试只用于快速反馈；订单并发、MySQL 条件更新、Flyway 兼容、Redis 降级和容器冒烟必须在后续集成测试中使用真实组件。

H2 `test` profile 的 Flyway 基线固定为 V009。V010 起允许使用 H2 不支持的 MySQL 8.4 DDL，但必须由 `Backend MySQL Integration` 在一次性空库中以 `latest` 执行首次 migrate、无 pending/失败历史检查和重复 migrate；画像及后续依赖 V010+ 表结构的 Repository/事务测试也必须进入 MySQL 集成集合。新增后续迁移时不得修改已执行 SQL，也不得逐版本复制 H2 专用迁移。

## 6. 固定演示种子

固定种子与 Flyway 结构迁移分离，默认不执行。受控初始化时临时设置：

```powershell
$env:SEED_ENABLED = "true"
$env:SEED_FIXED_VALUE = "20260802"
Set-Location backend
.\mvnw.cmd spring-boot:run
```

初始化完成后停止应用并清除当前终端中的`SEED_ENABLED`。本地 Compose 也会显式传递同名变量，但默认值仍为`false`。

当前种子确保10部Mock影片、4家Mock影院、每家2个影厅、相对运行日期0至6天的早中晚场次和80座完整座位图。重复执行只补缺失业务对象；不会更新已有场次，也不会重置`LOCKED`、`SOLD`或其他非`AVAILABLE`座位。账号、订单、支付、电子票、天气和Agent失败场景由对应模块另行初始化，不属于本种子范围。
