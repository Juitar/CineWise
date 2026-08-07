import { Button, Tag } from 'antd';
import React from 'react';
import { Link } from 'umi';

import type { AgentDisplayItem } from '../../../modules/agent/projection';
import './index.css';

interface AgentDisplayItemViewProps {
  item: AgentDisplayItem;
  onConfirm(itemKey: string, confirmed: boolean): void;
}

function CardFields({ fields }: Pick<AgentDisplayItem, 'fields'>) {
  if (!fields?.length) return null;
  return (
    <dl className="agent-card-fields">
      {fields.map((field, index) => (
        <div key={`${field.label}:${field.value}:${index}`}>
          <dt>{field.label}</dt>
          <dd>{field.value}</dd>
        </div>
      ))}
    </dl>
  );
}

function MessageBubble({ item }: { item: AgentDisplayItem }) {
  return <p className="agent-card-text">{item.text}</p>;
}

function QuestionCard({ item }: { item: AgentDisplayItem }) {
  return (
    <>
      <div className="agent-card-heading">
        <Tag color="purple">需要补充</Tag>
        <strong>{item.title}</strong>
      </div>
      {item.text !== item.title && <p className="agent-card-text">{item.text}</p>}
      {!!item.options?.length && (
        <div className="agent-question-options" aria-label="可选答案（只读）">
          {item.options.map((option, index) => (
            <span key={`${option}:${index}`}>{option}</span>
          ))}
        </div>
      )}
      <CardFields fields={item.fields} />
    </>
  );
}

function RecommendationCard({ item }: { item: AgentDisplayItem }) {
  const isPlan = item.kind === 'plan-card';
  return (
    <>
      <div className="agent-card-heading">
        <Tag color={isPlan ? 'blue' : 'geekblue'}>{isPlan ? '观影方案' : '影片推荐'}</Tag>
        <strong>{item.title}</strong>
      </div>
      <p className="agent-card-text">{item.text}</p>
      <CardFields fields={item.fields} />
    </>
  );
}

function BusinessIntentCard({ item }: { item: AgentDisplayItem }) {
  return (
    <>
      <div className="agent-card-heading">
        <Tag color="green">场次已确认</Tag>
        <strong>{item.title}</strong>
      </div>
      <p className="agent-card-text">{item.text}</p>
      <CardFields fields={item.fields} />
      {item.selectSeatsPath && (
        <Link className="agent-card-primary-link" to={item.selectSeatsPath}>
          去选座
        </Link>
      )}
    </>
  );
}

function ProgressCard({ item }: { item: AgentDisplayItem }) {
  return (
    <>
      <div className="agent-card-heading">
        <span className="agent-progress-indicator" aria-hidden="true" />
        <strong>处理进度</strong>
      </div>
      <p className="agent-card-text">{item.text}</p>
      <CardFields fields={item.fields} />
    </>
  );
}

function ErrorCard({ item }: { item: AgentDisplayItem }) {
  return (
    <>
      <div className="agent-card-heading">
        <Tag color="error">未完成</Tag>
        <strong>这次没有处理成功</strong>
      </div>
      <p className="agent-card-text">{item.text}</p>
    </>
  );
}

function StatusCard({ item }: { item: AgentDisplayItem }) {
  const placeholder = item.kind === 'card-placeholder';
  return (
    <>
      <strong>{placeholder ? '卡片暂不可用' : '运行状态'}</strong>
      <p className="agent-card-text">{item.text}</p>
    </>
  );
}

function ConfirmationActions({
  item,
  onConfirm,
}: {
  item: AgentDisplayItem;
  onConfirm(itemKey: string, confirmed: boolean): void;
}) {
  if (!item.confirmation) return null;
  const disabled =
    item.confirmation.status !== 'PENDING_CONFIRMATION' || item.confirmation.submitting;
  return (
    <div className="agent-confirmation-actions">
      <Button
        type="primary"
        disabled={disabled}
        loading={item.confirmation.submitting}
        onClick={() => onConfirm(item.key, true)}
      >
        确认操作
      </Button>
      <Button disabled={disabled} onClick={() => onConfirm(item.key, false)}>
        拒绝操作
      </Button>
    </div>
  );
}

/** 统一的安全卡片入口；不接收原始 Agent 事件或 payload。 */
export function AgentDisplayItemView({ item, onConfirm }: AgentDisplayItemViewProps) {
  let content: React.ReactNode;
  switch (item.kind) {
    case 'assistant-text':
    case 'user-text':
      content = <MessageBubble item={item} />;
      break;
    case 'question':
      content = <QuestionCard item={item} />;
      break;
    case 'movie-card':
    case 'plan-card':
      content = <RecommendationCard item={item} />;
      break;
    case 'business-intent':
      content = <BusinessIntentCard item={item} />;
      break;
    case 'progress':
      content = <ProgressCard item={item} />;
      break;
    case 'error':
      content = <ErrorCard item={item} />;
      break;
    case 'completed':
    case 'card-placeholder':
      content = <StatusCard item={item} />;
      break;
    default:
      content = (
        <>
          <strong>卡片暂不可用</strong>
          <p className="agent-card-text">卡片暂不可用</p>
        </>
      );
  }

  return (
    <article
      className={`agent-message-item agent-message-item--${item.kind}`}
      data-agent-card-kind={item.kind}
      role="listitem"
    >
      {content}
      <ConfirmationActions item={item} onConfirm={onConfirm} />
    </article>
  );
}
