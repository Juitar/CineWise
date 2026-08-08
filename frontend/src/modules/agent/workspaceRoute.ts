const AGENT_WORKSPACE_PATTERN = /^\/recommendations\/([^/]+)/;

export function agentWorkspaceBase(pathname: string): string | null {
  const match = AGENT_WORKSPACE_PATTERN.exec(pathname);
  return match ? `/recommendations/${encodeURIComponent(decodeURIComponent(match[1]))}` : null;
}

/** 在 Agent 中进入购票子页面时保留右侧对话；普通页面继续使用原路由。 */
export function workspacePath(pathname: string, target: string): string {
  const base = agentWorkspaceBase(pathname);
  if (!base || !target.startsWith('/')) return target;
  return `${base}${target}`;
}
