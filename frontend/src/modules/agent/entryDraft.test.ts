import { describe, expect, it } from 'vitest';

import { setPendingAgentDraft, takePendingAgentDraft } from './entryDraft';

describe('首页 Agent 草稿', () => {
  it('只在当前内存读取一次且不保留空输入', () => {
    setPendingAgentDraft('  推荐一部电影  ');
    expect(takePendingAgentDraft()).toBe('推荐一部电影');
    expect(takePendingAgentDraft()).toBeNull();
    setPendingAgentDraft('   ');
    expect(takePendingAgentDraft()).toBeNull();
  });
});
