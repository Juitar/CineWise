import { Dropdown, NavBar, Selector } from 'antd-mobile';
import React, { useState } from 'react';
import { useLocation, useNavigate } from 'umi';

import { DEMO_CITY_OPTIONS, type DemoCityCode } from '../../modules/content/demoCities';
import { useDemoCityNavigation } from '../../modules/content/useDemoCityNavigation';

export const MobileUserHeader: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { city, selectCity } = useDemoCityNavigation();
  const [activeDropdownKey, setActiveDropdownKey] = useState<string | null>(null);

  const getPageTitle = (path: string) => {
    if (path === '/') return '首页';
    if (path.startsWith('/movies')) return '影片';
    if (path.startsWith('/cinemas')) return '影院';
    if (path.startsWith('/profile')) return '我的';
    return '妙语购票';
  };

  const isHome = location.pathname === '/';
  const canSwitchCity = isHome || location.pathname === '/cinemas';

  const handleCityChange = (cityCodes: DemoCityCode[]) => {
    const cityCode = cityCodes[0];
    if (!cityCode) {
      return;
    }
    selectCity(cityCode);
    setActiveDropdownKey(null);
  };

  return (
    <div className="mobile-user-header">
      <NavBar backArrow={!isHome} onBack={() => navigate(-1)} className="mobile-user-navbar">
        {canSwitchCity ? (
          <Dropdown
            activeKey={activeDropdownKey}
            className="mobile-city-dropdown"
            onChange={setActiveDropdownKey}
          >
            <Dropdown.Item key="city" title={city.name}>
              <Selector
                columns={2}
                onChange={handleCityChange}
                options={DEMO_CITY_OPTIONS.map((option) => ({
                  label: option.name,
                  value: option.code,
                }))}
                value={[city.code]}
              />
            </Dropdown.Item>
          </Dropdown>
        ) : (
          <span className="mobile-user-title">{getPageTitle(location.pathname)}</span>
        )}
      </NavBar>
    </div>
  );
};
