import { Dropdown } from 'antd';
import type { MenuProps } from 'antd';
import React, { useState } from 'react';
import { useNavigate } from 'umi';

import { useAuth } from '../../shared/auth/AuthProvider';
import './index.css';

export function AdminTopBar() {
  const navigate = useNavigate();
  const { currentUser, logout } = useAuth();
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const menuItems: MenuProps['items'] = [
    {
      key: 'logout',
      danger: true,
      disabled: isLoggingOut,
      label: isLoggingOut ? '正在退出' : '退出登录',
    },
  ];

  const handleMenuClick: MenuProps['onClick'] = ({ key }) => {
    if (key !== 'logout' || isLoggingOut) {
      return;
    }
    setIsLoggingOut(true);
    void logout().finally(() => {
      navigate('/login', { replace: true });
      setIsLoggingOut(false);
    });
  };

  return (
    <header className="admin-top-bar">
      <div className="admin-top-bar-left"></div>
      <div className="admin-top-bar-right">
        <Dropdown
          menu={{ items: menuItems, onClick: handleMenuClick }}
          placement="bottomRight"
          trigger={['click']}
        >
          <button type="button" className="admin-user-profile">
            <div className="admin-avatar" aria-hidden="true">
              管
            </div>
            <span className="admin-name">
              {currentUser?.nickname ?? '管理员'} <span className="arrow-down">v</span>
            </span>
          </button>
        </Dropdown>
      </div>
    </header>
  );
}
