## Context

Umi 当前生产入口为固定 `/umi.js`。Nginx 已对 `/index.html` 返回 `Cache-Control: no-store`，但固定入口文件名本身没有版本区分；浏览器或中间缓存继续使用旧入口时，会请求上一版异步文件，而新镜像只保留当前 `dist/`，因此可能白屏。

## Decisions

### 1. 使用 Umi 的 `hash: true`

在 `frontend/.umirc.ts` 开启框架现有内容哈希能力，不增加第二套打包器或手工重命名产物。构建后入口、异步 JS 和 CSS 均由 Utoopack 生成带哈希文件名。

### 2. 用独立脚本检查生产产物

新增 Node 脚本读取 `dist/index.html`：提取 `script[src]` 和样式 `link[href]`，忽略远程地址、`data:` 和页面锚点，将根路径引用映射到 `dist/` 后检查文件存在，并检查入口 JS 文件名包含足够长度的十六进制内容哈希。脚本接入 `pnpm check`，不依赖浏览器和服务器。

### 3. 使用生产静态预览验证真实拆包加载

新增 Playwright 生产配置，用只读取 `dist/` 的 Node 静态服务器提供构建产物和单页路由回退，再访问 `/` 和 `/movies`。测试记录 JS/CSS 响应失败和页面脚本错误；这样能发现 HTML 正确但异步文件缺失、懒加载失败或生产构建运行时报错的问题。静态服务器由 Playwright 全局准备在同一进程中启动和关闭，避免 Windows 下包管理器启动的预览子进程无法被及时回收。

### 4. 保留开发 E2E，CI 追加生产 E2E

现有 `pnpm e2e` 继续验证开发服务器。新增 `pnpm e2e:production` 验证生产产物，并在前端 CI 完成 `pnpm build` 后运行。`pnpm check` 仍不负责安装 Chromium，避免普通代码检查隐式下载浏览器。

## Risks / Trade-offs

- 带哈希文件名会使每次内容变化生成新路径，发布系统必须让 HTML 和静态资源来自同一镜像；当前 Docker 多阶段构建满足这一点。
- 旧镜像资源不会保留；正确做法是保证入口不复用和发布切换一致，不在镜像中无限保留旧文件。
- A 仍需在真实 Docker/Nginx 环境复核响应头和普通刷新，本 change 的本地预览不能替代服务器缓存检查。

## Migration Plan

1. C 合入内容哈希、产物检查和生产预览测试。
2. A 使用当前提交重新构建 Docker 镜像，检查 `index.html`、静态资源响应头和实际文件。
3. A 连续发布两个版本后使用普通刷新验证首页与懒加载页面；异常时回退应用镜像，不修改数据库。
