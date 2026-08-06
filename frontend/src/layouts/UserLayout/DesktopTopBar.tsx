import { Avatar, Dropdown, Input } from 'antd';
import type { MenuProps } from 'antd';
import React from 'react';
import { Link } from 'umi';

import { useLogout } from '../../modules/auth/useLogout';
import { useAuth } from '../../shared/auth/AuthProvider';
import { MapPinIcon, SearchIcon, UserIcon } from '../../shared/components/icons/layout-icons';

export const DesktopTopBar: React.FC = () => {
  const { currentUser, status } = useAuth();
  const { handleLogout, isLoggingOut } = useLogout();
  const userMenu: MenuProps['items'] = [
    {
      key: 'profile',
      label: <Link to="/profile">个人中心</Link>,
    },
    {
      key: 'orders',
      label: <Link to="/orders">我的订单</Link>,
    },
    {
      type: 'divider',
    },
    {
      key: 'logout',
      disabled: isLoggingOut,
      label: <span className="logout-menu-label">{isLoggingOut ? '正在退出' : '退出登录'}</span>,
    },
  ];

  const handleUserMenuClick: MenuProps['onClick'] = ({ key }) => {
    if (key !== 'logout' || isLoggingOut) {
      return;
    }
    void handleLogout();
  };

  return (
    <header className="desktop-top-bar">
      <div className="desktop-top-bar-left">
        <div className="desktop-location-selector" aria-label="当前城市：长沙">
          <MapPinIcon size={16} />
          <span>长沙</span>
        </div>

        {/* 全局搜索框 */}
        <Input
          className="desktop-search-input"
          placeholder="搜索电影、影院"
          prefix={<SearchIcon size={16} className="desktop-search-icon" />}
        />
      </div>

      <div className="desktop-top-bar-right">
        {status === 'authenticated' && currentUser ? (
          <Dropdown
            menu={{ items: userMenu, onClick: handleUserMenuClick }}
            placement="bottomRight"
            trigger={['click']}
            arrow
          >
            <button type="button" className="desktop-user-profile">
              <Avatar className="user-avatar" icon={<UserIcon size={18} />} />
              <span className="desktop-user-name">{currentUser.nickname}</span>
            </button>
          </Dropdown>
        ) : (
          <Link className="desktop-login-link" to="/login">
            登录
          </Link>
        )}
      </div>
    </header>
  );
};
