## Context

当前前后端基础验证 workflow 没有路径过滤。历史纯 OpenSpec PR 的前端验证约耗时 1 分 54 秒、后端验证约耗时 2 分 19 秒；前端加入生产构建与 Nginx 冒烟后，完整验证约为 3 分钟。纯文档变更在 PR 和合并后的 `dev` push 会重复运行这些质量门。

直接在 workflow 顶层增加 `paths` 虽然简单，但如果 `Frontend Verify / verify` 或 `Backend Verify / verify` 是分支保护的必需检查，整个 workflow 不创建时可能让检查保持 Pending。因此本次保持 workflow 和 job 始终存在，只条件跳过重型步骤。

## Decisions

### 1. 使用仓库内原生 Git 脚本判断范围

新增 `.github/scripts/detect-ci-changes.sh`，接收需要观察的路径，并通过事件提供的 base/head SHA 执行 `git diff --quiet`。PR 使用 `base...head` 三点范围，只判断当前分支相对合并基线新增的变更，避免把目标分支合并基点之后的独立提交误算进来；push 使用 `before..head` 两点范围，判断本次推送实际引入的变更。脚本把 `run=true/false` 写入 `GITHUB_OUTPUT`，同时输出比较范围和判断结果。

不引入第三方 changed-files Action，避免新增供应链依赖和权限。Checkout 使用完整历史，确保 PR base/head 与 push before/head 对象可用；如果 SHA 为空、全零或对象缺失，脚本回退为 `run=true`。

### 2. 保持稳定的 job 名称和成功结果

`Frontend Verify` 与 `Backend Verify` 仍对所有 PR 及 `main/dev` push 触发，并保留名为 `verify` 的 job。Checkout 和范围判断始终运行，后续步骤使用同一输出条件。

纯文档变更时 job 正常成功，而不是整个 workflow 缺席或失败；现有分支保护无需改名即可继续使用。

### 3. 路径边界

前端完整验证的输入为：

- `frontend/**`
- `.github/workflows/frontend-verify.yml`
- `.github/scripts/detect-ci-changes.sh`

后端完整验证的输入为：

- `backend/**`
- `.github/workflows/backend-verify.yml`
- `.github/scripts/detect-ci-changes.sh`

普通 `docs/**`、Markdown 和 OpenSpec 不触发前后端重型质量门。MySQL/Redis 集成 workflow 已有独立路径过滤，本次不修改。Demo Deploy 已排除纯文档变化且承担发布阶段质量门，本次保持不变。

### 4. 同一 PR 或分支取消过时运行

两个 workflow 分别设置 concurrency group，PR 使用 PR 编号，push 使用完整 Git ref；`cancel-in-progress: true` 确保新提交替代旧提交。前后端使用不同 group 前缀，不会互相取消。

## Risks / Trade-offs

- 完整历史 checkout 比默认浅克隆多传输 Git 元数据，但明显小于安装依赖和浏览器的成本。
- 路径清单遗漏会导致必要质量门被跳过；workflow 文件和检测脚本自身始终触发完整验证，并用四类历史提交范围测试路径矩阵。
- 本次不缓存 Playwright 或 Docker layer；先验证触发正确性，后续再独立评审缓存优化。

## Migration Plan

1. 在个人分支验证检测脚本对文档、前端、后端和混合变更的输出。
2. 创建 PR，确认 workflow 自身变更会触发前后端完整质量门。
3. 合并后使用下一次纯文档 PR 确认两个 `verify` job 快速成功。
4. 稳定后再评估 Playwright browser cache 与 Buildx GHA cache。
