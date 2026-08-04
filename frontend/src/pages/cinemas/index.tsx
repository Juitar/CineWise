import React from 'react';
import './index.css';

export default function CinemasPage() {
  return (
    <div className="cinemas-page-container">
      <div className="cinemas-page-main">
        <section className="cinemas-page-content">
          <h1 className="visually-hidden">影院列表</h1>
          <nav className="cinemas-breadcrumb" aria-label="面包屑">
            首页 / <span className="current">影院</span>
          </nav>

          <div className="cinemas-ai-recommendation">
            <div className="ai-rec-header">
              <span className="ai-rec-title">AI 推荐影院</span>
              <span className="ai-rec-refresh">↻ 换一批</span>
            </div>
            <div className="ai-rec-cards">
              {[
                {
                  id: 'xingguang',
                  name: '星光影城 (万象旗舰店)',
                  score: '8.6',
                  dist: '演示距离 1.2km',
                  tags: ['IMAX'],
                  posterClass: 'cinema-poster--navy',
                },
                {
                  id: 'cgv-xixi',
                  name: 'CGV 影城 (杭州西溪店)',
                  score: '8.3',
                  dist: '演示距离 2.8km',
                  tags: ['杜比全景声'],
                  posterClass: 'cinema-poster--red',
                },
                {
                  id: 'wanda-binjiang',
                  name: '万达影城 (滨江店)',
                  score: '8.4',
                  dist: '演示距离 2.1km',
                  tags: ['可停车'],
                  posterClass: 'cinema-poster--blue',
                },
              ].map((cinema) => (
                <div className="ai-rec-card" key={cinema.id}>
                  <div className={`ai-rec-poster ${cinema.posterClass}`} />
                  <div className="ai-rec-info">
                    <div className="ai-rec-name">{cinema.name}</div>
                    <div className="ai-rec-score-row">
                      <span className="ai-rec-score-icon">★</span>
                      <span className="ai-rec-score">{cinema.score}</span>
                    </div>
                    <div className="ai-rec-dist-row">
                      <span className="ai-rec-dist">距离 {cinema.dist}</span>
                      {cinema.tags.map((t) => (
                        <span key={t} className="ai-rec-tag">
                          {t}
                        </span>
                      ))}
                    </div>
                  </div>
                </div>
              ))}
            </div>
          </div>

          <div className="cinemas-filters-bar">
            <div className="filter-group left">
              <span className="filter-dropdown active">
                距离优先 <span className="arrow">v</span>
              </span>
              <span className="filter-dropdown">
                品牌 <span className="arrow">v</span>
              </span>
              <span className="filter-dropdown">
                影厅类型 <span className="arrow">v</span>
              </span>
              <span className="filter-dropdown">
                特色服务 <span className="arrow">v</span>
              </span>
            </div>
            <div className="filter-group right">
              <span className="filter-map-mode">
                <span className="map-icon">📍</span> 地图模式
              </span>
            </div>
          </div>

          <div className="cinemas-list-view">
            {[
              {
                id: 'xingguang-wanxiang',
                name: '星光影城 (杭州万象城店)',
                score: '8.6',
                address: '杭州市上城区富春路701号万象城4楼',
                tags: ['IMAX', '杜比全景声', '可停车', '儿童友好'],
                dist: '距离需授权',
                sessions: 18,
                price: 39,
                posterClass: 'cinema-poster--navy',
              },
              {
                id: 'wanda-binjiang',
                name: '万达影城 (滨江店)',
                score: '8.4',
                address: '杭州市滨江区江南大道228号星光大道购物中心5楼',
                tags: ['杜比全景声', '可停车', '会员优惠'],
                dist: '距离需授权',
                sessions: 16,
                price: 35,
                posterClass: 'cinema-poster--blue',
              },
              {
                id: 'cgv-xixi',
                name: 'CGV 影城 (杭州西溪店)',
                score: '8.3',
                address: '杭州市西湖区文二西路808号西溪印象城3楼',
                tags: ['IMAX', '可停车', '情侣座椅'],
                dist: '距离需授权',
                sessions: 14,
                price: 32,
                posterClass: 'cinema-poster--red',
              },
              {
                id: 'jinyi-xiaoshan',
                name: '金逸影城 (余地广场店)',
                score: '8.2',
                address: '杭州市萧山区市心北路108号金地广场4楼',
                tags: ['巨幕厅', '会员优惠', '可停车'],
                dist: '距离需授权',
                sessions: 12,
                price: 30,
                posterClass: 'cinema-poster--black',
              },
              {
                id: 'lumiere-dayuecheng',
                name: '卢米埃影城 (大悦城店)',
                score: '8.1',
                address: '杭州市拱墅区隐秀路1号大悦城购物中心6楼',
                tags: ['IMAX', '杜比全景声', '艺术影厅'],
                dist: '距离需授权',
                sessions: 10,
                price: 28,
                posterClass: 'cinema-poster--brown',
              },
            ].map((cinema) => (
              <div className="cinema-list-item" key={cinema.id}>
                <div className={`cinema-list-logo ${cinema.posterClass}`} />
                <div className="cinema-list-center">
                  <div className="cinema-list-title-row">
                    <span className="cinema-list-title">{cinema.name}</span>
                    <span className="cinema-list-score">{cinema.score}</span>
                    <span className="cinema-list-crown">👑</span>
                  </div>
                  <div className="cinema-list-address">
                    <span className="address-icon">📍</span>
                    {cinema.address}
                  </div>
                  <div className="cinema-list-tags">
                    {cinema.tags.map((t) => (
                      <span key={t} className="cinema-list-tag">
                        {t}
                      </span>
                    ))}
                  </div>
                </div>
                <div className="cinema-list-right">
                  <div className="cinema-right-row">距离 {cinema.dist}</div>
                  <div className="cinema-right-row">
                    今日场次 <span className="highlight-text">{cinema.sessions}</span> 场
                  </div>
                  <div className="cinema-right-row">
                    起价 <span className="price-text">¥{cinema.price}</span>
                  </div>
                  <span className="cinema-list-arrow">&gt;</span>
                </div>
              </div>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
}
