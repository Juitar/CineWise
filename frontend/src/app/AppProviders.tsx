import { ConfigProvider as DesktopConfigProvider } from 'antd';
import desktopZhCN from 'antd/locale/zh_CN';
import { ConfigProvider as MobileConfigProvider } from 'antd-mobile';
import 'antd-mobile/es/global';
import mobileZhCN from 'antd-mobile/es/locales/zh-CN';
import type { PropsWithChildren } from 'react';

import { AppErrorBoundary } from './AppErrorBoundary';

/** 为桌面端和移动端组件提供统一的中文环境。 */
export function AppProviders({ children }: PropsWithChildren) {
  return (
    <AppErrorBoundary>
      <DesktopConfigProvider locale={desktopZhCN}>
        <MobileConfigProvider locale={mobileZhCN}>{children}</MobileConfigProvider>
      </DesktopConfigProvider>
    </AppErrorBoundary>
  );
}
