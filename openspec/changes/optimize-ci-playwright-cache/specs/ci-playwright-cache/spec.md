## Purpose

减少 GitHub Actions 重复下载 Playwright Chromium 的时间，同时保持现有前端质量门和失败语义不变。

## ADDED Requirements

### Requirement: 完整前端验证复用浏览器缓存

`Frontend Verify` SHALL 在执行 Playwright 安装和浏览器测试前恢复 Linux Playwright 浏览器目录，并使用 Runner 操作系统和前端锁文件哈希作为缓存键。

#### Scenario: 缓存命中

- **WHEN** 相同操作系统和前端锁文件对应的 Chromium 缓存已经存在
- **THEN** workflow 恢复浏览器二进制，继续执行现有安装校验和全部浏览器测试

#### Scenario: 缓存未命中

- **WHEN** 首次运行或前端锁文件发生变化
- **THEN** workflow 按现有命令安装 Chromium 和系统依赖，并在运行结束后保存新的浏览器缓存

### Requirement: 部署前验证使用相同缓存策略

`Demo Deploy` 的前端验证 SHALL 使用与 `Frontend Verify` 相同的缓存路径和键规则，不得因缓存命中而跳过前端质量门或浏览器测试。

#### Scenario: dev 重复部署

- **WHEN** `dev` 上连续两个需要部署的版本使用相同前端锁文件
- **THEN** 后一次部署前验证可以恢复前一次保存的 Chromium 二进制，并仍执行质量门、E2E 和生产 Nginx 冒烟

### Requirement: 缓存故障不得降低验证范围

浏览器缓存 SHALL 仅作为下载加速层；安装命令、测试命令和失败结果必须保持原有语义。

#### Scenario: 缓存不存在或失效

- **WHEN** 缓存无法恢复、键发生变化或缓存内容不满足当前 Playwright 版本
- **THEN** Playwright 安装步骤下载所需 Chromium，任何安装或测试失败仍使 job 失败
