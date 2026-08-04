import React from 'react';
import './index.css';

export default function ProfilePage() {
  return (
    <div className="profile-page-container">
      <div className="profile-page-main">
        <section className="profile-page-content">
          <nav className="profile-breadcrumb" aria-label="面包屑">
            首页 / <span className="current">我的</span>
          </nav>

          {/* User Info Header */}
          <div className="profile-header-card">
            <div className="profile-header-left">
              <div className="profile-avatar">
                {/* 占位头像 */}
                <div className="avatar-placeholder"></div>
              </div>
              <div className="profile-info-text">
                <div className="profile-name-row">
                  <h1 className="profile-name">妙语用户</h1>
                  <span className="profile-edit-icon">✎</span>
                </div>
                <div className="profile-bio">热爱电影，享受每一次光影之旅</div>
                <div className="profile-ai-tags">
                  <span className="ai-tag-label">AI 观影偏好</span>
                  <span className="ai-tag">科幻</span>
                  <span className="ai-tag">IMAX</span>
                  <span className="ai-tag">安静环境</span>
                </div>
              </div>
            </div>
            <div className="profile-header-illustration">
              <div className="illustration-placeholder"></div>
            </div>
          </div>

          {/* Orders Section */}
          <div className="profile-section">
            <h2 className="profile-section-title">我的订单</h2>
            <div className="profile-order-tabs">
              <div className="order-tab active">
                待付款 <span className="tab-badge">1</span>
              </div>
              <div className="order-tab">
                待观影 <span className="tab-badge">1</span>
              </div>
              <div className="order-tab">
                已完成 <span className="tab-badge">8</span>
              </div>
            </div>

            <div className="profile-order-list">
              <div className="order-card">
                <div className="order-poster"></div>
                <div className="order-details">
                  <div className="order-title-row">
                    <span className="order-title">
                      星际穿越 <span className="order-tag">IMAX 3D</span>
                    </span>
                    <span className="order-status warning">待付款</span>
                  </div>
                  <div className="order-cinema">杭州UME影城 (西湖店)</div>
                  <div className="order-time">2024-06-22 (周六) 19:10</div>
                  <div className="order-seats">4号厅 | 5排8座 5排9座</div>
                </div>
                <div className="order-actions">
                  <div className="order-price">¥156</div>
                  <div className="order-countdown">请在 14:53 内完成支付</div>
                  <button type="button" className="pay-btn">
                    去支付
                  </button>
                </div>
              </div>
            </div>

            <div className="profile-section-footer">查看全部订单 &gt;</div>
          </div>

          {/* Viewing History Section */}
          <div className="profile-section">
            <div className="section-header-row">
              <h2 className="profile-section-title">我的观影记录</h2>
              <span className="section-more-link">全部记录 &gt;</span>
            </div>
            <div className="history-movies-grid">
              {[
                {
                  id: 'oppenheimer',
                  name: '奥本海默',
                  score: '9.6',
                  posterClass: 'history-poster--brown',
                },
                {
                  id: 'interstellar',
                  name: '星际穿越',
                  score: '9.4',
                  posterClass: 'history-poster--navy',
                },
                {
                  id: 'dune-2',
                  name: '沙丘2',
                  score: '8.9',
                  posterClass: 'history-poster--orange',
                },
                {
                  id: 'wandering-earth-2',
                  name: '流浪地球2',
                  score: '8.7',
                  posterClass: 'history-poster--blue-gray',
                },
                {
                  id: 'avatar-2',
                  name: '阿凡达：水之道',
                  score: '8.5',
                  posterClass: 'history-poster--blue',
                },
                {
                  id: 'inception',
                  name: '盗梦空间',
                  score: '9.3',
                  posterClass: 'history-poster--deep-blue',
                },
              ].map((movie) => (
                <div className="history-movie-card" key={movie.id}>
                  <div className={`history-movie-poster ${movie.posterClass}`} />
                  <div className="history-movie-name">{movie.name}</div>
                  <div className="history-movie-score">{movie.score}</div>
                </div>
              ))}
            </div>
          </div>
        </section>

        {/* Right Sidebar */}
        <aside className="profile-page-sidebar">
          {/* AI Profile Card */}
          <div className="profile-sidebar-card ai-profile">
            <div className="sidebar-card-header">
              <span className="sidebar-card-icon">🤖</span>
              <span className="sidebar-card-title">AI 观影画像</span>
            </div>

            <div className="ai-profile-list">
              <div className="ai-profile-item">
                <div className="item-left">
                  <span className="item-icon">🎯</span>
                  <span className="item-label">偏好类型</span>
                </div>
                <div className="item-value">科幻 · 冒险 · 悬疑</div>
              </div>
              <div className="ai-profile-item">
                <div className="item-left">
                  <span className="item-icon">🕒</span>
                  <span className="item-label">常去时间</span>
                </div>
                <div className="item-value">周末晚上、节假日</div>
              </div>
              <div className="ai-profile-item">
                <div className="item-left">
                  <span className="item-icon">🛋️</span>
                  <span className="item-label">偏好环境</span>
                </div>
                <div className="item-value">安静环境 · IMAX厅</div>
              </div>
              <div className="ai-profile-item">
                <div className="item-left">
                  <span className="item-icon">💺</span>
                  <span className="item-label">偏好座位</span>
                </div>
                <div className="item-value">中间偏后 · 靠中间</div>
              </div>
            </div>

            <div className="ai-recommendation-box">
              <div className="ai-recommendation-title">AI 推荐语</div>
              <div className="ai-recommendation-text">
                你偏爱科幻与烧脑剧情，喜欢沉浸式观影体验。
                <br />
                本周为你推荐《沙丘2》《星际穿越》。
              </div>
              <div className="ai-bot-illustration"></div>
            </div>
          </div>

          {/* Settings & Help Card */}
          <div className="profile-sidebar-card">
            <div className="sidebar-card-header">
              <span className="sidebar-card-title">设置与帮助</span>
            </div>
            <div className="settings-list">
              <div className="settings-item">
                <div className="settings-item-left">
                  <span className="settings-icon">⚙️</span>
                  <span>账号设置</span>
                </div>
                <span className="settings-arrow">&gt;</span>
              </div>
              <div className="settings-item">
                <div className="settings-item-left">
                  <span className="settings-icon">🔔</span>
                  <span>通知设置</span>
                </div>
                <span className="settings-arrow">&gt;</span>
              </div>
              <div className="settings-item">
                <div className="settings-item-left">
                  <span className="settings-icon">💬</span>
                  <span>意见反馈</span>
                </div>
                <span className="settings-arrow">&gt;</span>
              </div>
              <div className="settings-item">
                <div className="settings-item-left">
                  <span className="settings-icon">🎧</span>
                  <span>联系客服</span>
                </div>
                <span className="settings-arrow">&gt;</span>
              </div>
              <div className="settings-item">
                <div className="settings-item-left">
                  <span className="settings-icon">❓</span>
                  <span>帮助中心</span>
                </div>
                <span className="settings-arrow">&gt;</span>
              </div>
            </div>
          </div>
        </aside>
      </div>
    </div>
  );
}
