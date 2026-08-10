# 任务

- [x] C：确认 D 上传路径和当前默认 Nginx 日志风险；验证：默认 `$request` 会包含完整 `distanceContextId`。
- [ ] C：配置前端 Nginx 的距离上传日志脱敏，同时保留其他请求日志；静态配置验证已通过，本机 Docker 无法启动，`nginx -t` 和真实容器日志验证等待 CI。
- [x] C：增加配置回归脚本和 canary 日志验证；验证：本地生成的日志样例中 UUID 和查询参数均无命中，真实容器 canary 已加入 `e2e:production-nginx`。
- [x] C：核对 Compose、部署工作流和仓库内代理层；验证：仓库没有外部 CDN、Ingress、负载均衡或宿主机代理配置，已列出需部署负责人检查的日志层。
- [x] C：运行前端检查、OpenSpec 严格校验、`git diff --check` 并记录结果。
