import { TabBar } from 'antd-mobile';
import React from 'react';
import { useLocation, useNavigate } from 'umi';

import { FilmIcon, UserIcon } from '../../shared/components/icons/layout-icons';

export const MobileTabBar: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();

  const setRouteActive = (value: string) => {
    navigate(value);
  };

  const tabs = [
    {
      key: '/',
      title: '首页',
      icon: <FilmIcon size={24} />,
    },
    {
      key: '/movies',
      title: '影片',
      icon: <FilmIcon size={24} />,
    },
    {
      key: '/cinemas',
      title: '影院',
      icon: <FilmIcon size={24} />,
    },
    {
      key: '/profile',
      title: '我的',
      icon: <UserIcon size={24} />,
    },
  ];

  return (
    <div className="mobile-tab-bar">
      <TabBar activeKey={location.pathname} onChange={setRouteActive}>
        {tabs.map((item) => (
          <TabBar.Item key={item.key} icon={item.icon} title={item.title} />
        ))}
      </TabBar>
    </div>
  );
};
