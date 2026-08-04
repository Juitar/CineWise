import { Avatar, Dropdown, Input } from 'antd';
import type { MenuProps } from 'antd';
import React, { useState } from 'react';
import { Link, useNavigate } from 'umi';

import { useAuth } from '../../shared/auth/AuthProvider';
import { MapPinIcon, SearchIcon, UserIcon } from '../../shared/components/icons/layout-icons';

export const DesktopTopBar: React.FC = () => {
  const navigate = useNavigate();
  const { currentUser, logout, status } = useAuth();
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const userMenu: MenuProps['items'] = [
    {
      key: 'profile',
      label: <Link to="/profile">个人中心</Link>,
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
    setIsLoggingOut(true);
    void logout().finally(() => {
      navigate('/login', { replace: true });
      setIsLoggingOut(false);
    });
  };

  const cityMenu: MenuProps['items'] = [
    { key: 'hz', label: '杭州' },
    { key: 'sh', label: '上海' },
    { key: 'bj', label: '北京' },
    { key: 'sz', label: '深圳' },
  ];

  return (
    <header className="desktop-top-bar">
      <div className="desktop-top-bar-left">
        {/* 城市定位 */}
        <Dropdown menu={{ items: cityMenu }} placement="bottomLeft" arrow>
          <div className="desktop-location-selector">
            <MapPinIcon size={16} />
            <span>杭州</span>
            <span className="desktop-location-arrow">▼</span>
          </div>
        </Dropdown>

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
            arrow
          >
            <div className="desktop-user-profile">
              <Avatar className="user-avatar" icon={<UserIcon size={18} />} />
              <span className="desktop-user-name">{currentUser.nickname}</span>
            </div>
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
