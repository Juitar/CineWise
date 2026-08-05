import { Alert } from 'antd';

import './index.css';

/**
 * 内容同步接口尚未由 D 提供，页面只展示真实就绪状态。
 * 接口确定后在 modules/admin-content 中接入，不能在页面内直接发送请求。
 */
export default function AdminContentPage() {
  return (
    <div className="admin-dashboard-container">
      <nav className="admin-breadcrumb" aria-label="面包屑">
        <span>管理端</span>
        <span aria-hidden="true">&gt;</span>
        <span className="current">内容同步</span>
      </nav>

      <h1 className="page-title">内容同步状态</h1>

      <section className="dashboard-panel" aria-labelledby="content-api-status-title">
        <h2 id="content-api-status-title" className="panel-header">
          内容管理接口待提供
        </h2>
        <Alert
          type="info"
          showIcon
          message="当前不展示模拟同步数据"
          description="D 提供内容源列表和同步接口后，本页再接入真实来源、许可、最近成功时间、有效期、数据量和同步状态。"
        />
        <dl className="admin-readiness-list">
          <div>
            <dt>列表接口</dt>
            <dd>GET /api/v1/admin/content/sources</dd>
          </div>
          <div>
            <dt>同步接口</dt>
            <dd>POST /api/v1/admin/content/sync</dd>
          </div>
          <div>
            <dt>接口负责人</dt>
            <dd>D（内容与数据模块）</dd>
          </div>
        </dl>
      </section>
    </div>
  );
}
