import { Avatar, Dropdown } from 'antd';
import type { MenuProps } from 'antd';
import React from 'react';
import { Link } from 'umi';

import { useLogout } from '../../modules/auth/useLogout';
import { DEMO_CITY_OPTIONS, type DemoCityCode } from '../../modules/content/demoCities';
import { useDemoCityNavigation } from '../../modules/content/useDemoCityNavigation';
import { useAuth } from '../../shared/auth/AuthProvider';
import { MapPinIcon, UserIcon } from '../../shared/components/icons/layout-icons';

export const DesktopTopBar: React.FC = () => {
  const { currentUser, status } = useAuth();
  const { handleLogout, isLoggingOut } = useLogout();
  const { city, selectCity } = useDemoCityNavigation();
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
      disabled: isLoggingOut,
      label: <span className="logout-menu-label">{isLoggingOut ? '正在退出' : '退出登录'}</span>,
    },
  ];

  const handleUserMenuClick: MenuProps['onClick'] = ({ key }) => {
    if (key !== 'logout' || isLoggingOut) {
      return;
    }
    void handleLogout();
  };

  const handleCityMenuClick: MenuProps['onClick'] = ({ key }) => {
    selectCity(key as DemoCityCode);
  };

  const cityMenu: MenuProps = {
    items: DEMO_CITY_OPTIONS.map((option) => ({
      key: option.code,
      label: option.name,
    })),
    onClick: handleCityMenuClick,
    selectedKeys: [city.code],
  };

  return (
    <header className="desktop-top-bar">
      <div className="desktop-top-bar-left">
        <Dropdown menu={cityMenu} trigger={['click']}>
          <button
            aria-label={`当前城市：${city.name}，点击切换城市`}
            className="desktop-location-selector"
            type="button"
          >
            <MapPinIcon size={16} />
            <span>{city.name}</span>
          </button>
        </Dropdown>
      </div>

      <div className="desktop-top-bar-right">
        {status === 'authenticated' && currentUser ? (
          <Dropdown
            menu={{ items: userMenu, onClick: handleUserMenuClick }}
            placement="bottomRight"
            trigger={['click']}
            arrow
          >
            <button type="button" className="desktop-user-profile">
              <Avatar className="user-avatar" icon={<UserIcon size={18} />} />
              <span className="desktop-user-name">{currentUser.nickname}</span>
            </button>
          </Dropdown>
        ) : (
          <Link className="desktop-login-link" to="/login">
            登录
          </Link>
        )}
      </div>
    </header>
  );
};
