import { Menu } from 'antd';
import React from 'react';
import { Link, useLocation } from 'umi';
import { BrandLogoIcon } from '../../shared/components/icons';
import {
  FilmIcon,
  HomeIcon,
  MapPinIcon,
  UserIcon,
} from '../../shared/components/icons/layout-icons';

export const DesktopSidebar: React.FC = () => {
  const location = useLocation();

  const getSelectedKeys = () => {
    if (location.pathname === '/') return ['home'];
    if (location.pathname.startsWith('/movies')) return ['movies'];
    if (location.pathname.startsWith('/cinemas')) return ['cinemas'];
    if (location.pathname.startsWith('/profile')) return ['profile'];
    return [];
  };

  return (
    <aside className="desktop-sidebar">
      <div className="desktop-sidebar-logo">
        <Link to="/" className="brand-logo-link">
          <BrandLogoIcon size={32} />
          <span className="brand-logo-text">妙语购票</span>
        </Link>
      </div>

      <div className="desktop-sidebar-menu-wrapper">
        <Menu
          mode="inline"
          selectedKeys={getSelectedKeys()}
          className="desktop-sidebar-menu"
          items={[
            {
              key: 'home',
              icon: <HomeIcon size={18} />,
              label: <Link to="/">首页</Link>,
            },
            {
              key: 'movies',
              icon: <FilmIcon size={18} />,
              label: <Link to="/movies">影片</Link>,
            },
            {
              key: 'cinemas',
              icon: <MapPinIcon size={18} />,
              label: <Link to="/cinemas">影院</Link>,
            },
            {
              type: 'divider',
            },
            {
              key: 'profile',
              icon: <UserIcon size={18} />,
              label: <Link to="/profile">个人中心</Link>,
            },
          ]}
        />
      </div>
    </aside>
  );
};
