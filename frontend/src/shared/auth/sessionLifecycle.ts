type SessionEndHandler = () => void;

const sessionEndHandlers = new Set<SessionEndHandler>();

/** 注册登录会话结束清理；用于关闭 SSE 和清除仅属于当前用户的内存状态。 */
export function registerSessionEndHandler(handler: SessionEndHandler): () => void {
  sessionEndHandlers.add(handler);
  return () => sessionEndHandlers.delete(handler);
}

/** 在登出请求开始或 401 清理时同步通知当前页面停止私有活动。 */
export function notifySessionEnding(): void {
  sessionEndHandlers.forEach((handler) => handler());
}
