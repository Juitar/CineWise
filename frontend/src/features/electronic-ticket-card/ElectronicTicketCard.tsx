import React from 'react';
import { Alert, QRCode, Spin, Tag } from 'antd';
import { ELECTRONIC_TICKET_STATUS_LABELS } from '../../modules/order/status-presentation';
import type {
  ElectronicTicketInvalidationReason,
  ElectronicTicketStatus,
} from '../../modules/order/types';
import './index.css';

export type { ElectronicTicketStatus } from '../../modules/order/types';

export interface ElectronicTicketCardProps {
  ticketCode: string;
  orderNo: string;
  showTitle?: string;
  showId?: string;
  showTime?: string;
  cinemaName?: string;
  cinemaArea?: string;
  cinemaAddress?: string;
  posterUrl?: string | null;
  seatLabels?: string[];
  issuedAt?: string;
  qrPayload?: string;
  status: ElectronicTicketStatus;
  invalidationReason?: ElectronicTicketInvalidationReason | null;
  loading?: boolean;
  error?: string;
  isOfflineReadOnly?: boolean;
}

/**
 * 电子票（票根）展示卡片组件
 * 包含票号、场次、座位、出票时间以及本地渲染的二维码占位符
 * 视觉创新集中在票根/虚线切口，并按设计要求标明状态（已退票无法呈现有效样式）
 */
export const ElectronicTicketCard: React.FC<ElectronicTicketCardProps> = ({
  ticketCode,
  orderNo,
  showTitle = '未知影片',
  showId,
  showTime = '时间待定',
  cinemaName = '未知影院',
  cinemaArea,
  cinemaAddress,
  posterUrl,
  seatLabels = [],
  issuedAt,
  qrPayload,
  status,
  invalidationReason = null,
  loading = false,
  error,
  isOfflineReadOnly = false,
}) => {
  if (loading) {
    return (
      <div className="ticket-card-container">
        <Spin tip="正在读取电子票信息..." />
      </div>
    );
  }

  if (error) {
    return (
      <div className="ticket-card-container">
        <Alert type="error" showIcon message="电子票获取异常" description={error} />
      </div>
    );
  }

  const renderStatusBadge = () => {
    switch (status) {
      case 'REFUNDED':
        return <Tag color="error">{ELECTRONIC_TICKET_STATUS_LABELS.REFUNDED} (不可用)</Tag>;
      case 'INVALIDATED':
        return <Tag color="default">{ELECTRONIC_TICKET_STATUS_LABELS.INVALIDATED}</Tag>;
      case 'VALID':
        return <Tag color="success">{ELECTRONIC_TICKET_STATUS_LABELS.VALID}票可入场</Tag>;
    }
  };

  return (
    <div className={`ticket-card-container status-${status.toLowerCase()}`}>
      {isOfflineReadOnly && (
        <Alert
          className="ticket-offline-alert"
          type="info"
          showIcon
          message="离线只读提示"
          description="当前处于离线模式，仅呈现最近一次缓存的电子票。"
        />
      )}

      {/* 电子票主卡部（头部与影片详情） */}
      <div className="ticket-card-main">
        <div className="ticket-main-header">
          {posterUrl && <img className="ticket-movie-poster" src={posterUrl} alt="" />}
          <h2 className="ticket-movie-title">{showTitle}</h2>
          <div className="ticket-status-area">{renderStatusBadge()}</div>
        </div>

        <div className="ticket-info-list">
          {showId && (
            <div className="ticket-info-item">
              <span className="ticket-info-label">场次编号：</span>
              <span className="ticket-info-value">{showId}</span>
            </div>
          )}
          <div className="ticket-info-item">
            <span className="ticket-info-label">影院：</span>
            <span className="ticket-info-value">{cinemaName}</span>
          </div>
          {cinemaArea && (
            <div className="ticket-info-item">
              <span className="ticket-info-label">区域：</span>
              <span className="ticket-info-value">{cinemaArea}</span>
            </div>
          )}
          {cinemaAddress && (
            <div className="ticket-info-item">
              <span className="ticket-info-label">地址：</span>
              <span className="ticket-info-value">{cinemaAddress}</span>
            </div>
          )}
          <div className="ticket-info-item">
            <span className="ticket-info-label">场次：</span>
            <span className="ticket-info-value">{showTime}</span>
          </div>
          <div className="ticket-info-item">
            <span className="ticket-info-label">座位编号：</span>
            <span className="ticket-info-value seats-highlight">
              {seatLabels.length > 0 ? seatLabels.join('  ') : '详见凭证'}
            </span>
          </div>
          <div className="ticket-info-item">
            <span className="ticket-info-label">出票时间：</span>
            <span className="ticket-info-value">{issuedAt || '---'}</span>
          </div>
          <div className="ticket-info-item">
            <span className="ticket-info-label">订单号：</span>
            <span className="ticket-info-value">{orderNo}</span>
          </div>
        </div>

        {status === 'REFUNDED' && (
          <div className="ticket-refunded-watermark" aria-label="该电子票已退票，不可作为凭证使用">
            <span>已退票</span>
          </div>
        )}
        {status === 'INVALIDATED' && (
          <Alert
            className="ticket-invalidated-alert"
            type="warning"
            showIcon
            message={
              invalidationReason === 'SHOW_ENDED' ? '影片已结束，电子票已失效' : '电子票已失效'
            }
            description="该电子票不可作为入场凭证使用。"
          />
        )}
      </div>

      {/* 票根视觉切割/虚线切口区 */}
      <div className="ticket-stub-divider" aria-hidden="true">
        <div className="ticket-cutout left" />
        <div className="ticket-dashed-line" />
        <div className="ticket-cutout right" />
      </div>

      {/* 二维码周围必须保留完整白色静区，不能叠加票根虚线或装饰边框。 */}
      <div className="ticket-card-footer">
        <div className="ticket-qr-placeholder" aria-label="电子票二维码本地渲染区">
          <div className="ticket-qr-box">
            {qrPayload && status === 'VALID' ? (
              <QRCode
                value={qrPayload}
                type="svg"
                size={144}
                bordered={false}
                aria-label="有效电子票二维码"
              />
            ) : (
              <span className="ticket-qr-tip">当前电子票二维码不可用</span>
            )}
          </div>
        </div>
        <div className="ticket-code-display">
          <span className="ticket-code-label">入场取票码：</span>
          <span className="ticket-code-value">{ticketCode}</span>
        </div>
      </div>
    </div>
  );
};

export default ElectronicTicketCard;
