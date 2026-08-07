# Tasks: frontend-admin-agent-runs-integration

## 1. 契约和规划

- [x] 1.1 Owner：C；核对最新 `dev`、PR #170 Controller、DTO、权限测试和两个固定夹具；验证：在 proposal/design 记录准确提交和差异。
- [x] 1.2 Owner：C；完成 proposal、spec、design、tasks；验证：`openspec validate frontend-admin-agent-runs-integration --strict` 通过。

## 2. DTO、API 和消费者测试

- [x] 2.1 Owner：C；修正列表、详情和节点类型及运行时映射，补齐 `sessionId` 和所有可空字段；验证：mapper 与 contract 测试通过。
- [x] 2.2 Owner：C；支持五个实际运行状态、未知状态和安全中文占位；验证：query、mapper 和页面测试通过。
- [x] 2.3 Owner：C；清理空筛选、保留 ISO 8601 和字符串 runId 编码；验证：`api.test.ts` 覆盖六个参数和空值。
- [x] 2.4 Owner：C；增加两个 PR #170 固定夹具的消费者测试；验证：列表和详情夹具均通过白名单映射且不含敏感字段。

## 3. 页面状态和恢复

- [x] 3.1 Owner：C；复用公共页面状态组件处理首次加载、空列表、筛选空态、刷新失败、401、403、详情 404 和 5xx/traceId；验证：页面测试通过。
- [x] 3.2 Owner：C；保证详情关闭、切换、刷新和重复请求不显示上一条数据；验证：Hook 和页面竞态测试通过。
- [x] 3.3 Owner：C；完成翻页、刷新和窄屏布局；验证：页面交互测试、类型检查和生产构建通过。
- [x] 3.4 Owner：C；检查页面和日志只显示脱敏白名单字段；验证：敏感字段注入测试和变更扫描通过。

## 4. 自动验证

- [x] 4.1 Owner：C；运行 `admin-agent` 模块和轨迹页面定向测试；验证：6 个文件、36 个用例通过。
- [x] 4.2 Owner：C；运行 `pnpm check`；验证：106 个测试文件、590 个用例通过，格式、Lint、类型、生产构建和现有隐私检查通过。
- [x] 4.3 Owner：C；运行 OpenSpec 严格校验、`git diff --check` 和 Git 状态核对；验证：无无关文件和后端业务改动。

## 5. PR #170 合并后的真实验收

临时 PR worktree 已在 `8e72199c` 完成 868 个后端测试（0 失败、0 错误、62 个按环境跳过），并完成 Jar、Checkstyle、SpotBugs（0 问题）和 JaCoCo；该结果只用于合并前准备，不代替以下合并后真实 HTTP 和部署验收。

- [x] 5.1 Owner：B；将 PR #170 合入可部署 `dev` 并提供可运行环境；验证：`dev@3ba1d084` 合并 head `8e72199c496d364ec2c078eeb8c2242762cbbaad`。
- [x] 5.2 Owner：C/B；运行 PR #170 后端 `backend\\mvnw.cmd verify`；验证：wrapper 因本机临时目录脚本问题不可用，改用同一 Maven 3.9.11、Java 21.0.11 在英文临时目录执行完整 `mvn verify`，223 份报告、868 个测试通过，0 失败、0 错误、62 个按环境跳过，Jar、Checkstyle、SpotBugs（0 问题）和 JaCoCo 均通过。
- [x] 5.3 Owner：C/B；真实 HTTP 验证 ADMIN 列表、详情和不存在 runId 404；验证：独立 H2 临时服务中 ADMIN 列表 200（2 条）、详情 200、未知 runId 404，字段与页面一致。
- [x] 5.4 Owner：C/B；真实 HTTP 验证 USER 403、匿名认证规则且响应无轨迹数据；验证：独立 H2 临时服务中 USER 403、匿名 401，响应均不含轨迹数据。
- [ ] 5.5 Owner：C/B；验证真实 `WAITING_LOCATION`、页面安全展示和部署环境；验证：不显示原始工具参数、模型内部内容或敏感数据。
