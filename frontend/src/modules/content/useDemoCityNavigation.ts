import { useLocation, useNavigate } from 'umi';

import { buildDemoCityDestination, resolveDemoCity, type DemoCityCode } from './demoCities';

/**
 * 读取 URL 中的受控演示城市，并在切换时保持内容浏览与交易业务上下文隔离。
 */
export function useDemoCityNavigation() {
  const location = useLocation();
  const navigate = useNavigate();
  const city = resolveDemoCity(new URLSearchParams(location.search).get('location'));

  const selectCity = (cityCode: DemoCityCode) => {
    navigate(buildDemoCityDestination(location.pathname, location.search, cityCode));
  };

  return { city, selectCity };
}
