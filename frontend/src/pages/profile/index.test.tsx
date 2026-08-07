import { cleanup, fireEvent, render, screen } from '@testing-library/react';
import React from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import type { CurrentUser } from '../../modules/auth/types';
import type { ProfilePage as ProfilePageData } from '../../modules/profile/api';
import ProfilePage from './index';

const user: CurrentUser = {
  id: '1001',
  role: 'USER',
  nickname: '演示用户',
  emailMasked: 'u***@cinewise.test',
  emailVerified: true,
  status: 'NORMAL',
  privacyPolicyVersion: '2026-08-03',
};

const mocks = vi.hoisted(() => ({
  auth: { currentUser: null as CurrentUser | null },
  logout: { handleLogout: vi.fn(), isLoggingOut: false },
  profile: {
    consentEnabled: false,
    consentSaving: false,
    consentStateKnown: true,
    load: vi.fn(),
    notice: '未开启画像数据使用' as string | null,
    profile: null as ProfilePageData | null,
    saving: false,
    setConsentEnabled: vi.fn(),
    setEnabled: vi.fn(),
    state: 'consent-required',
  },
}));

vi.mock('umi', () => ({
  Link: ({ children, to, ...props }: React.PropsWithChildren<{ to: string }>) => (
    <a href={to} {...props}>
      {children}
    </a>
  ),
}));

vi.mock('../../shared/auth/AuthProvider', () => ({
  useAuth: () => mocks.auth,
}));

vi.mock('../../modules/auth/useLogout', () => ({
  useLogout: () => mocks.logout,
}));

vi.mock('../../modules/profile/useProfile', () => ({
  useProfile: () => mocks.profile,
}));

describe('ProfilePage', () => {
  beforeEach(() => {
    mocks.auth.currentUser = user;
    mocks.logout.handleLogout.mockReset().mockResolvedValue(undefined);
    mocks.logout.isLoggingOut = false;
    mocks.profile.consentEnabled = false;
    mocks.profile.consentSaving = false;
    mocks.profile.consentStateKnown = true;
    mocks.profile.notice = '未开启画像数据使用';
    mocks.profile.profile = null;
    mocks.profile.saving = false;
    mocks.profile.setConsentEnabled.mockReset().mockResolvedValue(undefined);
    mocks.profile.setEnabled.mockReset().mockResolvedValue(undefined);
    mocks.profile.state = 'consent-required';
  });

  afterEach(cleanup);

  it('展示认证接口提供的账号摘要和隐私入口', () => {
    render(<ProfilePage />);

    expect(screen.getByRole('heading', { level: 1, name: '个人中心' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { level: 2, name: '演示用户' })).toBeInTheDocument();
    expect(screen.getByText('u***@cinewise.test')).toBeInTheDocument();
    expect(screen.getByText('已验证')).toBeInTheDocument();
    expect(screen.getByText('2026-08-03')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: '查看隐私说明' })).toHaveAttribute('href', '/privacy');
  });

  it('显示订单入口，并在未同意时不展示旧画像数据', () => {
    render(<ProfilePage />);

    expect(screen.getByRole('link', { name: '我的订单' })).toHaveAttribute('href', '/orders');
    expect(screen.queryByText('我的观影记录')).not.toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'AI 观影画像' })).toBeInTheDocument();
    expect(screen.getByRole('status')).toHaveTextContent('未开启画像数据使用');
    expect(screen.getByRole('switch', { name: '使用用户画像' })).not.toBeChecked();
    expect(screen.queryByRole('checkbox', { name: '开启个性化' })).not.toBeInTheDocument();
    expect(screen.queryByText('星际穿越')).not.toBeInTheDocument();
  });

  it('已同意时区分画像数据使用和个性化两个开关', () => {
    mocks.profile.consentEnabled = true;
    mocks.profile.notice = null;
    mocks.profile.profile = {
      preference: { enabled: false, updatedAt: '2026-08-07T00:00:00Z', version: 3 },
      tags: [],
      total: 0,
    };
    mocks.profile.state = 'ready';
    render(<ProfilePage />);

    const consentToggle = screen.getByRole('switch', { name: '使用用户画像' });
    const personalizationToggle = screen.getByRole('checkbox', { name: '开启个性化' });
    expect(consentToggle).toBeChecked();
    expect(personalizationToggle).not.toBeChecked();

    fireEvent.click(consentToggle);
    fireEvent.click(personalizationToggle);
    expect(mocks.profile.setConsentEnabled).toHaveBeenCalledWith(false);
    expect(mocks.profile.setEnabled).toHaveBeenCalledWith(true);
  });

  it('画像同意提交中禁用两个开关', () => {
    mocks.profile.consentEnabled = true;
    mocks.profile.consentSaving = true;
    mocks.profile.notice = null;
    mocks.profile.profile = {
      preference: { enabled: true, updatedAt: '2026-08-07T00:00:00Z', version: 3 },
      tags: [],
      total: 0,
    };
    mocks.profile.state = 'ready';
    render(<ProfilePage />);

    expect(screen.getByRole('switch', { name: '使用用户画像' })).toBeDisabled();
    expect(screen.getByRole('checkbox', { name: '开启个性化' })).toBeDisabled();
    expect(screen.getByText('正在保存')).toBeInTheDocument();
  });

  it('通过共享退出 Hook 执行退出并显示提交中状态', () => {
    const { rerender } = render(<ProfilePage />);

    fireEvent.click(screen.getByRole('button', { name: '退出登录' }));
    expect(mocks.logout.handleLogout).toHaveBeenCalledOnce();

    mocks.logout.isLoggingOut = true;
    rerender(<ProfilePage />);
    expect(screen.getByRole('button', { name: '正在退出' })).toBeDisabled();
  });

  it('用户摘要缺失时不展示默认资料', () => {
    mocks.auth.currentUser = null;
    render(<ProfilePage />);

    expect(screen.getByRole('alert')).toHaveTextContent('暂时无法读取个人资料');
    expect(screen.queryByRole('link', { name: '我的订单' })).not.toBeInTheDocument();
    expect(screen.queryByText('演示用户')).not.toBeInTheDocument();
  });
});
