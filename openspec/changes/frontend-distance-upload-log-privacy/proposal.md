# 距离上传访问日志脱敏

## 背景与目标

距离上传接口把一次性 `distanceContextId` 放在 URL 路径中。前端容器当前继承 Nginx 默认 access log，默认 `$request` 会记录完整路径和查询参数，阻塞 PR #157 的隐私验收。本变更由 C 让前端容器日志只记录固定脱敏路径，并提供外部网关复验方法。

## 范围

- 为 `POST /api/v1/recommendation/distance-contexts/{distanceContextId}/location` 配置固定脱敏的 Nginx 日志目标。
- 距离上传日志不得包含完整 `distanceContextId` 或该请求的查询参数，其他请求继续保留现有访问日志。
- 增加可重复执行的静态配置验证和真实 Nginx 日志验证说明。
- 核对仓库内 Compose、部署工作流是否还包含另一层外部网关配置。

## 非范围

- 不修改 D 的坐标上传接口、B 的 PR #157、数据库、SSE 或 Agent 业务代码。
- 不记录、检查或保存坐标、地址和请求体。
- 不宣称仓库外的平台网关已经通过；平台日志需要基础设施负责人用同一 canary 验证。

## Owner 与影响

C 负责 `frontend/nginx.conf`、仓库内验证脚本和证据。部署/基础设施负责人负责仓库外 CDN、负载均衡、Ingress 或宿主机反向代理日志验证。D 提供接口路径和验收场景，B 不接触上传内容。

## 验收结果

- 用带 canary UUID 的上传 URL 请求前端 Nginx 后，access log 只出现 `/api/v1/recommendation/distance-contexts/[redacted]/location`。
- access log 中找不到 canary UUID，也找不到该请求的查询参数。
- Nginx 配置检查和隐私验证脚本通过；仓库外网关在正式隐私验收前提供同样的无命中证据。
