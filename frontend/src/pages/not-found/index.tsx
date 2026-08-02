import { Link } from 'umi';

import './index.css';

export default function NotFoundPage() {
  return (
    <section className="not-found-page">
      <p className="not-found-page-code">404</p>
      <h1 className="not-found-page-title">页面不存在</h1>
      <p className="not-found-page-description">当前地址没有对应页面，可能已经失效。</p>
      <Link className="not-found-page-link" to="/">
        返回首页
      </Link>
    </section>
  );
}
