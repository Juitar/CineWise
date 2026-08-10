## Why

出行建议页把 Provider 内部来源编码直接展示给用户，例如 `NETSTART_MAOYAN` 和 `AMAP_ROUTE`。用户无法理解这些编码，页面也因此出现中英文混合。

## What Changes

- 在出行建议页将影院内容、路线和天气的已知来源编码显示为中文名称。
- 未识别、为空的来源统一显示“来源待确认”，不回显内部编码。
- 保持现有面包屑和接口字段，不修改后端 API、数据库或第三方 Provider。

## Capabilities

### New Capabilities

- `travel-page-source-presentation`: 出行建议页以中文显示用户可见的数据来源。

### Modified Capabilities

- 无。

## Impact

- 影响 `frontend/src/pages/travel/index.tsx` 和新增的 `frontend/src/modules/travel/source-labels.ts`。
- D 前端负责实现和测试；不影响 A、B、C 的接口或数据。
