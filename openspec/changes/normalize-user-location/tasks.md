# Tasks

## 1. 跨模块确认

- [x] 1.1 B 已确认 `getWeather` 只接收唯一字段 `cinemaId`，值为无前导零的正十进制字符串；不保留 `cinemaArea`、地址、`placeText`、坐标或 `userId` 兼容入口。B 后续接入 Agent 时负责复核 Tool Schema、ID 校验和夹具；多影院地点只使用已有公开候选列表并由 B 发起选择。
- [x] 1.2 C 已确认浏览器坐标来自 `position.coords.longitude/latitude`；路线使用一次性用户坐标和影院坐标，天气只传 `cinemaId`；精确坐标不得写入存储、URL、日志、埋点、Agent 消息、SSE 或 Mock 快照；拒绝、失败、非法坐标或影院无坐标统一展示“路线暂不可用”。浏览器原始坐标超过 6 位小数时由 D 的 `BrowserUserLocationAdapter` 统一按 `HALF_UP` 四舍五入到 6 位，C 不自行截断。

## 2. D 位置基础

- [x] 2.1 D 实现 `geo` 的统一坐标、粒度、校验和浏览器/地点文本入口，并覆盖合法、缺失、越界、精度和歧义测试。
- [x] 2.2 D 实现高德地点文本 Adapter 与影院坐标查询服务，并以 HTTP Mock 覆盖成功、失败、超时和多候选处理。

## 3. 现有 D 用例替换

- [x] 3.1 D 以类型化坐标替换路线 Command、Provider 端口和 Amap Adapter，删除 `originValue` 与 `cinemaArea` 目的地，并覆盖粒度与隐私测试。
- [x] 3.2 D 以影院坐标逆地理得到 `adcode` 查询天气，按 `adcode` 缓存并保留标记明确的区域映射回退；`area` 缺失但影院坐标有效时仍必须尝试逆地理。
- [x] 3.3 D 以影院坐标替换餐饮查询和缓存键；在距离推荐中复用统一校验并标注直线距离。
- [x] 3.4 D 已检查本仓库 REST/OpenAPI、Agent Tool Schema、Mock、前后端类型和测试夹具；当前前端没有路线/定位请求入口，B 的 Agent Schema 由 B 后续接入并复核，未虚构调用方。

## 4. 验证与交付

- [x] 4.1 D 已运行单元、上下文和全量验证；扫描确认本次代码不把精确坐标写入 DB、Redis、日志、URL、Agent Prompt、轨迹或 SSE。
- [x] 4.2 D 已完成高德地点文本、路线和天气逆地理 HTTP Mock 验证；NetStart 坐标转城市及按用户位置查影院明确不做，因此不保留 NetStart Adapter 或其测试。
- [x] 4.3 D 已执行 `backend/mvnw.cmd verify`、`openspec validate normalize-user-location --strict`、`git diff --check` 和 `git status`。
