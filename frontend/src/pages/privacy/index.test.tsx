import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';

import PrivacyPage from './index';

const mocks = vi.hoisted(() => ({
  navigate: vi.fn(),
}));

vi.mock('umi', () => ({
  useNavigate: () => mocks.navigate,
}));

describe('PrivacyPage', () => {
  it('使用通用返回操作回到来源页面', () => {
    render(<PrivacyPage />);

    fireEvent.click(screen.getByRole('button', { name: '返回' }));

    expect(mocks.navigate).toHaveBeenCalledWith(-1);
    expect(screen.queryByText('返回注册页')).not.toBeInTheDocument();
  });
});
