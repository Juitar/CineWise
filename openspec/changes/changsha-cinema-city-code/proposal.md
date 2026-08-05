## Why

真实影院同步当前把 NetStart 城市 ID `70` 同时保存为公开 `cityCode`，而前端和 `ContentController` 约定 `cityCode/location` 使用标准行政代码。用户已决定继续演示长沙，因此需要把公开代码统一为 `430100`，只在第三方 HTTP 适配器内部使用 `70`。

## What Changes

- 真实影院同步的公开查询和持久化城市代码改为长沙行政代码 `430100`。
- NetStart HTTP 适配器将 `430100` 映射为供应商城市 ID `70`，供应商 ID 不进入 DTO、数据库和前端 URL。
- 影院前端默认城市、页面文案和测试改为长沙 `430100`。
- 重新执行一次受控同步并验证 `/api/v1/cinemas?location=430100` 返回 LIVE 数据。

### 非范围

- 不增加多城市选择器，不支持其他城市映射。
- 不修改影片、场次、价格、库存或路线数据。
- 不执行 Flyway，不修改数据库结构。

## Capabilities

### New Capabilities

- `changsha-cinema-city-code`: 长沙影院对外统一使用 `430100`，Provider 内部使用 `70`。

### Modified Capabilities

无。

## Impact

- D 负责 Provider 映射和真实同步；C 负责影院页面默认城市。
- 影响 NetStart Provider/HTTP 适配器、影院查询前端和对应测试。
- 完成标志：前端请求 `location=430100`，接口返回 20 家 `sourceType=LIVE` 影院，响应记录的 `cityCode` 全部为 `430100`。
