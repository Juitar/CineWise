# 设计

## Nginx 日志边界

`frontend/nginx.conf` 作为 `/etc/nginx/conf.d/default.conf` 加载，位于 Nginx `http` 上下文内，因此可以在 `server` 外使用 `map`。`map` 以不含查询参数的 `$uri` 判断距离上传路径：

- 匹配上传路径时，日志目标固定为 `/api/v1/recommendation/distance-contexts/[redacted]/location`；
- 其他请求继续使用 `$request_uri`，保持现有排障信息；
- 自定义 `log_format` 重新组合 `$request_method $privacy_request_target $server_protocol`，禁止引用会包含原始 URI 的 `$request`。

在 `server` 内显式声明 `access_log`，覆盖从 Nginx `http` 上下文继承的默认 combined 日志。代理转发仍使用原请求路径，脱敏只影响 access log，不修改接口行为。

## 查询参数

受保护路径命中后不拼接 `$args` 或 `$request_uri`，即使调用方意外附带查询参数，也不会进入前端容器日志。其他 API 仍保持 `$request_uri` 记录方式。

## 验证

仓库脚本读取 `frontend/nginx.conf`，检查：

- 存在上传路径 `map` 和 `[redacted]` 固定目标；
- 自定义日志格式不使用 `$request`；
- `server` 使用自定义日志格式；
- 受保护目标不包含 `$args` 或 `$request_uri`。

实际验证使用 Nginx 容器发送含 canary UUID 和查询参数的请求，再搜索容器 access log。上游返回 2xx、4xx 或 5xx 都不影响日志脱敏断言。

## 外部网关

仓库 Compose 只发布前端和后端本机端口，没有 CDN、Ingress、负载均衡或宿主机反向代理日志配置。因此 C 只能提供仓库内 Nginx 证据。部署负责人必须在每层外部访问日志中搜索相同 canary UUID；任一层命中都视为隐私验收失败。
