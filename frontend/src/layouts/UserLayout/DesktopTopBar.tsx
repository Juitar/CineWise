import { Avatar, Dropdown, Input } from 'antd';
import type { MenuProps } from 'antd';
import React from 'react';
import { Link } from 'umi';

import { MapPinIcon, SearchIcon, UserIcon } from '../../shared/components/icons/layout-icons';

export const DesktopTopBar: React.FC = () => {
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
      label: <span className="logout-menu-label">退出登录</span>,
    },
  ];

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
        {/* 用户头像下拉 */}
        <Dropdown menu={{ items: userMenu }} placement="bottomRight" arrow>
          <div className="desktop-user-profile">
            <Avatar className="user-avatar" icon={<UserIcon size={18} />} />
          </div>
        </Dropdown>
      </div>
    </header>
  );
};
