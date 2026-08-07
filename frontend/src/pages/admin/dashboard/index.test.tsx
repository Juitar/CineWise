import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import { setupTestEnvironment } from '../../../features/test-utils';
import AdminContentPage from './index';

const useAdminContentSync = vi.hoisted(() => vi.fn());
vi.mock('../../../modules/admin-content/hooks', () => ({ useAdminContentSync }));

setupTestEnvironment();

describe('AdminContentPage', () => {
  it('展示真实内容源字段', () => {
    useAdminContentSync.mockReturnValue({
      sources: [
        {
          provider: 'NetStart',
          resourceType: 'MOVIE',
          cityName: '长沙',
          status: 'SUCCESS',
          lastSuccessAt: '2026-08-07T10:00:00+08:00',
          expiresAt: '2026-08-08T10:00:00+08:00',
          isExpired: false,
          successCount: 12,
          failureCount: 0,
          failureCategory: null,
          licenseNotice: '仅用于开发和演示',
          startedAt: null,
          finishedAt: null,
          dataTime: null,
        },
      ],
      task: null,
      error: null,
      loading: false,
      submitting: false,
      resultUnknown: false,
      isTaskInProgress: false,
      refresh: vi.fn(),
      submit: vi.fn(),
      recover: vi.fn(),
    });
    render(<AdminContentPage />);
    expect(screen.getByRole('heading', { name: '内容同步状态' })).toBeInTheDocument();
    expect(screen.getByText('NetStart / MOVIE')).toBeInTheDocument();
    expect(screen.getByText('仅用于开发和演示')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '手动同步' })).toBeEnabled();
  });
});
