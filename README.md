# CineWise

CineWise 采用前后端共用的单仓库结构。`backend/` 是基于 Java 21、Spring Boot 3.5 和 MyBatis-Plus 的模块化单体后端；`frontend/` 预留给 Umi 前端工程；`openspec/`、`docs/` 和根目录部署配置由团队共享。

后端由 A 维护公共基础、票务交易、数据库迁移顺序和部署，B/C/D 在各自领域包内实现业务；前端骨架及共享前端能力由 C 负责建设。

## 仓库结构

```text
CineWise/
├── backend/       Spring Boot 后端
├── frontend/      Umi 前端（由前端负责人初始化）
├── docs/          团队共享说明
├── openspec/      需求与契约变更
├── .github/       CI 工作流
└── compose.yaml   本地联调编排
```

## 快速开始

1. 复制 `.env.example` 为 `.env`，按本地环境填写密码和密钥；`.env` 不得提交。
2. 启动 MySQL 与 Redis：`docker compose up -d mysql redis`。
3. Windows 执行 `backend\mvnw.cmd -f backend\pom.xml spring-boot:run`，macOS/Linux 执行 `bash ./backend/mvnw -f backend/pom.xml spring-boot:run`。
4. dev 环境访问 `http://localhost:8080/actuator/health` 和 `http://localhost:8080/swagger-ui.html`。

本机未安装 Docker 时，可以先进入 `backend/` 后执行 `mvnw.cmd verify`（Windows）或 `bash ./mvnw verify`（macOS/Linux），完成编译、测试和静态检查。测试环境使用 H2 的 MySQL 兼容模式，不替代后续 MySQL Testcontainers 与并发验收。

## 模块与负责人

| 模块 | 负责人 | 主要范围 |
| --- | --- | --- |
| `common`、`ticketing`、`order`、`admin`、`job` | A | 公共基础、排期座位、订单支付退票、管理订单、调度与部署 |
| `agent` | B | Agent 规划、执行、确认、工具编排与 SSE |
| `auth` | C | Spring Security、JWT Cookie、认证上下文、验证码和公共邮件端口 |
| `profile`、`content`、`recommendation`、`travel` | D | 画像、内容数据、推荐和出行提醒 |

各业务模块统一按 `api/application/domain/infrastructure` 分层。跨模块只能依赖公开的 Application Service、DTO、领域事件或类型化工具，禁止访问其他模块的 Entity、Mapper、Repository，也禁止通过 HTTP 调用本应用 Controller。

## 开发约定

- 公共契约优先在 `openspec/changes/` 更新，再修改实现、OpenAPI、夹具和测试。
- 新增 Flyway 脚本前由 A 分配版本号；已合并的迁移不得修改。
- REST 业务 ID 使用十进制字符串，金额使用两位小数字符串，时间使用 ISO 8601。
- 写操作不进行网络层自动重试；幂等与恢复必须由业务用例明确实现。
- 提交前进入 `backend/`，执行 `mvnw.cmd verify`（Windows）或 `bash ./mvnw verify`（macOS/Linux）。

详细边界见 [后端骨架说明](docs/backend-skeleton.md) 和 [后端团队编码规范](docs/backend-coding-standards.md)。
