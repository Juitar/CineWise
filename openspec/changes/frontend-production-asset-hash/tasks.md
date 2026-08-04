## 1. 规则与配置

- [x] 1.1 C 确认问题只涉及前端生产资源命名和构建检查，不修改业务接口、Docker/Nginx 配置或数据库。（验证：proposal 与 design 范围核对）
- [x] 1.2 C 在 Umi 配置中开启生产资源内容哈希。（验证：`dist/index.html` 不再引用固定 `/umi.js`）

## 2. 自动检查

- [x] 2.1 C 新增生产产物检查，断言入口 JS 带内容哈希，且 HTML 引用的本地 JS/CSS 均存在。（验证：`pnpm check`）
- [x] 2.2 C 新增生产预览浏览器测试，覆盖首页和懒加载 `/movies` 页面及资源加载失败。（验证：`pnpm e2e:production`）
- [x] 2.3 C 将生产预览测试接入前端 CI，同时保留开发服务器 E2E。（验证：工作流与脚本核对）

## 3. 交付验证

- [x] 3.1 C 执行 `pnpm check`、`pnpm e2e:production`、OpenSpec 严格校验和 Git 差异检查。（验证：记录实际命令结果）
- [ ] 3.2 A 在 Docker/Nginx 演示环境复核 `index.html` 缓存、带哈希资源缓存、连续发布和普通刷新结果。（验证：服务器响应头、构建文件和浏览器冒烟记录）
