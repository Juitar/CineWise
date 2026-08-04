import React, { useState } from 'react';
import './index.css';

export default function AgentLogsPage() {
  const [selectedLog, setSelectedLog] = useState('AG202505220015');

  const logs = [
    { id: 'AG202505220015', time: '2025-05-22 14:35:21', duration: '8.42s', status: '成功' },
    { id: 'AG202505220014', time: '2025-05-22 14:28:07', duration: '3.21s', status: '处理中' },
    { id: 'AG202505220013', time: '2025-05-22 14:18:44', duration: '6.17s', status: '成功' },
    { id: 'AG202505220012', time: '2025-05-22 14:10:09', duration: '2.73s', status: '失败' },
    { id: 'AG202505220011', time: '2025-05-22 14:02:31', duration: '5.94s', status: '成功' },
  ];

  const renderStatus = (status: string) => {
    switch (status) {
      case '成功':
        return <span className="log-status-tag status-green">成功</span>;
      case '处理中':
        return <span className="log-status-tag status-blue">处理中</span>;
      case '失败':
        return <span className="log-status-tag status-red">失败</span>;
      default:
        return null;
    }
  };

  return (
    <div className="agent-logs-page">
      <nav className="admin-breadcrumb" aria-label="面包屑">
        <span>管理端</span>
        <span aria-hidden="true">&gt;</span>
        <span className="current">Agent轨迹</span>
      </nav>
      <h1 className="page-title">Agent轨迹</h1>

      <div className="agent-logs-layout">
        {/* Left Column: Log List */}
        <div className="logs-sidebar">
          <div className="logs-sidebar-title">最近运行记录</div>
          <div className="logs-list">
            {logs.map((log) => (
              <button
                type="button"
                key={log.id}
                className={`log-item ${selectedLog === log.id ? 'active' : ''}`}
                onClick={() => setSelectedLog(log.id)}
              >
                <div className="log-item-header">
                  <span className="log-id">{log.id}</span>
                  {renderStatus(log.status)}
                </div>
                <div className="log-item-meta">
                  <div className="meta-line">
                    <span className="meta-icon">🕒</span> {log.time}
                  </div>
                  <div className="meta-line">
                    <span className="meta-icon">⏱️</span> 总耗时 {log.duration}
                  </div>
                </div>
                <div className="log-arrow">&gt;</div>
              </button>
            ))}
          </div>
          <div className="logs-load-more">查看更多 v</div>
        </div>

        {/* Right Column: Log Details */}
        <div className="logs-content">
          {/* Run Details */}
          <div className="log-detail-card">
            <div className="card-title">运行详情</div>
            <div className="run-details-grid">
              <div className="run-detail-item">
                <div className="detail-label">Run ID</div>
                <div className="detail-value">
                  {selectedLog} <span className="copy-icon">📋</span>
                </div>
              </div>
              <div className="run-detail-item">
                <div className="detail-label">状态</div>
                <div className="detail-value">{renderStatus('成功')}</div>
              </div>
              <div className="run-detail-item">
                <div className="detail-label">
                  <span className="meta-icon">🕒</span> 开始时间
                </div>
                <div className="detail-value text-gray">2025-05-22 14:35:21</div>
              </div>
              <div className="run-detail-item">
                <div className="detail-label">
                  <span className="meta-icon">🕒</span> 结束时间
                </div>
                <div className="detail-value text-gray">2025-05-22 14:35:29</div>
              </div>
              <div className="run-detail-item">
                <div className="detail-label">
                  <span className="meta-icon">⏱️</span> 总耗时
                </div>
                <div className="detail-value font-medium">8.42s</div>
              </div>
            </div>
            <div className="run-meta-row">
              <div className="meta-pill">
                <span className="pill-icon">📦</span> Agent版本 v2.3.1
              </div>
              <div className="meta-pill">
                <span className="pill-icon">💻</span> 触发来源 Web
              </div>
              <div className="meta-pill">
                <span className="pill-icon">🔄</span> 运行类型 推荐购票流程
              </div>
            </div>
          </div>

          {/* Timeline */}
          <div className="log-detail-card">
            <div className="card-title">执行步骤时间线</div>
            <div className="timeline-container">
              <div className="timeline-track"></div>

              <div className="timeline-step">
                <div className="step-icon-wrapper">
                  <div className="step-icon">📤</div>
                  <div className="step-number">1</div>
                </div>
                <div className="step-title">接收请求</div>
                <div className="step-status status-text-green">✓ 成功</div>
                <div className="step-duration">512ms</div>
              </div>

              <div className="timeline-step">
                <div className="step-icon-wrapper">
                  <div className="step-icon">🧠</div>
                  <div className="step-number">2</div>
                </div>
                <div className="step-title">意图识别</div>
                <div className="step-status status-text-green">✓ 成功</div>
                <div className="step-duration">721ms</div>
              </div>

              <div className="timeline-step">
                <div className="step-icon-wrapper">
                  <div className="step-icon">🛡️</div>
                  <div className="step-number">3</div>
                </div>
                <div className="step-title">参数校验</div>
                <div className="step-status status-text-green">✓ 成功</div>
                <div className="step-duration">436ms</div>
              </div>

              <div className="timeline-step">
                <div className="step-icon-wrapper">
                  <div className="step-icon">🔍</div>
                  <div className="step-number">4</div>
                </div>
                <div className="step-title">查询影片与影院</div>
                <div className="step-status status-text-green">✓ 成功</div>
                <div className="step-duration">2.63s</div>
              </div>

              <div className="timeline-step">
                <div className="step-icon-wrapper">
                  <div className="step-icon">⭐</div>
                  <div className="step-number">5</div>
                </div>
                <div className="step-title">推荐方案生成</div>
                <div className="step-status status-text-green">✓ 成功</div>
                <div className="step-duration">2.74s</div>
              </div>

              <div className="timeline-step">
                <div className="step-icon-wrapper">
                  <div className="step-icon">🚀</div>
                  <div className="step-number">6</div>
                </div>
                <div className="step-title">返回结果</div>
                <div className="step-status status-text-green">✓ 成功</div>
                <div className="step-duration">1.38s</div>
              </div>
            </div>
          </div>

          <div className="logs-bottom-row">
            {/* Tool Calls */}
            <div className="log-detail-card tools-card">
              <div className="card-title">工具调用记录</div>
              <table className="logs-table">
                <thead>
                  <tr>
                    <th>工具名称</th>
                    <th>状态</th>
                    <th>开始时间</th>
                    <th>耗时</th>
                    <th>输出摘要</th>
                  </tr>
                </thead>
                <tbody>
                  <tr>
                    <td>search_movies</td>
                    <td>
                      <span className="status-text-green">✓ 成功</span>
                    </td>
                    <td>14:35:21.742</td>
                    <td>1.21s</td>
                    <td>返回 12 条影片</td>
                  </tr>
                  <tr>
                    <td>search_cinemas</td>
                    <td>
                      <span className="status-text-green">✓ 成功</span>
                    </td>
                    <td>14:35:22.972</td>
                    <td>1.08s</td>
                    <td>返回 8 家影院</td>
                  </tr>
                  <tr>
                    <td>search_showtimes</td>
                    <td>
                      <span className="status-text-green">✓ 成功</span>
                    </td>
                    <td>14:35:24.051</td>
                    <td>1.46s</td>
                    <td>返回 8 个场次</td>
                  </tr>
                  <tr>
                    <td>recommend_seats</td>
                    <td>
                      <span className="status-text-green">✓ 成功</span>
                    </td>
                    <td>14:35:25.511</td>
                    <td>1.19s</td>
                    <td>生成 3 个方案</td>
                  </tr>
                  <tr>
                    <td>build_response</td>
                    <td>
                      <span className="status-text-green">✓ 成功</span>
                    </td>
                    <td>14:35:26.704</td>
                    <td>0.84s</td>
                    <td>返回响应结构</td>
                  </tr>
                </tbody>
              </table>
            </div>

            {/* Summary */}
            <div className="log-detail-card summary-card">
              <div className="card-title">结果摘要</div>
              <div className="summary-content">
                <div className="trophy-icon">🏆</div>
                <div className="summary-text">
                  本次运行成功完成，共执行 6 个步骤，调用 5 个工具，生成 3 个推荐方案。
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  );
}
