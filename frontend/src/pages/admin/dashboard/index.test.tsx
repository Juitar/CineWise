import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';

import AdminContentPage from './index';

describe('AdminContentPage', () => {
  it('接口未提供时只展示真实待接入状态', () => {
    render(<AdminContentPage />);

    expect(screen.getByRole('heading', { name: '内容同步状态' })).toBeInTheDocument();
    expect(screen.getByText('GET /api/v1/admin/content/sources')).toBeInTheDocument();
    expect(screen.getByText('D（内容与数据模块）')).toBeInTheDocument();
    expect(screen.queryByText('流浪地球2')).not.toBeInTheDocument();
    expect(screen.queryByText('今天 10:17:45')).not.toBeInTheDocument();
  });
});
