import { Link } from 'umi';

export function DesktopUserHeader() {
  return (
    <header className="user-layout-header">
      <div className="user-layout-header-content">
        <Link className="user-layout-brand" to="/">
          妙语购票
        </Link>
      </div>
    </header>
  );
}
