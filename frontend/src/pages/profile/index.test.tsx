import { cleanup, fireEvent, render, screen, within } from '@testing-library/react';
import { Modal } from 'antd';
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
    createTag: vi.fn(),
    deleteTag: vi.fn(),
    notice: '未开启画像数据使用' as string | null,
    profile: null as ProfilePageData | null,
    saving: false,
    setConsentEnabled: vi.fn(),
    setEnabled: vi.fn(),
    state: 'consent-required',
    updateTag: vi.fn(),
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
    mocks.profile.createTag.mockReset().mockResolvedValue(undefined);
    mocks.profile.deleteTag.mockReset().mockResolvedValue(undefined);
    mocks.profile.setEnabled.mockReset().mockResolvedValue(undefined);
    mocks.profile.updateTag.mockReset().mockResolvedValue(undefined);
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

  it('可以选择六种标签类型和电影类型值并添加标签', () => {
    mocks.profile.consentEnabled = true;
    mocks.profile.notice = null;
    mocks.profile.profile = {
      preference: { enabled: true, updatedAt: '2026-08-07T00:00:00Z', version: 3 },
      tags: [],
      total: 0,
    };
    mocks.profile.state = 'ready';
    render(<ProfilePage />);

    expect(screen.getByRole('option', { name: '电影类型' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: '观影时段' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: '影院' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: '影厅' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: '价格区间' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: '座位偏好' })).toBeInTheDocument();

    fireEvent.change(screen.getByLabelText('标签值'), { target: { value: '科幻' } });
    fireEvent.submit(screen.getByRole('button', { name: '添加标签' }));

    expect(mocks.profile.createTag).toHaveBeenCalledWith({
      type: 'MOVIE_GENRE',
      value: '科幻',
      polarity: 'LIKE',
      weight: 1,
    });
  });

  it('可以修改倾向、停用和删除本人手动标签', () => {
    mocks.profile.consentEnabled = true;
    mocks.profile.notice = null;
    mocks.profile.profile = {
      preference: { enabled: true, updatedAt: '2026-08-07T00:00:00Z', version: 3 },
      tags: [
        {
          confidence: 1,
          expiresAt: null,
          id: '1001',
          polarity: 'LIKE',
          source: 'MANUAL',
          status: 'ACTIVE',
          type: 'MOVIE_GENRE',
          updatedAt: '2026-08-07T00:00:00Z',
          value: '科幻',
          version: 0,
          weight: 1,
        },
      ],
      total: 1,
    };
    mocks.profile.state = 'ready';
    const confirm = vi.spyOn(Modal, 'confirm').mockImplementation((config) => {
      void config.onOk?.();
      return { destroy: vi.fn(), update: vi.fn() };
    });
    render(<ProfilePage />);

    fireEvent.change(screen.getByRole('combobox', { name: '修改科幻倾向' }), {
      target: { value: 'DISLIKE' },
    });
    fireEvent.click(screen.getByRole('button', { name: '停用' }));
    fireEvent.click(screen.getByRole('button', { name: '删除' }));

    expect(mocks.profile.updateTag).toHaveBeenNthCalledWith(1, '1001', {
      polarity: 'DISLIKE',
      weight: 1,
    });
    expect(mocks.profile.updateTag).toHaveBeenNthCalledWith(2, '1001', { status: 'DISABLED' });
    expect(mocks.profile.deleteTag).toHaveBeenCalledWith('1001');
    expect(confirm).toHaveBeenCalledWith(
      expect.objectContaining({ title: '确认删除画像标签', okText: '删除', cancelText: '取消' }),
    );
    vi.restoreAllMocks();
  });

  it('只向可操作的手动标签展示倾向和状态操作', () => {
    mocks.profile.consentEnabled = true;
    mocks.profile.notice = null;
    mocks.profile.profile = {
      preference: { enabled: true, updatedAt: '2026-08-07T00:00:00Z', version: 3 },
      tags: [
        {
          confidence: 1,
          expiresAt: null,
          id: '1001',
          polarity: 'LIKE',
          source: 'MANUAL',
          status: 'DISABLED',
          type: 'MOVIE_GENRE',
          updatedAt: '2026-08-07T00:00:00Z',
          value: '科幻',
          version: 0,
          weight: 1,
        },
        {
          confidence: 0.8,
          expiresAt: null,
          id: '1002',
          polarity: 'LIKE',
          source: 'CONVERSATION',
          status: 'ACTIVE',
          type: 'TIME',
          updatedAt: '2026-08-07T00:00:00Z',
          value: '晚上',
          version: 0,
          weight: 0.8,
        },
        {
          confidence: 0.5,
          expiresAt: '2026-09-01T00:00:00Z',
          id: '1003',
          polarity: 'DISLIKE',
          source: 'BEHAVIOR',
          status: 'DISABLED',
          type: 'PRICE',
          updatedAt: '2026-08-07T00:00:00Z',
          value: '¥30 以下',
          version: 0,
          weight: 0.5,
        },
      ],
      total: 3,
    };
    mocks.profile.state = 'ready';
    render(<ProfilePage />);

    const manualDisabledRow = screen
      .getByText('科幻', { selector: 'strong' })
      .closest('.profile-tag-item');
    const conversationRow = screen
      .getByText('晚上', { selector: 'strong' })
      .closest('.profile-tag-item');
    const behaviorRow = screen
      .getByText('¥30 以下', { selector: 'strong' })
      .closest('.profile-tag-item');
    expect(manualDisabledRow).not.toBeNull();
    expect(conversationRow).not.toBeNull();
    expect(behaviorRow).not.toBeNull();

    expect(
      within(manualDisabledRow as HTMLElement).getByRole('button', { name: '恢复' }),
    ).toBeInTheDocument();
    expect(
      within(manualDisabledRow as HTMLElement).queryByText('修改倾向'),
    ).not.toBeInTheDocument();
    expect(within(conversationRow as HTMLElement).queryByText('修改倾向')).not.toBeInTheDocument();
    expect(
      within(conversationRow as HTMLElement).queryByRole('button', { name: '停用' }),
    ).not.toBeInTheDocument();
    expect(within(behaviorRow as HTMLElement).queryByText('修改倾向')).not.toBeInTheDocument();
    expect(
      within(behaviorRow as HTMLElement).queryByRole('button', { name: '恢复' }),
    ).not.toBeInTheDocument();
  });
});
