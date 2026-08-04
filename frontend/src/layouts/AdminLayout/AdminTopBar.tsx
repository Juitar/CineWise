import React from 'react';
import './index.css';

export function AdminTopBar() {
  return (
    <header className="admin-top-bar">
      <div className="admin-top-bar-left"></div>
      <div className="admin-top-bar-right">
        <div className="admin-notification">
          <span className="bell-icon">🔔</span>
          <span className="notification-badge">3</span>
        </div>
        <div className="admin-user-profile">
          <div className="admin-avatar" aria-hidden="true">
            管
          </div>
          <span className="admin-name">
            管理员 <span className="arrow-down">v</span>
          </span>
        </div>
      </div>
    </header>
  );
}
