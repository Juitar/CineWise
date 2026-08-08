import { Button, DatePicker, Input, Select } from 'antd';
import type { Dayjs } from 'dayjs';
import React, { useState } from 'react';
import type { OrderStatus } from '../admin-order/types';
import './index.css';

const { RangePicker } = DatePicker;

export interface AdminOrderFiltersParams {
  orderNo?: string;
  userKeyword?: string;
  status?: OrderStatus;
  movieId?: string;
  showId?: string;
  dateFrom?: string;
  dateTo?: string;
}

export interface AdminOrderFiltersProps {
  /**
   * 触发查询回调
   *
   * @param params 收集的合法白名单查询条件
   */
  onSearch: (params: AdminOrderFiltersParams) => void;
  /**
   * 触发重置回调
   */
  onReset: () => void;
  /**
   * 是否正在加载中，用于禁用按钮
   */
  loading?: boolean;
}

/**
 * 管理订单筛选区组件
 *
 * 严格使用后端白名单条件：
 * - orderNo: 订单号
 * - userKeyword: 用户标识关键字
 * - status: 订单状态
 * - movieId: 影片ID
 * - showId: 场次ID
 * - dateFrom/dateTo: 下单时间范围
 *
 * 不发起实际网络请求，仅通过 onSearch 将条件传递给父级。
 */
export const AdminOrderFilters: React.FC<AdminOrderFiltersProps> = ({
  onSearch,
  onReset,
  loading = false,
}) => {
  const [orderNo, setOrderNo] = useState('');
  const [userKeyword, setUserKeyword] = useState('');
  const [status, setStatus] = useState<OrderStatus | undefined>(undefined);
  const [movieId, setMovieId] = useState('');
  const [showId, setShowId] = useState('');
  const [dates, setDates] = useState<[Dayjs | null, Dayjs | null] | null>(null);

  const handleSearch = () => {
    const params: AdminOrderFiltersParams = {};

    if (orderNo.trim()) params.orderNo = orderNo.trim();
    if (userKeyword.trim()) params.userKeyword = userKeyword.trim();
    if (status) params.status = status;
    if (movieId.trim()) params.movieId = movieId.trim();
    if (showId.trim()) params.showId = showId.trim();

    if (dates && dates[0] && dates[1]) {
      // 传递 yyyy-MM-dd 格式，不进行 UTC 转换
      params.dateFrom = dates[0].format('YYYY-MM-DD');
      params.dateTo = dates[1].format('YYYY-MM-DD');
    }

    onSearch(params);
  };

  const handleReset = () => {
    setOrderNo('');
    setUserKeyword('');
    setStatus(undefined);
    setMovieId('');
    setShowId('');
    setDates(null);
    onReset();
  };

  return (
    <div className="admin-order-filters">
      <div className="admin-order-filters-grid">
        <div className="admin-order-filters-item">
          <div className="admin-order-filters-label">订单号</div>
          <Input
            id="filter-orderNo"
            name="orderNo"
            placeholder="请输入订单号"
            value={orderNo}
            onChange={(e) => setOrderNo(e.target.value)}
            disabled={loading}
          />
        </div>
        <div className="admin-order-filters-item">
          <div className="admin-order-filters-label">用户关键词</div>
          <Input
            id="filter-userKeyword"
            name="userKeyword"
            placeholder="请输入用户ID或邮箱关键字"
            value={userKeyword}
            onChange={(e) => setUserKeyword(e.target.value)}
            disabled={loading}
          />
        </div>
        <div className="admin-order-filters-item">
          <div className="admin-order-filters-label">订单状态</div>
          <Select
            id="filter-status"
            className="admin-order-filters-control-full"
            placeholder="全部状态"
            allowClear
            value={status}
            onChange={setStatus}
            disabled={loading}
            options={[
              { label: '待支付', value: 'PENDING_PAYMENT' },
              { label: '支付确认中', value: 'PAYING' },
              { label: '已支付', value: 'PAID' },
              { label: '已取消', value: 'CANCELLED' },
              { label: '已过期', value: 'EXPIRED' },
              { label: '退款处理中', value: 'REFUNDING' },
              { label: '已退款', value: 'REFUNDED' },
            ]}
          />
        </div>
        <div className="admin-order-filters-item">
          <div className="admin-order-filters-label">影片ID</div>
          <Input
            id="filter-movieId"
            name="movieId"
            placeholder="请输入影片ID"
            value={movieId}
            onChange={(e) => setMovieId(e.target.value)}
            disabled={loading}
          />
        </div>
        <div className="admin-order-filters-item">
          <div className="admin-order-filters-label">场次ID</div>
          <Input
            id="filter-showId"
            name="showId"
            placeholder="请输入场次ID"
            value={showId}
            onChange={(e) => setShowId(e.target.value)}
            disabled={loading}
          />
        </div>
        <div className="admin-order-filters-item">
          <div className="admin-order-filters-label">下单时间</div>
          <RangePicker
            id="filter-dates"
            className="admin-order-filters-control-full"
            placeholder={['开始日期', '结束日期']}
            value={dates}
            onChange={(val) => setDates(val || null)}
            disabled={loading}
          />
        </div>
      </div>
      <div className="admin-order-filters-actions">
        <Button onClick={handleReset} disabled={loading}>
          重置
        </Button>
        <Button type="primary" onClick={handleSearch} loading={loading}>
          查询
        </Button>
      </div>
    </div>
  );
};
