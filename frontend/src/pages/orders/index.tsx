import React, { useMemo, useState } from 'react';
import { history } from 'umi';
import { OrderList } from '../../features/order-list/OrderList';
import type { OrderSummaryItem } from '../../features/order-list/OrderList';
import { useOrders } from '../../modules/order/transaction-hooks';
import { formatOrderDateTime } from '../../modules/order/formatters';
import type { OrderStatus } from '../../modules/order/types';
import { TransactionBackButton } from '../../features/transaction-back-button/TransactionBackButton';
import './index.css';

/**
 * 个人订单列表页面：/orders
 * 筛选和分页均查询服务端本人订单，不在浏览器伪造业务记录。
 */
export default function OrdersPage() {
  const [selectedStatus, setSelectedStatus] = useState<string>('ALL');
  const [selectedDate, setSelectedDate] = useState<string>('');
  const [currentPage, setCurrentPage] = useState<number>(1);
  const query = useMemo(
    () => ({
      status: selectedStatus === 'ALL' ? undefined : (selectedStatus as OrderStatus),
      dateFrom: selectedDate || undefined,
      dateTo: selectedDate || undefined,
      page: currentPage,
      size: 10,
    }),
    [currentPage, selectedDate, selectedStatus],
  );
  const ordersQuery = useOrders(query);
  const orders: OrderSummaryItem[] = (ordersQuery.data?.records ?? []).map((order) => ({
    orderId: order.orderId,
    orderNo: order.orderNo,
    showTitle: '影片信息暂不可用',
    showId: order.showId,
    showTime: formatOrderDateTime(order.showStartTime),
    ticketCount: order.ticketCount,
    totalAmount: order.totalAmount,
    status: order.status,
    expireTime: order.expireTime,
  }));

  return (
    <div className="orders-page-wrapper">
      <div className="orders-page-content">
        <TransactionBackButton onBack={() => history.push('/')} label="返回首页" />
        <OrderList
          orders={orders}
          loading={ordersQuery.loading}
          error={ordersQuery.error?.message}
          selectedStatus={selectedStatus}
          selectedDate={selectedDate}
          currentPage={currentPage}
          totalCount={ordersQuery.data?.total ?? 0}
          onStatusChange={(status) => {
            setSelectedStatus(status);
            setCurrentPage(1);
          }}
          onDateFilterChange={(date) => {
            setSelectedDate(date);
            setCurrentPage(1);
          }}
          onPageChange={(page) => setCurrentPage(page)}
          onOrderClick={(orderNo) => history.push(`/orders/${encodeURIComponent(orderNo)}`)}
        />
      </div>
    </div>
  );
}
