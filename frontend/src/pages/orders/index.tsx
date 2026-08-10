import React, { useMemo, useState } from 'react';
import { history } from 'umi';
import { OrderList } from '../../features/order-list/OrderList';
import type { OrderSummaryItem } from '../../features/order-list/OrderList';
import { useOrders } from '../../modules/order/transaction-hooks';
import { useOrderContentDetails } from '../../modules/order/useOrderContentDetails';
import { formatOrderDateTime } from '../../modules/order/formatters';
import type { OrderStatus } from '../../modules/order/types';
import {
  PROFILE_BREADCRUMB_ITEM,
  TransactionBreadcrumb,
} from '../../features/transaction-breadcrumb/TransactionBreadcrumb';
import { OrderContentNotice } from '../../features/order-content-notice/OrderContentNotice';
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
  const records = ordersQuery.data?.records ?? [];
  const contentReferences = useMemo(
    () => records.map(({ movieId, cinemaId }) => ({ movieId, cinemaId })),
    [records],
  );
  const content = useOrderContentDetails(contentReferences);
  const orders: OrderSummaryItem[] = records.map((order) => ({
    orderId: order.orderId,
    orderNo: order.orderNo,
    showTitle: content.moviesById.get(order.movieId)?.title ?? '影片信息暂不可用',
    showId: order.showId,
    showTime: formatOrderDateTime(order.showStartTime),
    ticketCount: order.ticketCount,
    totalAmount: order.totalAmount,
    status: order.status,
    expireTime: order.expireTime,
    posterUrl: content.moviesById.get(order.movieId)?.posterUrl,
    cinemaName: content.cinemasById.get(order.cinemaId)?.name ?? '影院信息暂不可用',
    cinemaArea: content.cinemasById.get(order.cinemaId)?.area ?? undefined,
    cinemaAddress: content.cinemasById.get(order.cinemaId)?.address ?? undefined,
  }));
  const movieReferenceCount = new Set(
    contentReferences.map(({ movieId }) => movieId).filter(Boolean),
  ).size;
  const cinemaReferenceCount = new Set(
    contentReferences.map(({ cinemaId }) => cinemaId).filter(Boolean),
  ).size;
  const isContentLoading =
    content.isLoading ||
    (!content.hasUnavailableContent &&
      (content.moviesById.size < movieReferenceCount ||
        content.cinemasById.size < cinemaReferenceCount));
  const isPageLoading = ordersQuery.loading || isContentLoading;
  const shouldShowContentNotice = !ordersQuery.loading && !isContentLoading;

  return (
    <div className="orders-page-wrapper">
      <div className="orders-page-content">
        <TransactionBreadcrumb items={[PROFILE_BREADCRUMB_ITEM, { label: '我的订单' }]} />
        {shouldShowContentNotice && (
          <OrderContentNotice
            isLoading={false}
            hasUnavailableContent={content.hasUnavailableContent}
            onRetry={content.refresh}
          />
        )}
        <OrderList
          orders={orders}
          loading={isPageLoading}
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
