import type { ReactNode } from 'react';

import { AppProviders } from './app/AppProviders';
import './styles/global.css';

export function rootContainer(container: ReactNode) {
  return <AppProviders>{container}</AppProviders>;
}
