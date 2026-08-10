## ADDED Requirements

### Requirement: 生产网页显示妙语购票标题

前端 SHALL 将全局网页标题设置为“妙语购票”，生产构建生成的 `index.html` SHALL 包含 `<title>妙语购票</title>`。

#### Scenario: 用户打开服务器页面

- **WHEN** 用户在浏览器打开用户端或管理端页面
- **THEN** 浏览器标签页标题显示“妙语购票”

#### Scenario: 生产构建标题错误

- **WHEN** 生产构建生成的 `index.html` 标题缺失或不是“妙语购票”
- **THEN** 生产产物检查失败并指出当前标题
