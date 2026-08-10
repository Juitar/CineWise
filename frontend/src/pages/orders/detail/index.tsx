import React, { useMemo } from 'react';
import { history, useParams } from 'umi';
import { message } from 'antd';
import { OrderDetail } from '../../../features/order-detail/OrderDetail';
import {
  useCancelOrder,
  useOrder,
  usePaymentQuery,
} from '../../../modules/order/transaction-hooks';
import { formatOrderDateTime } from '../../../modules/order/formatters';
import {
  ORDERS_BREADCRUMB_ITEM,
  PROFILE_BREADCRUMB_ITEM,
  TransactionBreadcrumb,
} from '../../../features/transaction-breadcrumb/TransactionBreadcrumb';
import { OrderContentNotice } from '../../../features/order-content-notice/OrderContentNotice';
import { useOrderContentDetails } from '../../../modules/order/useOrderContentDetails';
import { useTravelTaskByOrder } from '../../../modules/travel/useTravelTask';
import { isTravelAdviceAvailable } from '../../../modules/travel/advice-availability';
import { ApiError } from '../../../shared/api/ApiError';
import './index.css';

/**
 * 个人订单详情页面：/orders/:orderNo
 * 取消响应未知时只刷新当前订单状态，不自动重发取消请求。
 */
export default function OrderDetailPage() {
  const { orderNo = '' } = useParams<{ orderNo: string }>();
  const orderQuery = useOrder(orderNo);
  const cancellation = useCancelOrder(orderNo);
  const paymentQuery = usePaymentQuery(orderNo);
  const travelTaskQuery = useTravelTaskByOrder();
  const order = orderQuery.data;
  const content = useOrderContentDetails(
    useMemo(() => (order ? [{ movieId: order.movieId, cinemaId: order.cinemaId }] : []), [order]),
  );
  const movie = order ? content.moviesById.get(order.movieId) : undefined;
  const cinema = order ? content.cinemasById.get(order.cinemaId) : undefined;
  const isContentLoading = Boolean(
    order &&
    (content.isLoading ||
      (!content.hasUnavailableContent && (movie === undefined || cinema === undefined))),
  );
  const isPageLoading = orderQuery.loading || isContentLoading;
  const shouldShowContentNotice = Boolean(order) && !isContentLoading;
  const canViewTravelAdvice = Boolean(
    order?.showStartTime && isTravelAdviceAvailable(order.showStartTime),
  );

  const handleCancel = async () => {
    const cancelled = await cancellation.submit();
    if (cancelled) {
      await orderQuery.refresh();
      message.success('订单已取消');
      return;
    }
  };

  const recoverCancellation = async () => {
    const recovered = await cancellation.recover();
    if (recovered) {
      await orderQuery.refresh();
    }
  };

  const handleViewTicket = async () => {
    const payment = await paymentQuery.query();
    if (payment?.ticketId) {
      history.push(`/tickets/${encodeURIComponent(payment.ticketId)}`);
      return;
    }
    if (!paymentQuery.error) {
      message.error('暂时无法取得电子票信息，请稍后重试');
    }
  };

  const handleViewTravel = async () => {
    if (!order?.orderId) {
      message.error('当前订单信息不完整，暂时无法查询出行任务');
      return;
    }
    try {
      const task = await travelTaskQuery.find(order.orderId);
      if (task) history.push(`/travel/${encodeURIComponent(task.taskId)}`);
    } catch (error) {
      if (error instanceof ApiError && (error.status === 404 || error.code === 207001)) {
        message.info('该订单的出行任务尚未建立或不可访问');
      } else {
        message.error('出行任务暂时无法查询，请稍后重试');
      }
    }
  };

  return (
    <div className="order-detail-page-wrapper">
      <div className="order-detail-page-content">
        <TransactionBreadcrumb
          items={[PROFILE_BREADCRUMB_ITEM, ORDERS_BREADCRUMB_ITEM, { label: '订单详情' }]}
        />
        {shouldShowContentNotice ? (
          <OrderContentNotice
            isLoading={false}
            hasUnavailableContent={content.hasUnavailableContent}
            onRetry={content.refresh}
          />
        ) : null}
        <OrderDetail
          orderNo={order?.orderNo ?? orderNo}
          status={order?.status ?? 'PENDING_PAYMENT'}
          showTitle={order ? (movie?.title ?? '影片信息暂不可用') : undefined}
          showTime={formatOrderDateTime(order?.showStartTime)}
          posterUrl={movie?.posterUrl}
          cinemaName={cinema?.name ?? '影院信息暂不可用'}
          cinemaArea={cinema?.area ?? undefined}
          cinemaAddress={cinema?.address ?? undefined}
          ticketCount={order?.ticketCount ?? 0}
          unitPrice={order?.unitPrice ?? '0.00'}
          totalAmount={order?.totalAmount ?? '0.00'}
          expireTime={order?.expireTime ? formatOrderDateTime(order.expireTime) : undefined}
          updatedAt={order?.updatedAt ? formatOrderDateTime(order.updatedAt) : undefined}
          loading={isPageLoading}
          error={
            orderQuery.error?.message ?? cancellation.error?.message ?? paymentQuery.error?.message
          }
          onPay={() =>
            history.push(
              order?.status === 'PAYING'
                ? `/payments/${encodeURIComponent(orderNo)}/result`
                : `/payments/${encodeURIComponent(orderNo)}`,
            )
          }
          onCancel={() => void handleCancel()}
          onViewTicket={() => void handleViewTicket()}
          onViewTravel={canViewTravelAdvice ? () => void handleViewTravel() : undefined}
          onApplyRefund={() => history.push(`/orders/${encodeURIComponent(orderNo)}/refund`)}
          onViewOrders={() => history.push('/orders')}
          cancelResultUnknown={cancellation.resultUnknown}
          onRecoverCancel={() => void recoverCancellation()}
        />
      </div>
    </div>
  );
}
