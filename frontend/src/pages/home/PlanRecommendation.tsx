import React from 'react';
import './PlanRecommendation.css';

interface PlanRecommendationProps {
  onBack: () => void;
}

export const PlanRecommendation: React.FC<PlanRecommendationProps> = ({ onBack }) => {
  return (
    <div className="plan-recommendation-container">
      <div className="plan-header-row">
        <button type="button" className="back-to-home-btn" onClick={onBack}>
          返回首页
        </button>
        <h2 className="plan-main-title">为你生成 3 个观影方案</h2>
        <div className="plan-subtitle">静态演示数据：路线、距离和美食信息尚未实时校验</div>
        <button type="button" className="refresh-plan-btn">
          <span className="refresh-icon">🔄</span> 换一批
        </button>
      </div>

      <div className="plan-cards-list">
        {/* Plan A */}
        <div className="plan-card-full active-plan">
          <div className="plan-badge-col">
            <div className="plan-badge-title">方案 A</div>
            <div className="plan-badge-desc">首选推荐</div>
            <div className="plan-radio-circle checked">✓</div>
          </div>

          <div className="plan-movie-col">
            <div className="plan-movie-poster plan-movie-poster--blue-gray" />
            <div className="plan-movie-info">
              <div className="plan-movie-name">
                云边有个小卖部 <span className="score">8.5</span>
              </div>
              <div className="plan-movie-tags">温情 / 剧情 | 口碑佳</div>
              <div className="plan-cinema-name">
                <span className="icon">🏛️</span> 杭州UME影城 (西湖店)
              </div>
              <div className="plan-cinema-tags">
                <span className="ctag">激光厅</span>
                <span className="ctag">店内餐饮</span>
              </div>
              <div className="plan-showtime">今天 19:40 (国语 2D)</div>
              <div className="plan-seat-info">6号厅 | 184个座位可选</div>
            </div>
          </div>

          <div className="plan-travel-col">
            <div className="travel-row">
              <span className="travel-label">距离</span>
              <span className="travel-val">
                1.2 km | 18 分钟 <span className="status-badge green">较近</span>
              </span>
            </div>
            <div className="travel-row">
              <span className="travel-label">预计出发</span>
              <span className="travel-val bold">19:00</span>
              <div className="travel-sub">建议提前 40 分钟到达</div>
            </div>
            <div className="travel-row">
              <span className="travel-label">路线用时</span>
              <span className="travel-val bold">18 分钟</span>
              <div className="travel-sub">地铁 10 分钟 + 步行 8 分钟</div>
            </div>
          </div>

          <div className="plan-food-col">
            <div className="food-title">附近美食推荐</div>
            <div className="food-item">
              <div className="food-img food-img--gray" />
              <div className="food-info">
                <div className="food-name">外婆家 (西湖店)</div>
                <div className="food-desc">杭帮菜 · 600m · 步行7分钟</div>
              </div>
            </div>
            <div className="food-item">
              <div className="food-img food-img--cream" />
              <div className="food-info">
                <div className="food-name">喜茶 (湖滨银泰店)</div>
                <div className="food-desc">奶茶 · 450m · 步行5分钟</div>
              </div>
            </div>
            <div className="food-tags">
              <span className="ftag">地铁直达</span>
              <span className="ftag">步行友好</span>
              <span className="ftag">附近奶茶</span>
              <span className="ftag highlight">散场后可就餐</span>
            </div>
          </div>
        </div>

        {/* Plan B */}
        <div className="plan-card-full">
          <div className="plan-badge-col">
            <div className="plan-badge-title">方案 B</div>
            <div className="plan-badge-desc">性价比高</div>
            <div className="plan-radio-circle"></div>
          </div>

          <div className="plan-movie-col">
            <div className="plan-movie-poster plan-movie-poster--sky" />
            <div className="plan-movie-info">
              <div className="plan-movie-name">
                末路狂花钱 <span className="score">8.1</span>
              </div>
              <div className="plan-movie-tags">喜剧 | 合家欢</div>
              <div className="plan-cinema-name">
                <span className="icon">🏛️</span> 星聚影城 (城西银泰店)
              </div>
              <div className="plan-cinema-tags">
                <span className="ctag">杜比全景声</span>
              </div>
              <div className="plan-showtime">今天 20:10 (国语 2D)</div>
              <div className="plan-seat-info">5号厅 | 132个座位可选</div>
            </div>
          </div>

          <div className="plan-travel-col">
            <div className="travel-row">
              <span className="travel-label">距离</span>
              <span className="travel-val">
                2.1 km | 26 分钟 <span className="status-badge orange">适中</span>
              </span>
            </div>
            <div className="travel-row">
              <span className="travel-label">预计出发</span>
              <span className="travel-val bold">19:10</span>
              <div className="travel-sub">建议提前 60 分钟到达</div>
            </div>
            <div className="travel-row">
              <span className="travel-label">路线用时</span>
              <span className="travel-val bold">26 分钟</span>
              <div className="travel-sub">地铁 18 分钟 + 步行 8 分钟</div>
            </div>
          </div>

          <div className="plan-food-col">
            <div className="food-title">附近美食推荐</div>
            <div className="food-item">
              <div className="food-img food-img--tan" />
              <div className="food-info">
                <div className="food-name">弄堂里 (城西银泰店)</div>
                <div className="food-desc">本帮菜 · 700m · 步行5分钟</div>
              </div>
            </div>
            <div className="food-tags">
              <span className="ftag">地铁直达</span>
              <span className="ftag highlight">性价比高</span>
              <span className="ftag highlight">散场后可就餐</span>
            </div>
          </div>
        </div>
      </div>

      <div className="plan-bottom-grid">
        <div className="plan-map-section">
          <h3 className="plan-section-title">路线预览 (方案 A)</h3>
          <div className="map-placeholder">
            <div className="map-route-line">
              <div className="map-point start">
                <div className="point-label">你的当前位置</div>
              </div>
              <div className="map-point end">
                <div className="point-label">
                  UME影城 (西湖店)
                  <br />
                  预计 19:18 到达
                </div>
              </div>
              <div className="route-info-badge">
                <div className="time">18 分钟</div>
                <div className="desc">地铁 10 分钟 · 步行 8 分钟</div>
              </div>
            </div>
          </div>
        </div>

        <div className="plan-food-section">
          <h3 className="plan-section-title">
            周边美食 (方案 A推荐) <span className="more-link">查看更多美食 &gt;</span>
          </h3>
          <div className="food-scroll-list">
            <div className="food-scroll-item">
              <div className="img food-img--gray" />
              <div className="name">外婆家 (西湖店)</div>
              <div className="desc">杭帮菜 · 步行7分钟</div>
              <div className="price">¥80/人</div>
            </div>
            <div className="food-scroll-item">
              <div className="img food-img--cream" />
              <div className="name">喜茶 (湖滨银泰店)</div>
              <div className="desc">奶茶 · 步行5分钟</div>
              <div className="price">¥20/人</div>
            </div>
            <div className="food-scroll-item">
              <div className="img food-img--pink" />
              <div className="name">新白鹿餐厅</div>
              <div className="desc">浙菜 · 步行5分钟</div>
              <div className="price">¥70/人</div>
            </div>
          </div>
        </div>
      </div>

      <div className="plan-preferences-bar">
        <div className="pref-title">你的观影偏好</div>
        <div className="pref-items">
          <span className="pref-item">
            <span className="icon">🎯</span> 偏好类型: 剧情/温情/喜剧
          </span>
          <span className="pref-item">
            <span className="icon">📍</span> 可接受距离: ≤ 3 km
          </span>
          <span className="pref-item">
            <span className="icon">💰</span> 预算范围: ≤ ¥150/人
          </span>
          <span className="pref-item">
            <span className="icon">🕒</span> 观影时间: 今天 19:00 以后
          </span>
          <span className="pref-item">
            <span className="icon">👥</span> 结伴人数: 2人
          </span>
        </div>
        <button type="button" className="edit-pref-btn">
          调整偏好
        </button>
      </div>
    </div>
  );
};
