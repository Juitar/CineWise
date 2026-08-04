import React from 'react';
import './index.css';

export default function AdminDashboardPage() {
  return (
    <div className="admin-dashboard-container">
      <nav className="admin-breadcrumb" aria-label="面包屑">
        <span>管理端</span>
        <span aria-hidden="true">&gt;</span>
        <span className="current">工作台</span>
      </nav>

      {/* Summary Cards */}
      <div className="dashboard-summary-grid">
        <div className="summary-card">
          <div className="summary-card-header">
            <div className="summary-icon bg-purple">
              <span className="icon-text">🗄️</span>
            </div>
            <div className="summary-info">
              <div className="summary-title">内容同步状态</div>
              <div className="summary-status status-green">
                <span className="dot"></span> 正常
              </div>
            </div>
          </div>
          <div className="summary-footer">最后同步：今天 10:18</div>
        </div>

        <div className="summary-card">
          <div className="summary-card-header">
            <div className="summary-icon bg-blue">
              <span className="icon-text">📄</span>
            </div>
            <div className="summary-info">
              <div className="summary-title">订单状态</div>
              <div className="summary-count">
                28 <span className="count-badge">待处理</span>
              </div>
            </div>
          </div>
          <div className="summary-footer">今日新增：16</div>
        </div>

        <div className="summary-card">
          <div className="summary-card-header">
            <div className="summary-icon bg-indigo">
              <span className="icon-text">🤖</span>
            </div>
            <div className="summary-info">
              <div className="summary-title">Agent运行状态</div>
              <div className="summary-status status-green">
                <span className="dot"></span> 运行中
              </div>
            </div>
          </div>
          <div className="summary-footer">在线 Agent：3 / 3</div>
        </div>

        <div className="summary-card">
          <div className="summary-card-header">
            <div className="summary-icon bg-cyan">
              <span className="icon-text">🛡️</span>
            </div>
            <div className="summary-info">
              <div className="summary-title">系统状态</div>
              <div className="summary-status status-green">
                <span className="dot"></span> 正常
              </div>
            </div>
          </div>
          <div className="summary-footer">服务运行良好</div>
        </div>
      </div>

      {/* Middle Section (2 Columns) */}
      <div className="dashboard-middle-row">
        {/* Data Sync Status */}
        <div className="dashboard-panel panel-left">
          <div className="panel-header">
            <span className="panel-icon">🔄</span> 数据同步状态
          </div>
          <table className="admin-table">
            <thead>
              <tr>
                <th>数据类型</th>
                <th>状态</th>
                <th>最后同步时间</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>
                  <span className="table-icon-text">🎥</span> 影片数据
                </td>
                <td>
                  <span className="status-text status-green">
                    <span className="dot"></span> 正常
                  </span>
                </td>
                <td>今天 10:18:22</td>
              </tr>
              <tr>
                <td>
                  <span className="table-icon-text">🏛️</span> 影院数据
                </td>
                <td>
                  <span className="status-text status-green">
                    <span className="dot"></span> 正常
                  </span>
                </td>
                <td>今天 10:17:45</td>
              </tr>
            </tbody>
          </table>
        </div>

        {/* Recent Orders */}
        <div className="dashboard-panel panel-right">
          <div className="panel-header">
            <span className="panel-icon">📋</span> 最近订单
          </div>
          <table className="admin-table">
            <thead>
              <tr>
                <th>订单号</th>
                <th>影片</th>
                <th>影院</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>MO202505220001</td>
                <td>流浪地球2</td>
                <td>杭州UME影城 (西湖店)</td>
                <td>
                  <span className="order-tag tag-purple">待支付</span>
                </td>
              </tr>
              <tr>
                <td>MO202505220002</td>
                <td>哈尔的移动城堡</td>
                <td>万达影城 (武林广场店)</td>
                <td>
                  <span className="order-tag tag-green">已完成</span>
                </td>
              </tr>
              <tr>
                <td>MO202505220003</td>
                <td>复仇者联盟4</td>
                <td>CGV影城 (滨江店)</td>
                <td>
                  <span className="order-tag tag-blue">已取消</span>
                </td>
              </tr>
              <tr>
                <td>MO202505220004</td>
                <td>灌篮高手</td>
                <td>SFC上影影城 (庆春店)</td>
                <td>
                  <span className="order-tag tag-orange">处理中</span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      {/* Bottom Section */}
      <div className="dashboard-panel">
        <div className="panel-header">
          <span className="panel-icon">🤖</span> 最近Agent任务
        </div>
        <table className="admin-table">
          <thead>
            <tr>
              <th>运行ID</th>
              <th>意图摘要</th>
              <th>状态</th>
              <th>开始时间</th>
            </tr>
          </thead>
          <tbody>
            <tr>
              <td>AG202505220015</td>
              <td>查询杭州附近IMAX场次，周末晚上</td>
              <td>
                <span className="task-tag tag-green-outline">成功</span>
              </td>
              <td>今天 10:20:11</td>
            </tr>
            <tr>
              <td>AG202505220014</td>
              <td>推荐适合情侣看的爱情电影</td>
              <td>
                <span className="task-tag tag-green-outline">成功</span>
              </td>
              <td>今天 10:18:09</td>
            </tr>
            <tr>
              <td>AG202505220013</td>
              <td>查找带儿童观影优惠的影院</td>
              <td>
                <span className="task-tag tag-blue-outline">已完成</span>
              </td>
              <td>今天 10:15:32</td>
            </tr>
            <tr>
              <td>AG202505220012</td>
              <td>比较两部科幻电影的评分和时长</td>
              <td>
                <span className="task-tag tag-orange-outline">处理中</span>
              </td>
              <td>今天 10:12:07</td>
            </tr>
          </tbody>
        </table>
      </div>
    </div>
  );
}
