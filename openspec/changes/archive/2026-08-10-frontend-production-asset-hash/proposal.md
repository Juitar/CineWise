## Why

当前生产入口固定为 `/umi.js` 且文件名不随内容变化。连续部署后，浏览器可能继续使用上一版入口，再请求新镜像中已经不存在的异步 JS/CSS，最终出现白屏；强制刷新恢复与该问题一致。

## What Changes

- 开启 Umi 生产资源内容哈希，让入口、异步 JS 和 CSS 文件名随内容变化。
- 增加生产构建产物检查，校验入口 JS 带内容哈希，且 `index.html` 引用的本地 JS/CSS 全部存在。
- 增加生产预览浏览器检查，验证首页和一个懒加载页面均能打开，且页面没有资源加载失败。
- 将生产产物检查接入 `pnpm check`，将生产预览检查接入前端 CI。

## Capabilities

### New Capabilities

- `frontend-production-assets`: 前端生产资源的版本命名、产物完整性和生产页面加载检查。

### Modified Capabilities

无。

## Impact

- C：修改 `frontend/.umirc.ts`、前端构建脚本和生产浏览器测试。
- A：合并后审查 Docker/Nginx 对 `index.html` 和带哈希静态资源的缓存规则，并在演示服务器验证发布结果。
- 后端 API、DTO、权限、数据库和业务页面行为均不变化。
