import React, { useState } from 'react';
import { Link } from 'umi';
import { useMediaQuery } from '../../shared/hooks/useMediaQuery';
import { AgentCard } from './AgentCard';
import { PlanRecommendation } from './PlanRecommendation';
import './index.css';
import { RobotIcon } from '../../shared/components/icons/layout-icons';

export default function HomePage() {
  const isMobile = useMediaQuery('(max-width: 1023px)');
  const [showPlans, setShowPlans] = useState(false);

  return (
    <div className="home-page-container">
      <h1 className="visually-hidden">妙语购票</h1>
      {/* 移动端 Agent 顶部横幅（仅在移动端首页出现） */}
      {isMobile && (
        <div className="home-mobile-agent-banner">
          <div className="home-mobile-agent-banner-inner">
            <div className="home-mobile-agent-banner-header">
              <div className="home-mobile-agent-icon-wrap">
                <RobotIcon size={18} />
              </div>
              <div className="home-mobile-agent-text">
                <div className="home-mobile-agent-title">妙语 AI 助理</div>
                <div className="home-mobile-agent-subtitle">告诉我你想看什么</div>
              </div>
            </div>

            <div className="home-mobile-agent-input-container">
              <input
                className="home-mobile-agent-input"
                placeholder="例如：周末有什么好看的动作片？"
              />
              <button type="button" className="home-mobile-agent-send-btn">
                发送
              </button>
            </div>
          </div>
        </div>
      )}

      <div className="home-page-main">
        {/* 左侧主体内容（影片列表、影院列表） */}
        <section className="home-page-content">
          <div className="home-main-content">
            {showPlans ? (
              <PlanRecommendation onBack={() => setShowPlans(false)} />
            ) : (
              <>
                {/* 筛选条件栏 */}
                <div className="home-filter-bar">
                  <div className="filter-chip">
                    📅 今天 <span className="arrow">v</span>
                  </div>
                  <div className="filter-chip">
                    🕒 19:00以后 <span className="arrow">v</span>
                  </div>
                  <div className="filter-chip">
                    👥 2人 <span className="arrow">v</span>
                  </div>
                  <div className="filter-chip">
                    💰 ≤ ¥150 <span className="arrow">v</span>
                  </div>
                  <div className="filter-chip">
                    📍 离我最近 <span className="arrow">v</span>
                  </div>
                  <div className="filter-chip">
                    ··· 类型 <span className="arrow">v</span>
                  </div>
                  <div className="filter-chip more-filter">
                    更多筛选 <span>▽</span>
                  </div>
                </div>

                {/* 正在热映 */}
                <div className="home-section">
                  <div className="section-header">
                    <h2 className="home-section-title">正在热映</h2>
                    <Link className="section-link" to="/movies">
                      全部 28 部
                    </Link>
                  </div>
                  <div className="movies-list">
                    {[
                      {
                        id: 'kowloon',
                        name: '九龙城寨之围城',
                        score: '9.2',
                        posterClass: 'poster--navy',
                      },
                      {
                        id: 'apes',
                        name: '猩球崛起：新世界',
                        score: '8.6',
                        posterClass: 'poster--blue-gray',
                      },
                      {
                        id: 'spy-family',
                        name: '间谍过家家 代号：白',
                        score: '8.8',
                        posterClass: 'poster--pink',
                      },
                      {
                        id: 'moments',
                        name: '云边有个小卖部',
                        score: '8.5',
                        posterClass: 'poster--peach',
                      },
                      {
                        id: 'last-frenzy',
                        name: '末路狂花钱',
                        score: '8.1',
                        posterClass: 'poster--sky',
                      },
                    ].map((movie) => (
                      <div className="movie-card" key={movie.id}>
                        <div className={`movie-poster-large ${movie.posterClass}`} />
                        <div className="movie-card-info">
                          <div className="movie-card-name">{movie.name}</div>
                          <div className="movie-card-score">{movie.score}</div>
                          <button type="button" className="buy-ticket-btn">
                            购票
                          </button>
                        </div>
                      </div>
                    ))}
                    <div className="scroll-arrow-right">&gt;</div>
                  </div>
                </div>

                {/* 附近影院 */}
                <div className="home-section">
                  <div className="section-header">
                    <h2 className="home-section-title">
                      附近影院 <span className="section-subtitle">基于你的位置推荐</span>
                    </h2>
                    <Link className="section-link" to="/cinemas">
                      查看更多
                    </Link>
                  </div>
                  <div className="cinemas-list">
                    {[
                      {
                        id: 'ume-west-lake',
                        name: 'UME影城 (杭州西湖店)',
                        dist: '演示距离 1.2km',
                        tags: ['IMAX', '杜比影院', '停车优惠'],
                        price: '39',
                        times: ['19:20', '20:40', '22:10'],
                      },
                      {
                        id: 'xingju-yintai',
                        name: '星聚影城 (城西银泰店)',
                        dist: '演示距离 2.1km',
                        tags: ['巨幕厅', 'VIP厅', '可停车'],
                        price: '38',
                        times: ['18:50', '20:30', '22:15'],
                      },
                      {
                        id: 'yunbian-wanda',
                        name: '云边影城 (杭州拱墅万达店)',
                        dist: '演示距离 2.8km',
                        tags: ['IMAX', 'RealD 3D', '美食套餐'],
                        price: '42',
                        times: ['19:10', '21:05', '23:00'],
                      },
                    ].map((cinema) => (
                      <div className="cinema-card" key={cinema.id}>
                        <div className="cinema-header">
                          <div className="cinema-title-wrap">
                            <div className="cinema-icon-placeholder"></div>
                            <div className="cinema-name">{cinema.name}</div>
                          </div>
                          <div className="cinema-dist">{cinema.dist}</div>
                        </div>
                        <div className="cinema-tags">
                          {cinema.tags.map((t) => (
                            <span key={t} className="cinema-tag">
                              {t}
                            </span>
                          ))}
                        </div>
                        <div className="cinema-price-row">
                          <span className="cinema-price">
                            ¥{cinema.price} <span className="price-suffix">起</span>
                          </span>
                        </div>
                        <div className="cinema-times">
                          {cinema.times.map((t) => (
                            <span key={t} className="cinema-time">
                              {t}
                            </span>
                          ))}
                        </div>
                      </div>
                    ))}
                  </div>
                </div>

                {/* 个性化服务横幅 */}
                <div className="personalized-banner">
                  <div className="personalized-icon-wrap">
                    <RobotIcon size={24} />
                  </div>
                  <div className="personalized-content">
                    <div className="personalized-title">想要更精准的推荐？</div>
                    <div className="personalized-desc">
                      开启后，AI将结合路线、出发时间与附近美食等信息，为你提供个性化观影方案
                    </div>
                  </div>
                  <button type="button" className="personalized-btn">
                    开启个性化服务
                  </button>
                </div>
              </>
            )}
          </div>
        </section>

        {/* 右侧 Agent 面板（仅 PC 端） */}
        {!isMobile && (
          <aside className="home-page-agent-sidebar">
            <AgentCard onViewPlan={() => setShowPlans(true)} />
          </aside>
        )}
      </div>
    </div>
  );
}
