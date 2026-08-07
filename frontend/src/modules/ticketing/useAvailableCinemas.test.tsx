import { act, renderHook, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { ApiError } from '../../shared/api/ApiError';
import { useAvailableCinemas } from './useAvailableCinemas';

const mocks = vi.hoisted(() => ({ getAvailableCinemas: vi.fn() }));

vi.mock('./api', () => ({ getAvailableCinemas: mocks.getAvailableCinemas }));

const response = { total: 0, page: 1, size: 20, records: [] };

describe('useAvailableCinemas', () => {
  beforeEach(() => mocks.getAvailableCinemas.mockReset());

  it('查询失败后可对同一个影片重试', async () => {
    mocks.getAvailableCinemas
      .mockRejectedValueOnce(new ApiError('offline', { kind: 'NETWORK' }))
      .mockResolvedValueOnce(response);
    const { result } = renderHook(() => useAvailableCinemas('10001'));

    await waitFor(() => expect(result.current.error?.kind).toBe('NETWORK'));
    act(() => result.current.retry());
    await waitFor(() => expect(result.current.data).toEqual(response));
    expect(mocks.getAvailableCinemas).toHaveBeenNthCalledWith(2, '10001', expect.any(AbortSignal));
  });
});
