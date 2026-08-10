# 设计

页面只组合 `modules/admin-content` Hook。模块通过 `apiRequest` 调用三个既有接口。POST 超时或断网时保留同一 `clientRequestId` 于 sessionStorage，只允许 GET 查询原任务，不自动重发。
