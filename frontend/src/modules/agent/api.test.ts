import { beforeEach, describe, expect, it, vi } from 'vitest';

const api = vi.hoisted(() => ({ apiRequest: vi.fn() }));
vi.mock('../../shared/api/client', () => api);

import { resolveBrowserCity } from './api';

describe('resolveBrowserCity', () => {
  beforeEach(() => {
    api.apiRequest.mockReset();
    api.apiRequest.mockResolvedValue({ city: '长沙市' });
  });

  it('normalizes high precision browser coordinates before validation', async () => {
    await expect(resolveBrowserCity(112.93881460000001, 28.228208500000004)).resolves.toBe(
      '长沙市',
    );
    expect(api.apiRequest).toHaveBeenCalledWith(
      '/api/v1/agent/location/city',
      expect.objectContaining({
        body: { longitude: 112.938815, latitude: 28.228209 },
      }),
    );
  });
});
