import { ConfigProvider as DesktopConfigProvider } from 'antd';
import desktopZhCN from 'antd/locale/zh_CN';
import { ConfigProvider as MobileConfigProvider } from 'antd-mobile';
import 'antd-mobile/es/global';
import mobileZhCN from 'antd-mobile/es/locales/zh-CN';
import dayjs from 'dayjs';
import 'dayjs/locale/zh-cn';
import type { PropsWithChildren } from 'react';

import { AuthProvider } from '../shared/auth/AuthProvider';
import { AppErrorBoundary } from './AppErrorBoundary';

dayjs.locale('zh-cn');

/** 为桌面端和移动端组件提供统一的中文环境。 */
export function AppProviders({ children }: PropsWithChildren) {
  return (
    <AppErrorBoundary>
      <DesktopConfigProvider locale={desktopZhCN}>
        <MobileConfigProvider locale={mobileZhCN}>
          <AuthProvider>{children}</AuthProvider>
        </MobileConfigProvider>
      </DesktopConfigProvider>
    </AppErrorBoundary>
  );
}
