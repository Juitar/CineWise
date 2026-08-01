# CineWise 前端

本目录预留给 CineWise Umi 前端工程，由前端负责人按照前端系统分析设计与 OpenSpec 契约初始化。

前端工程应独立维护自己的 `package.json`、`src/`、构建配置、测试和 Dockerfile，不与 `backend/` 共享源码目录或应用级环境配置。前后端通过冻结的 REST、SSE 和 Agent 卡片契约联调。
