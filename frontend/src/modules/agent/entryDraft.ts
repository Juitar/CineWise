let pendingDraft: string | null = null;

/** 暂存首页尚未提交的 Agent 输入；只存在当前 SPA 内存，不进入 URL 或浏览器存储。 */
export function setPendingAgentDraft(value: string): void {
  const normalized = value.trim();
  pendingDraft = normalized ? normalized.slice(0, 2000) : null;
}

/** 登录回跳后的工作区只读取一次草稿，避免路由重渲染时重复提交。 */
export function takePendingAgentDraft(): string | null {
  const current = pendingDraft;
  pendingDraft = null;
  return current;
}
