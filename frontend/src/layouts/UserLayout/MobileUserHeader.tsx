import { NavBar } from 'antd-mobile';
import React from 'react';
import { useLocation, useNavigate } from 'umi';

export const MobileUserHeader: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();

  const getPageTitle = (path: string) => {
    if (path === '/') return '首页';
    if (path.startsWith('/movies')) return '影片';
    if (path.startsWith('/cinemas')) return '影院';
    if (path.startsWith('/profile')) return '我的';
    return '妙语购票';
  };

  const isHome = location.pathname === '/';

  return (
    <div className="mobile-user-header">
      <NavBar backArrow={!isHome} onBack={() => navigate(-1)} className="mobile-user-navbar">
        <span className="mobile-user-title">{getPageTitle(location.pathname)}</span>
      </NavBar>
    </div>
  );
};
