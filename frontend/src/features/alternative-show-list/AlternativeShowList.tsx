import React from 'react';
import { Button, Tag, Empty, Spin, Alert } from 'antd';
import './index.css';

export interface AlternativeShowItem {
  showId: string;
  movieId: string;
  cinemaId: string;
  startTime: string;
  basePrice: string;
  status: string;
  availableSeatCount: number;
}

export interface AlternativeShowListProps {
  shows: AlternativeShowItem[];
  loading?: boolean;
  error?: string;
  onSelectShow?: (show: AlternativeShowItem) => void;
}

/**
 * 替代场次展示列表组件
 * 当该放映可能发生变动时，展示同影片同影院可用替代场次
 * 包含电影 ID、影院 ID、场次 ID、起始时间与最低票价
 */
export const AlternativeShowList: React.FC<AlternativeShowListProps> = ({
  shows,
  loading = false,
  error,
  onSelectShow,
}) => {
  if (loading) {
    return (
      <div className="alt-shows-container">
        <Spin tip="正在查询其他可选替代场次..." />
      </div>
    );
  }

  if (error) {
    return (
      <div className="alt-shows-container">
        <Alert type="error" showIcon message="获取替代场次失败" description={error} />
      </div>
    );
  }

  if (shows.length === 0) {
    return (
      <div className="alt-shows-container">
        <Empty description="当前暂无其他可替代的同类放映场次" />
      </div>
    );
  }

  return (
    <div className="alt-shows-container">
      <h3 className="alt-shows-title">推荐替代场次（同影院）</h3>
      <div className="alt-shows-list" role="list">
        {shows.map((item) => (
          <article key={item.showId} className="alt-show-card">
            <div className="alt-show-main">
              <div className="alt-show-time">{item.startTime}</div>
              <div className="alt-show-meta">
                <Tag color="success">{item.status === 'ON_SALE' ? '热售中' : item.status}</Tag>
                <span className="alt-show-seats">余座：{item.availableSeatCount} 个</span>
              </div>
            </div>
            <div className="alt-show-action">
              <span className="alt-show-price">¥ {item.basePrice}</span>
              <Button type="primary" size="small" onClick={() => onSelectShow?.(item)}>
                选择此场
              </Button>
            </div>
          </article>
        ))}
      </div>
    </div>
  );
};

export default AlternativeShowList;
