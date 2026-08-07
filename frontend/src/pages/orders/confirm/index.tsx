import React, { useMemo, useEffect } from 'react';
import { history, useSearchParams } from 'umi';
import { Alert, message } from 'antd';
import { ErrorBlock } from 'antd-mobile';
import { OrderConfirmation } from '../../../features/order-confirmation/OrderConfirmation';
import { OrderCreateSuccess } from '../../../features/order-create-success/OrderCreateSuccess';
import { useShows, useSeatMap } from '../../../modules/ticketing/hooks';
import { useCreateOrder } from '../../../modules/order/hooks';
import {
  clearConfirmOrderSession,
  markConfirmOrderUnknown,
  useConfirmOrderSession,
} from '../../../modules/order/confirm-session';
import { calculateOrderTotalAmount } from '../../../modules/order/money';
import { formatOrderTime } from '../../../modules/order/formatters';
import { buildOrderDetailPath, buildPaymentPath } from '../../../modules/order/routes';
import { ApiError } from '../../../shared/api/ApiError';
import { useMediaQuery } from '../../../shared/hooks/useMediaQuery';
import { TransactionBreadcrumb } from '../../../features/transaction-breadcrumb/TransactionBreadcrumb';
import './index.css';

/**
 * 订单确认页面：/orders/confirm?showId=...&seatId=1&seatId=2&movieId=...&cinemaId=...
 * 1. 严格使用 searchParams.getAll('seatId') 读取多座位；
 * 2. 页面载入/重载后重新请求权威座位图，若任一所选座位不再为 AVAILABLE，禁止按剩余座位直接建单；
 * 3. 稳健持久化非敏感流水号与幂等键，遇到 RESULT_UNKNOWN 保持该状态（即时刷新页面也不重发），只提供重新查询订单结果按钮；
 * 4. 金额由真实场次字符串基础价按分计算，最终以建单响应为准。
 */
export default function OrderConfirmPage() {
  const [searchParams] = useSearchParams();
  const showId = searchParams.get('showId') || '';
  const movieId = searchParams.get('movieId') || '';
  const cinemaId = searchParams.get('cinemaId') || '';
  const isMobile = useMediaQuery('(max-width: 1023px)');

  const requestedSeatIds = useMemo(() => {
    return searchParams.getAll('seatId');
  }, [searchParams]);

  const confirmSession = useConfirmOrderSession(showId, requestedSeatIds);
  const { clientRequestId, idempotencyKey } = confirmSession;

  // 1. 获取真实场次列表，根据 showId 匹配权威 basePrice 字符串
  const {
    loading: showsLoading,
    shows,
    error: showsError,
    refetch: refetchShows,
  } = useShows(movieId, cinemaId);
  const currentShow = useMemo(() => {
    return shows.find((s) => s.showId === showId);
  }, [shows, showId]);

  // 2. 权威服务端座位图查询与最新可用性过滤
  const {
    loading: seatLoading,
    seatMap,
    error: seatError,
    refetch: refetchSeats,
  } = useSeatMap(showId);

  const {
    loading: orderLoading,
    isRecovering,
    isResultUnknown,
    isSeatConflict,
    error: orderError,
    order,
    submitOrder,
    recoverOrder,
    setResultUnknownState,
  } = useCreateOrder();

  useEffect(() => {
    setResultUnknownState(confirmSession.isResultUnknown);
  }, [confirmSession.isResultUnknown, setResultUnknownState]);

  useEffect(() => {
    if (isResultUnknown) {
      markConfirmOrderUnknown(showId, requestedSeatIds);
    }
  }, [isResultUnknown, showId, requestedSeatIds]);

  const availableSeats = useMemo(() => {
    if (!seatMap || !seatMap.seats) {
      return [];
    }
    return seatMap.seats.filter(
      (seat) => requestedSeatIds.includes(seat.seatId) && seat.status === 'AVAILABLE',
    );
  }, [seatMap, requestedSeatIds]);

  const hasInvalidSeats = useMemo(() => {
    if (!seatMap || !seatMap.seats || requestedSeatIds.length === 0) {
      return false;
    }
    return availableSeats.length !== requestedSeatIds.length;
  }, [seatMap, requestedSeatIds, availableSeats]);

  const totalAmount = useMemo(() => {
    if (order) {
      return order.totalAmount;
    }
    return calculateOrderTotalAmount(currentShow?.basePrice, availableSeats.length);
  }, [order, currentShow, availableSeats.length]);

  if (!showId || requestedSeatIds.length === 0) {
    return (
      <div className="confirm-page-container">
        {isMobile ? (
          <ErrorBlock
            status="default"
            title="参数错误"
            description="缺失场次 showId 或有效的 seatId 列表。"
          />
        ) : (
          <Alert
            type="error"
            showIcon
            message="参数错误"
            description="缺失场次 showId 或有效的 seatId 列表。"
          />
        )}
      </div>
    );
  }

  const seatsPath = `/shows/${encodeURIComponent(showId)}/seats?movieId=${encodeURIComponent(movieId)}&cinemaId=${encodeURIComponent(cinemaId)}`;

  const handleReturnToSeats = () => {
    history.push(seatsPath);
  };

  const handleSubmitOrder = async () => {
    if (
      hasInvalidSeats ||
      availableSeats.length === 0 ||
      !currentShow ||
      showsLoading ||
      !!showsError ||
      currentShow.showId !== showId ||
      currentShow.status !== 'ON_SALE'
    ) {
      message.error('当前无法验证场次信息或选座状态无效，禁止按剩余座位直接建单');
      return;
    }
    try {
      const res = await submitOrder(
        {
          showId,
          seatIds: availableSeats.map((s) => s.seatId),
          clientRequestId,
        },
        idempotencyKey,
      );
      if (res) {
        clearConfirmOrderSession(showId, requestedSeatIds);
      }
    } catch (error: unknown) {
      if (error instanceof ApiError && error.code === 204001) {
        // 204001 是明确失败，可以清除本次幂等会话；刷新后回到座位图，避免旧 URL 继续携带失效选择。
        await refetchSeats();
        clearConfirmOrderSession(showId, requestedSeatIds);
        message.error('座位不可锁定，请重新选择');
        handleReturnToSeats();
      }
      // 其他明确错误由 Hook 保存；这里仅阻止未处理 Promise，不写 RESULT_UNKNOWN。
    }
  };

  const handleRecoverQuery = async () => {
    try {
      const res = await recoverOrder(clientRequestId);
      if (res) {
        clearConfirmOrderSession(showId, requestedSeatIds);
      }
    } catch {
      // 仅捕获异常以防止未处理Promise拒绝
    }
  };

  const selectedLabels = availableSeats.map((seat) => seat.seatLabel);
  const isShowNotAvailable =
    !showsLoading && !showsError && (!currentShow || currentShow.status !== 'ON_SALE');
  const isNotAvailable = hasInvalidSeats || isShowNotAvailable;
  const contextError = showsError ?? seatError;
  const confirmationError = isSeatConflict ? null : (contextError ?? orderError);
  const submitDisabled =
    hasInvalidSeats ||
    availableSeats.length === 0 ||
    seatLoading ||
    showsLoading ||
    !!contextError ||
    !currentShow ||
    currentShow.showId !== showId ||
    currentShow.status !== 'ON_SALE';

  const handleReloadContext = () => {
    void Promise.all([refetchShows(), refetchSeats()]);
  };

  const handlePay = () => {
    if (order?.status === 'PENDING_PAYMENT') {
      // 支付必须由用户在支付页主动确认，建单成功页只提供安全导航出口。
      history.push(buildPaymentPath(order.orderNo));
    }
  };

  const handleViewOrder = () => {
    if (order) {
      history.push(buildOrderDetailPath(order.orderNo));
    }
  };

  return (
    <div className="confirm-page-container">
      <TransactionBreadcrumb
        items={
          order
            ? [
                { label: '我的订单', to: '/orders' },
                { label: '订单详情', to: buildOrderDetailPath(order.orderNo) },
                { label: '订单已创建' },
              ]
            : [
                {
                  label: '选择场次',
                  to: `/shows?movieId=${encodeURIComponent(movieId)}&cinemaId=${encodeURIComponent(cinemaId)}`,
                },
                { label: '选择座位', to: seatsPath },
                { label: '确认订单' },
              ]
        }
      />
      <h1 className="confirm-page-title">确认订单信息</h1>

      {order ? (
        <OrderCreateSuccess
          orderNo={order.orderNo}
          totalAmount={order.totalAmount}
          expireTimeText={formatOrderTime(order.expireTime)}
          onPay={order.status === 'PENDING_PAYMENT' ? handlePay : undefined}
          onViewOrder={handleViewOrder}
          loading={orderLoading || isRecovering}
        />
      ) : (
        <OrderConfirmation
          auditoriumName={seatMap?.auditoriumName ?? '影厅信息待确认'}
          seatLabels={selectedLabels}
          ticketCount={availableSeats.length}
          totalAmount={totalAmount}
          loading={seatLoading || showsLoading}
          submitting={orderLoading}
          recovering={isRecovering}
          submitDisabled={submitDisabled}
          error={confirmationError}
          isConflict={isSeatConflict}
          isNotAvailable={isNotAvailable}
          isResultUnknown={isResultUnknown}
          onSubmit={handleSubmitOrder}
          onRetry={handleRecoverQuery}
          onCancel={handleReturnToSeats}
          onErrorAction={contextError ? handleReloadContext : handleReturnToSeats}
          errorActionLabel={contextError ? '重新加载' : '返回修改'}
        />
      )}
    </div>
  );
}
