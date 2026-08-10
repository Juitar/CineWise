# frontend-distance-upload-log-privacy Specification

## Purpose
TBD - created by archiving change frontend-distance-upload-log-privacy. Update Purpose after archive.
## Requirements
### Requirement: 前端容器不得记录完整距离上下文 ID

系统 SHALL 对 `/api/v1/recommendation/distance-contexts/{distanceContextId}/location` 的 access log 使用固定脱敏路径，MUST NOT 记录完整 `distanceContextId`。

#### Scenario: 上传 URL 包含 canary UUID

- **WHEN** 客户端请求 `/api/v1/recommendation/distance-contexts/11111111-2222-4333-8444-555555555555/location`
- **THEN** 前端 Nginx access log 记录 `/api/v1/recommendation/distance-contexts/[redacted]/location`
- **AND** 日志中不存在该 canary UUID

### Requirement: 距离上传查询参数不得进入日志

系统 SHALL 在距离上传路径命中时忽略原始查询参数，MUST NOT 通过 `$request`、`$request_uri` 或 `$args` 把完整上传 URL 写入 access log。

#### Scenario: 上传 URL 意外带查询参数

- **WHEN** 距离上传请求带有 `?debug=privacy-canary`
- **THEN** access log 中不存在 `privacy-canary`
- **AND** 代理仍按原始请求处理，不改变接口响应

### Requirement: 其他请求必须保留访问日志

系统 SHALL 继续记录其他静态资源和 API 请求的正常 URI，不能为避免单个隐私字段而关闭整个前端容器 access log。

#### Scenario: 请求普通 API

- **WHEN** 客户端请求不属于距离上传路径的 API
- **THEN** access log 继续记录该请求 URI

### Requirement: 外部代理必须独立通过隐私验收

系统 SHALL 在 CDN、负载均衡、Ingress、宿主机反向代理或平台访问日志存在时，对每一层验证完整上传 URL不会被记录。仓库内没有对应配置时，MUST 明确标记为外部未验证，不能用前端 Nginx 结果替代。

#### Scenario: 部署环境存在外部网关

- **WHEN** canary 上传请求经过前端容器外的一个或多个代理层
- **THEN** 每层访问日志搜索 canary UUID均无结果
- **AND** 任一层出现 UUID 都阻止隐私验收通过

