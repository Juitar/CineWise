## 1. 契约确认

- [x] 1.1 A 接手并确认复用既有 `UserLocationAdapter`/`BasicRouteService`，采用唯一 POI/ADDRESS 和 107004/107005 错误语义。
- [x] 1.2 A 确认前端 DTO、错误展示、页面交互和精确地点文本不进入公共日志或存储。

## 2. A 后端

- [x] 2.1 扩展公开路线请求 DTO 与 Application 入口，分别校验浏览器坐标和手动地点，拒绝混合输入。
- [x] 2.2 复用 `UserLocationAdapter.fromPlaceText()`，拒绝歧义、空结果和 CITY/DISTRICT，不返回或记录坐标与地点原文。
- [x] 2.3 为正常、越权、未确认、歧义、粒度不足、Provider 失败和隐私边界补充测试与 OpenAPI。

## 3. A 前端

- [x] 3.1 更新 travel 类型、契约解析、API 和 Hook，保证路线 POST 不自动重发。
- [x] 3.2 实现当前位置/手动地点切换、隐私确认、HTTP/拒绝定位提示、手动地点输入和错误展示。
- [x] 3.3 覆盖桌面/移动端、定位失败回退、手动成功、歧义、粒度不足、422/503 和离开页面清理。

## 4. 验证与交付

- [ ] 4.1 执行 D 后端验证、C `pnpm check`、严格 OpenSpec 校验及日志/响应/存储泄露扫描。
- [ ] 4.2 D/C 联调确认后，记录部署 HTTPS 与手动地点兜底的验收结果。
