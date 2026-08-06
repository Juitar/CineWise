# Proposal: 购票主线前端规范整改

## 背景

场次、选座和订单确认页面已具备购票功能，但早期实现仍有页面层承载交易逻辑、移动端组件未统一、时间与金额处理分散等问题。本变更将其整理到现有前端规范和公开 A 票务契约下。

## 范围

- 整理 `/shows`、`/shows/:showId/seats`、`/orders/confirm` 的页面与模块边界。
- 移动端小于 1024px 使用 antd-mobile 的加载、错误和主要操作组件。
- 统一时间格式、金额展示、订单幂等恢复和场次/座位有效性校验。
- 接入 `OrderCreateSuccess` 展示组件，删除过期的下一迭代文案。
- 补充 PC/移动端组件、模块和 E2E 测试。

## 非范围

- 不修改 C 的路由、RequireAuth、JWT、Cookie、shared/api、布局、主题和 `.umirc.ts`。
- 不修改 A 后端接口、数据库、迁移、价格或库存规则。
- 前端展示 AI 不实现 API、Hook、请求、幂等或状态恢复逻辑。

## Owner

- A：modules、pages 编排、真实接口接线、状态恢复、时间金额工具和测试。
- 前端展示 AI：features、页面展示、CSS、antd/antd-mobile 视图与组件测试。

## 验收

- 页面只组合公开模块 Hook，不在页面内实现交易持久化或金额业务计算。
- PC 与移动端均能完成场次→选座→确认建单，响应未知只查询原结果。
- `pnpm check`、购票 P1 E2E、OpenSpec strict 和差异检查通过。
