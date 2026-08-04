import React, { useRef, useMemo, useEffect } from 'react';
import { history, useSearchParams } from 'umi';
import { Button, Spin, Alert, message } from 'antd';
import { useShows, useSeatMap } from '../../../modules/ticketing/hooks';
import { useCreateOrder } from '../../../modules/order/hooks';
import './index.css';

interface PendingOrderSession {
  clientRequestId: string;
  idempotencyKey: string;
  isResultUnknown?: boolean;
}

function getOrderSession(showId: string, seatIds: string[]): PendingOrderSession {
  if (!showId || seatIds.length === 0) {
    return {
      clientRequestId: crypto.randomUUID(),
      idempotencyKey: crypto.randomUUID(),
      isResultUnknown: false,
    };
  }
  const key = `cw_order_${showId}_${[...seatIds].sort().join('_')}`;
  try {
    const raw = sessionStorage.getItem(key);
    if (raw) {
      return JSON.parse(raw) as PendingOrderSession;
    }
  } catch {
    // ignore
  }
  const session: PendingOrderSession = {
    clientRequestId: crypto.randomUUID(),
    idempotencyKey: crypto.randomUUID(),
    isResultUnknown: false,
  };
  try {
    sessionStorage.setItem(key, JSON.stringify(session));
  } catch {
    // ignore
  }
  return session;
}

function saveOrderSessionUnknown(showId: string, seatIds: string[]): void {
  const key = `cw_order_${showId}_${[...seatIds].sort().join('_')}`;
  try {
    const raw = sessionStorage.getItem(key);
    if (raw) {
      const parsed = JSON.parse(raw) as PendingOrderSession;
      parsed.isResultUnknown = true;
      sessionStorage.setItem(key, JSON.stringify(parsed));
    }
  } catch {
    // ignore
  }
}

function clearOrderSession(showId: string, seatIds: string[]): void {
  const key = `cw_order_${showId}_${[...seatIds].sort().join('_')}`;
  try {
    sessionStorage.removeItem(key);
  } catch {
    // ignore
  }
}

function calculateTotalAmount(basePriceStr: string | undefined, count: number): string {
  if (!basePriceStr || count <= 0) return '0.00';
  const parts = basePriceStr.split('.');
  const yuan = parseInt(parts[0] || '0', 10);
  const fen = parseInt((parts[1] || '00').padEnd(2, '0').slice(0, 2), 10);
  const totalCents = (yuan * 100 + fen) * count;
  const totalYuan = Math.floor(totalCents / 100);
  const totalFen = totalCents % 100;
  return `${totalYuan}.${totalFen.toString().padStart(2, '0')}`;
}

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

  const requestedSeatIds = useMemo(() => {
    return searchParams.getAll('seatId');
  }, [searchParams]);

  const sessionRef = useRef<PendingOrderSession | null>(null);
  if (!sessionRef.current) {
    sessionRef.current = getOrderSession(showId, requestedSeatIds);
  }
  const { clientRequestId, idempotencyKey } = sessionRef.current;

  // 1. 获取真实场次列表，根据 showId 匹配权威 basePrice 字符串
  const { loading: showsLoading, shows, error: showsError } = useShows(movieId, cinemaId);
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
    if (sessionRef.current?.isResultUnknown) {
      setResultUnknownState(true);
    }
  }, [setResultUnknownState]);

  useEffect(() => {
    if (isResultUnknown) {
      saveOrderSessionUnknown(showId, requestedSeatIds);
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
    return calculateTotalAmount(currentShow?.basePrice, availableSeats.length);
  }, [order, currentShow, availableSeats.length]);

  if (!showId || requestedSeatIds.length === 0) {
    return (
      <div className="confirm-page-container">
        <Alert
          type="error"
          showIcon
          message="参数错误"
          description="缺失场次 showId 或有效的 seatId 列表。"
        />
      </div>
    );
  }

  const handleReturnToSeats = () => {
    history.push(
      `/shows/${encodeURIComponent(showId)}/seats?movieId=${encodeURIComponent(movieId)}&cinemaId=${encodeURIComponent(cinemaId)}`,
    );
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
        clearOrderSession(showId, requestedSeatIds);
      }
    } catch {
      // Hook已经保存明确错误；这里仅阻止未处理Promise，不写RESULT_UNKNOWN。
    }
  };

  const handleRecoverQuery = async () => {
    try {
      const res = await recoverOrder(clientRequestId);
      if (res) {
        clearOrderSession(showId, requestedSeatIds);
      }
    } catch {
      // 仅捕获异常以防止未处理Promise拒绝
    }
  };

  const selectedLabels = availableSeats.map((s) => s.seatLabel).join('，');

  return (
    <div className="confirm-page-container">
      <h1 className="confirm-page-title">确认订单信息</h1>

      {showsError && (
        <Alert
          type="error"
          showIcon
          className="confirm-alert"
          message="场次信息查询发生异常"
          description={showsError.message || '请重试'}
        />
      )}

      {!showsLoading && !showsError && (!currentShow || currentShow.status !== 'ON_SALE') && (
        <Alert
          type="warning"
          showIcon
          className="confirm-alert"
          message="场次不可售或已失效"
          description="该场次当前不可售或不存在，禁止提交建单，请返回场次列表重新选择。"
          action={
            <Button size="small" onClick={handleReturnToSeats}>
              返回选择
            </Button>
          }
        />
      )}

      {seatError && (
        <Alert
          type="error"
          showIcon
          className="confirm-alert"
          message="无法验证座位最新状态"
          description={seatError.message || '请重试'}
          action={
            <Button size="small" onClick={refetchSeats}>
              重新拉取
            </Button>
          }
        />
      )}

      {hasInvalidSeats && (
        <Alert
          type="warning"
          showIcon
          className="confirm-alert"
          message="任一所选座位不再为 AVAILABLE"
          description="您选择的座位中任一座位已不是 AVAILABLE 状态，为保障交易准确，禁止按照剩余可用座位直接建单。请返回选座页重新选择。"
          action={
            <Button size="small" onClick={handleReturnToSeats}>
              重新选择座位
            </Button>
          }
        />
      )}

      {isSeatConflict && (
        <Alert
          type="error"
          showIcon
          className="confirm-alert"
          message="座位不可锁定"
          description="您选中的座位刚好被其他用户锁定，请返回场次座位图选择其他有效座位。"
          action={
            <Button size="small" type="primary" onClick={handleReturnToSeats}>
              重选座位
            </Button>
          }
        />
      )}

      {orderError && !isSeatConflict && (
        <Alert
          type={orderError.status === 401 ? 'warning' : 'error'}
          showIcon
          className="confirm-alert"
          message={orderError.status === 401 ? '需要用户登录' : '提交建单遇到异常'}
          description={
            isResultUnknown || isRecovering
              ? '提交建单未能确认服务端结果，已锁定为 RESULT_UNKNOWN 保护状态。禁止发起新请求重投，请查询原请求状态。'
              : orderError.message || '系统繁忙，请稍后重试'
          }
        />
      )}

      {seatLoading ? (
        <div className="confirm-loading">
          <Spin tip="核验服务端座位可用情况..." />
        </div>
      ) : (
        <div className="confirm-card">
          <div className="confirm-item-row">
            <span className="confirm-item-label">影厅场次</span>
            <span className="confirm-item-value">
              {seatMap ? seatMap.auditoriumName : '加载中...'}
            </span>
          </div>

          <div className="confirm-item-row">
            <span className="confirm-item-label">选择座位</span>
            <span className="confirm-item-value">
              {availableSeats.length > 0 ? selectedLabels : '无有效座位'}
            </span>
          </div>

          <div className="confirm-item-row">
            <span className="confirm-item-label">座位数量</span>
            <span className="confirm-item-value">{availableSeats.length} 张</span>
          </div>

          <div className="confirm-item-row">
            <span className="confirm-item-label">总价</span>
            <span className="confirm-item-price">¥ {totalAmount}</span>
          </div>
        </div>
      )}

      {order ? (
        <div className="confirm-success-card" role="status">
          <div className="confirm-success-title">订单创建成功！</div>
          <div className="confirm-success-order-id">
            订单编号：<strong>{order.orderNo}</strong>
          </div>
          <div className="confirm-success-req-id">
            应付总计：¥ {order.totalAmount} | 截至时间：
            {new Date(order.expireTime).toLocaleTimeString('zh-CN', {
              hour: '2-digit',
              minute: '2-digit',
              hour12: false,
            })}
          </div>
          <div className="confirm-success-notice">
            注：第一批次落地购票与建单全闭环；模拟支付等功能将于下一迭代正式接入。
          </div>
        </div>
      ) : isResultUnknown ? (
        <div className="confirm-actions">
          <Button type="primary" size="large" loading={isRecovering} onClick={handleRecoverQuery}>
            {isRecovering ? '正在查询订单状态...' : '重新查询订单结果'}
          </Button>
        </div>
      ) : (
        <div className="confirm-actions">
          <Button onClick={handleReturnToSeats} disabled={orderLoading || isRecovering}>
            返回修改
          </Button>
          <Button
            type="primary"
            size="large"
            loading={orderLoading || isRecovering}
            disabled={
              hasInvalidSeats ||
              availableSeats.length === 0 ||
              seatLoading ||
              showsLoading ||
              !!showsError ||
              !currentShow ||
              currentShow.showId !== showId ||
              currentShow.status !== 'ON_SALE'
            }
            onClick={handleSubmitOrder}
          >
            {isRecovering ? '正在同步查询订单...' : '确认并提交订单'}
          </Button>
        </div>
      )}
    </div>
  );
}
