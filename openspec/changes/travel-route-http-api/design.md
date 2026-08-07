# 设计

Controller 只做 HTTP DTO 校验和 `BasicRouteService` 调用，不经过 `PlanBasicRouteTool`；后者仍为 B 的 Agent 工具。请求坐标先由 `BrowserUserLocationAdapter` 校验原始范围并规范到 6 位，随后组装 `BasicRouteCommand`。

`BasicRouteService` 负责顺序校验：当前用户任务归属、影院坐标、位置共享确认、位置粒度、出行方式和 Provider。坐标只存在于当前方法调用与 Provider 请求中，不写入日志、异常、数据库、缓存、快照或响应。

首版允许 `DRIVING`、`WALKING`。`TRANSIT` 和其他值不产生自动降级，按 `307001` 返回。前端定位失败时不调用本接口；后端仍须拒绝非法坐标。

测试覆盖成功、任务不存在或无权、未确认共享、非法坐标、影院无坐标、非法方式和 Provider 失败。Controller/OpenAPI 测试确认接口不出现 `originValue`、`cinemaArea`、地址、影院坐标或路线折线。
