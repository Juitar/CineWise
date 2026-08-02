import { NavBar } from 'antd-mobile';
import { Link } from 'umi';

export function MobileUserHeader() {
  return (
    <header className="user-layout-mobile-header">
      <NavBar backIcon={false}>
        <Link className="user-layout-brand" to="/">
          妙语购票
        </Link>
      </NavBar>
    </header>
  );
}
