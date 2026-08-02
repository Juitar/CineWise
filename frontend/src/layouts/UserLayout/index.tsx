import { Outlet } from 'umi';

import { useMediaQuery } from '../../shared/hooks/useMediaQuery';
import { DesktopUserHeader } from './DesktopUserHeader';
import './index.css';
import { MobileUserHeader } from './MobileUserHeader';

export default function UserLayout() {
  const isMobile = useMediaQuery('(max-width: 1023px)');

  return (
    <div className="user-layout">
      {isMobile ? <MobileUserHeader /> : <DesktopUserHeader />}

      <main className="user-layout-content">
        <Outlet />
      </main>
    </div>
  );
}
