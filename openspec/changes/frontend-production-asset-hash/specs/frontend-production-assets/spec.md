## Purpose

保证浏览器在连续部署后使用当前版本入口，并在合并前发现生产 HTML 引用缺失资源或生产路由无法加载的问题。

## ADDED Requirements

### Requirement: 生产资源使用内容哈希

前端生产构建 SHALL 为入口 JS、异步 JS 和 CSS 生成带内容哈希的文件名，`index.html` SHALL NOT 引用固定的 `/umi.js`。

#### Scenario: 连续构建不同版本

- **WHEN** 前端源代码变化后重新执行生产构建
- **THEN** 入口和相关异步资源使用带内容哈希的文件名，不再依赖固定 `/umi.js`

### Requirement: 生产 HTML 只引用存在的本地资源

生产构建检查 SHALL 解析 `dist/index.html` 中的本地 JS/CSS 引用，并确认每个引用在 `dist/` 中存在；入口 JS 文件名 SHALL 包含内容哈希。

#### Scenario: HTML 引用缺失资源

- **WHEN** `dist/index.html` 引用了 `dist/` 中不存在的本地 JS 或 CSS
- **THEN** 生产构建检查失败并输出缺失资源路径

#### Scenario: 入口仍为固定文件名

- **WHEN** `dist/index.html` 的入口脚本仍是 `/umi.js` 或其他不带内容哈希的名称
- **THEN** 生产构建检查失败并指出入口脚本名称

### Requirement: 生产页面和懒加载资源可以打开

生产预览浏览器检查 SHALL 打开首页和至少一个懒加载路由，确认页面完成渲染，且没有 JS/CSS 请求失败。

#### Scenario: 打开生产首页和影片页

- **WHEN** 浏览器访问生产预览的 `/`，再访问懒加载路由 `/movies`
- **THEN** 两个页面均显示各自的主标题，且页面加载期间没有 JS/CSS 请求返回失败

### Requirement: 发布缓存由 A 复核

A SHALL 在 Docker/Nginx 演示环境确认 `index.html` 不缓存或每次重新校验，带内容哈希的静态资源可以长期缓存，并确认实际发布文件与当前构建一致。

#### Scenario: 演示服务器发布新版本

- **WHEN** 本 change 合入并由 A 在演示服务器重新构建前端镜像
- **THEN** 普通刷新取得当前入口，首页和懒加载页面均能打开，旧入口不会继续引用已删除的上一版资源
