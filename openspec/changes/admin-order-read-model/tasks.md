# Tasks: admin-order-read-model

## 1. 契约确认

- [x] 1.1 A冻结管理订单只读范围、筛选、聚合、脱敏、错误和非目标；验证：OpenSpec严格校验通过。
- [x] 1.2 C确认`/api/v1/admin/**`由唯一安全链限制为ADMIN，A和C分别经`CurrentUserAccessor`复核ADMIN；验证：A不新增安全链或解析JWT。
- [x] 1.3 C确认`UserAdminQueryPort`、1至19位数字ID、2至100位邮箱关键字、100人上限、`201010/400`、`301002/503`和批量脱敏语义；验证：A不访问`sys_user`或C持久化包。

## 2. A后端实现

- [x] 2.1 实现管理订单查询条件、ADMIN应用层复核和只读视图；验证：非法条件与USER角色测试通过。
- [x] 2.2 实现A自有交易表的管理只读Repository及稳定分页；验证：组合筛选、空分页和固定排序测试通过。
- [x] 2.3 实现订单、座位、支付、电子票、退款和场次引用的批量聚合；验证：列表按关联类型批量读取，详情关联状态正确。
- [ ] 2.4 C实现合入后接入`UserAdminQueryPort`，实现`userKeyword`和`emailMasked`；验证：未知用户空分页、历史用户缺失保留订单、过宽条件400、摘要不可用503。
- [x] 2.5 实现两个REST Controller、DTO和OpenAPI契约；验证：字符串ID、两位小数金额、ISO时间、稳定错误码和Mock夹具正确。

## 3. 测试与交付

- [x] 3.1 覆盖ADMIN成功、USER 403、非法筛选、详情404和严格只读测试。
- [x] 3.2 覆盖完整邮箱、JWT、Cookie、幂等键、二维码载荷、impactSnapshot和actionId敏感字段扫描。
- [x] 3.3 运行`mvnw.cmd verify`、`openspec validate admin-order-read-model --strict`和`git diff --check`；结果：全部通过，PR有效注释率30.93%，admin模块30.19%。
- [ ] 3.4 C正式认证合入后验证安全链ADMIN/USER/匿名HTTP语义；当前A测试不得冒充C认证验收。
- [x] 3.5 在A本地隔离MySQL库验证有界用户ID筛选、稳定分页、聚合映射和严格只读；结果：本地MySQL 8.0.40与CI MySQL 8.4.11下4个用例均通过；迁移发布仍须遵守专项迁移门禁。

## 4. 后续非本PR任务

- [x] 4.1 管理订单展示组件、集中 Mock、响应式布局与组件测试
- [x] 4.2 管理订单真实 DTO/API/Hook 和错误码联调；验证：消费后端夹具，筛选竞态、错误码、详情取消与手动重试测试通过。
- [ ] 4.3 C 安全链及 ADMIN/USER/匿名真实 HTTP 验证
