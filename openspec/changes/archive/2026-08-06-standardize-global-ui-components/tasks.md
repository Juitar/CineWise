## 1. 公共导航

- [x] 1.1 C 为用户端和管理端补充语义明确的 SVG 图标，并替换侧栏与移动端底栏的重复图标和 Emoji；验证：四个用户入口和三个管理入口图标均不同且含义正确。

## 2. 表单与管理页面

- [x] 2.1 C 将登录、注册密码字段改为 Ant Design `Input.Password`，验证码后缀改为 Ant Design `Button`；验证：眼睛垂直居中、可切换密码显示、原有登录测试通过。
- [x] 2.2 C 将 Agent 运行记录的通用列表、状态标签、表格和加载更多入口改为 Ant Design 组件；验证：选中记录、状态颜色与工具调用表格内容正常显示。

## 3. 验证

- [x] 3.1 C 运行 `pnpm format:check`、`pnpm lint`、`pnpm typecheck`、`pnpm test`、`pnpm build:verify`；验证：全部通过。
- [x] 3.2 C 运行 `openspec validate standardize-global-ui-components --strict`、`git diff --check`；验证：规则校验通过且无格式错误。
