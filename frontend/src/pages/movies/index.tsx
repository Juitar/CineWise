import React from 'react';
import './index.css';

export default function MoviesPage() {
  return (
    <div className="movies-page-container">
      <div className="movies-page-main">
        <section className="movies-page-content">
          <h1 className="visually-hidden">影片列表</h1>
          <nav className="movies-breadcrumb" aria-label="面包屑">
            首页 / <span className="current">电影</span>
          </nav>

          <div className="movies-nav-tabs">
            <div className="nav-tab active">正在热映</div>
            <div className="nav-tab">即将上映</div>
            <div className="nav-tab">口碑榜</div>
            <div className="nav-tab">想看榜</div>
          </div>

          <div className="movies-filters-bar">
            <div className="filter-group left">
              <span className="filter-pill active">全部</span>
              <span className="filter-pill">动作</span>
              <span className="filter-pill">喜剧</span>
              <span className="filter-pill">爱情</span>
              <span className="filter-pill">科幻</span>
              <span className="filter-pill">动画</span>
              <span className="filter-pill">悬疑</span>
              <span className="filter-pill">剧情</span>
            </div>
            <div className="filter-group right">
              <span className="filter-dropdown">
                全部地区 <span className="arrow">v</span>
              </span>
              <span className="filter-dropdown">
                全部年份 <span className="arrow">v</span>
              </span>
              <span className="filter-dropdown">
                综合排序 <span className="arrow">v</span>
              </span>
            </div>
          </div>

          <div className="movies-grid-view">
            {[
              {
                id: 'moments',
                name: '云边有个小卖部',
                score: '8.5',
                tags: ['剧情', '治愈'],
                info: '131分钟 / 正在热映',
                posterClass: 'movie-grid-poster--peach',
              },
              {
                id: 'kowloon',
                name: '九龙城寨之围城',
                score: '9.2',
                tags: ['动作', '犯罪'],
                info: '126分钟 / 正在热映',
                posterClass: 'movie-grid-poster--navy',
              },
              {
                id: 'spy-family',
                name: '间谍过家家 代号：白',
                score: '8.8',
                tags: ['动画', '喜剧'],
                info: '110分钟 / 正在热映',
                posterClass: 'movie-grid-poster--pink',
              },
              {
                id: 'apes',
                name: '猩球崛起：新世界',
                score: '8.6',
                tags: ['科幻', '冒险'],
                info: '132分钟 / 正在热映',
                posterClass: 'movie-grid-poster--blue-gray',
              },
              {
                id: 'last-frenzy',
                name: '末路狂花钱',
                score: '8.1',
                tags: ['喜剧', '剧情'],
                info: '116分钟 / 正在热映',
                posterClass: 'movie-grid-poster--sky',
              },
              {
                id: 'silent-kill',
                name: '默杀',
                score: '8.0',
                tags: ['悬疑', '犯罪'],
                info: '105分钟 / 正在热映',
                posterClass: 'movie-grid-poster--green',
              },
              {
                id: 'changan',
                name: '长安三万里',
                score: '9.0',
                tags: ['动画', '历史'],
                info: '168分钟 / 正在热映',
                posterClass: 'movie-grid-poster--orange',
              },
              {
                id: 'doraemon',
                name: '哆啦A梦：大雄的地球交响乐',
                score: '8.4',
                tags: ['动画', '冒险'],
                info: '115分钟 / 正在热映',
                posterClass: 'movie-grid-poster--blue',
              },
              {
                id: 'customs-frontline',
                name: '海关战线',
                score: '7.9',
                tags: ['动作', '剧情'],
                info: '123分钟 / 正在热映',
                posterClass: 'movie-grid-poster--slate',
              },
              {
                id: 'crisis-negotiators',
                name: '谈判专家',
                score: '8.2',
                tags: ['剧情', '犯罪'],
                info: '120分钟 / 正在热映',
                posterClass: 'movie-grid-poster--deep-blue',
              },
            ].map((movie) => (
              <div className="movie-grid-card" key={movie.id}>
                <div className={`movie-grid-poster ${movie.posterClass}`} />
                <div className="movie-grid-info">
                  <div className="movie-grid-name">{movie.name}</div>
                  <div className="movie-grid-score-row">
                    <span className="movie-grid-score-icon">★</span>
                    <span className="movie-grid-score">{movie.score}</span>
                  </div>
                  <div className="movie-grid-tags">
                    {movie.tags.map((t) => (
                      <span key={t} className="movie-tag">
                        {t}
                      </span>
                    ))}
                  </div>
                  <div className="movie-grid-desc">{movie.info}</div>
                </div>
              </div>
            ))}
          </div>
        </section>
      </div>
    </div>
  );
}
