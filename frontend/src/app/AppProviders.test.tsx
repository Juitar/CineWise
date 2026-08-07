import dayjs from 'dayjs';
import { describe, expect, it } from 'vitest';

import './AppProviders';

describe('全局组件中文环境', () => {
  it('为 Ant Design 日期组件设置 dayjs 中文环境', () => {
    expect(dayjs.locale()).toBe('zh-cn');
  });
});
