import React from 'react';
import './loading.css';

export default function PageLoading() {
  return (
    <div className="global-loading-container">
      <div className="skeleton-header">
        <div className="skeleton-title"></div>
        <div className="skeleton-subtitle"></div>
      </div>

      <div className="skeleton-tabs">
        <div className="skeleton-tab"></div>
        <div className="skeleton-tab"></div>
        <div className="skeleton-tab"></div>
        <div className="skeleton-tab"></div>
      </div>

      <div className="skeleton-grid">
        {[1, 2, 3, 4, 5, 6, 7, 8].map((i) => (
          <div className="skeleton-card" key={i}>
            <div className="skeleton-poster"></div>
            <div className="skeleton-text-line"></div>
            <div className="skeleton-text-short"></div>
          </div>
        ))}
      </div>
    </div>
  );
}
