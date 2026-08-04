# CineWise 前端

CineWise 前端是一个 Umi 4、React 18 和 TypeScript 5 单页应用。同一工程提供 PC 用户端、移动 H5 和管理端，三类页面共享路由、DTO、请求层和业务 Hook，视图组件可以分别实现。

## 环境要求

- Node.js `24.18.0`
- pnpm `10.34.5`

版本同时记录在 `.node-version`、`package.json` 和 `pnpm-lock.yaml` 中。不要使用 `latest` 安装依赖，也不要只修改 `package.json` 而不提交锁文件。

## 本地启动

```powershell
cd D:\code\CineWise\CineWise\frontend
pnpm install --frozen-lockfile
pnpm dev
```

开发地址默认为 `http://127.0.0.1:8000`。开发服务器把 `/api/**` 转发到 `http://127.0.0.1:8080`，浏览器代码不硬编码后端地址。

## 容器运行

根目录 `compose.yaml` 已包含前端服务。准备好根目录 `.env` 后执行：

```powershell
cd D:\code\CineWise\CineWise
docker compose up --build frontend
```

容器使用 Nginx 提供静态文件，把 `/api/**` 转发给 Compose 中的 `backend:8080`。Nginx 已关闭 API 响应缓冲，后续 Agent POST SSE 可以持续接收分块响应。

## 常用命令

| 命令                  | 用途                                         |
| --------------------- | -------------------------------------------- |
| `pnpm dev`            | 启动开发服务器和热更新                       |
| `pnpm build`          | 生成 `dist/` 生产文件                        |
| `pnpm build:verify`   | 构建并检查入口哈希与 HTML 静态资源完整性     |
| `pnpm check`          | 依次执行排版、代码、类型、测试和生产产物检查 |
| `pnpm format`         | 自动整理前端文件排版                         |
| `pnpm format:check`   | 检查排版，不修改文件                         |
| `pnpm lint`           | 检查 React、TypeScript 和 CSS 规则           |
| `pnpm typecheck`      | 检查 TypeScript 类型，不生成文件             |
| `pnpm test`           | 运行单元和组件测试                           |
| `pnpm test:coverage`  | 生成测试覆盖率报告                           |
| `pnpm e2e:install`    | 安装 Playwright Chromium                     |
| `pnpm e2e`            | 在桌面和手机尺寸执行浏览器流程测试           |
| `pnpm e2e:production` | 验证生产首页、懒加载页面和 JS/CSS 资源       |

## 源码目录

```text
src/
├── app/          应用 Provider、权限和错误边界
├── layouts/      用户端与管理端公共布局
├── pages/        Umi 路由页面，只负责页面组合
├── modules/      auth/content/agent/ticketing/order/travel/profile/admin
├── features/     Agent 工作区、动态卡片、座位图和支付状态等复杂组件
├── shared/
│   ├── api/      普通 REST 公共客户端和统一错误
│   ├── components/
│   ├── hooks/
│   └── types/    跨模块公开 DTO
└── styles/       主题变量和全局 CSS
```

Git 不记录空目录。某个模块第一次出现实际代码时再创建对应目录，不提交无意义的 `.gitkeep`。

## 普通 REST 请求

所有普通接口必须通过 `src/shared/api/client.ts` 的 `apiRequest<T>()` 调用：

```ts
const movie = await apiRequest<MovieSummary>('/api/v1/movies/1001');
```

公共客户端负责：

- 仅允许 `/api/**` 路径；
- 携带 HttpOnly Cookie，但不读取或保存 Token；
- 编码查询参数并解析后端 `Result<T>`；
- 将 HTTP、业务码、超时、取消和错误响应转成 `ApiError`；
- 写请求断网或超时时标记 `isResultUnknown`，调用模块只能查询原操作结果；
- 不自动重试创建订单、支付、退票等写请求。

Agent POST SSE 不使用该客户端，后续由独立流式客户端处理响应流、`Last-Event-ID` 和 `AbortController`。

## 当前已知事项

- Umi 4 的 request 插件只能通过 `@umijs/plugins` 大合集安装，该包会带入 DVA、旧 React 生态和大量无关依赖，因此本项目改用唯一的原生 `fetch` REST 客户端。
- 后端当前仍是默认拒绝业务接口的安全壳。JWT Cookie、CSRF、`/auth/me` 和 401/403 规则完成后才能进行登录联调。
- Umi 的 Utoo Pack 间接依赖会提示 `postcss` 和 `less-loader` peer 版本不同；当前开发和生产构建均已验证通过。项目不使用 Less 或 Sass，不为消除提示额外安装未使用的预处理器。

## 官方资料

- [Umi 快速上手](https://umijs.org/docs/guides/getting-started)
- [React 学习教程](https://react.dev/learn)
- [TypeScript 手册](https://www.typescriptlang.org/docs/handbook/intro.html)
- [Ant Design](https://ant.design/docs/react/introduce-cn)
- [Ant Design Mobile](https://mobile.ant.design/zh/guide/quick-start)
- [pnpm](https://pnpm.io/zh/)
- [Vitest](https://vitest.dev/guide/)
- [Playwright](https://playwright.dev/docs/intro)

项目编码要求见 [`../docs/frontend-coding-standards.md`](../docs/frontend-coding-standards.md)。
