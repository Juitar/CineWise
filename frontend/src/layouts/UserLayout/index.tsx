import { Outlet } from 'umi';
import React from 'react';

import { useMediaQuery } from '../../shared/hooks/useMediaQuery';
import { DesktopSidebar } from './DesktopSidebar';
import { DesktopTopBar } from './DesktopTopBar';
import { MobileTabBar } from './MobileTabBar';
import { MobileUserHeader } from './MobileUserHeader';
import './index.css';

export default function UserLayout() {
  const isMobile = useMediaQuery('(max-width: 1023px)');

  return (
    <div className={`user-layout ${isMobile ? 'user-layout-mobile' : 'user-layout-desktop'}`}>
      {/* 桌面端左侧导航栏 */}
      {!isMobile && <DesktopSidebar />}

      {/* 右侧主内容区域（包含顶部导航和页面内容） */}
      <div className="user-layout-main-column">
        {/* 桌面端顶部导航栏 */}
        {!isMobile && <DesktopTopBar />}

        {/* 移动端顶部导航栏 */}
        {isMobile && <MobileUserHeader />}

        {/* 移动端顶部 NavBar 占位符 */}
        {isMobile && <div className="user-layout-header-placeholder" />}

        {/* 主体内容 */}
        <main className="user-layout-content">
          <Outlet />
        </main>
      </div>

      {/* 移动端底部导航 */}
      {isMobile && <MobileTabBar />}
    </div>
  );
}
