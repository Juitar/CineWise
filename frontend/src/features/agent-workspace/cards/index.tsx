import { Button, Input, Tag } from 'antd';
import React, { useState } from 'react';
import { Link } from 'umi';

import type { AgentDisplayItem } from '../../../modules/agent/projection';
import './index.css';

interface AgentDisplayItemViewProps {
  item: AgentDisplayItem;
  answerDisabled: boolean;
  onAnswer(itemKey: string, answer: string): Promise<boolean>;
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

function QuestionCard({
  answerDisabled,
  item,
  onAnswer,
}: Pick<AgentDisplayItemViewProps, 'answerDisabled' | 'item' | 'onAnswer'>) {
  const [answer, setAnswer] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const expired = item.question ? Date.parse(item.question.expiresAt) <= Date.now() : true;
  const disabled = answerDisabled || expired || submitted || submitting;

  const submitAnswer = async (value: string) => {
    const normalized = value.trim();
    if (disabled || !normalized) return;
    setSubmitting(true);
    try {
      const sent = await onAnswer(item.key, normalized);
      if (sent) setSubmitted(true);
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <>
      <div className="agent-card-heading">
        <Tag color="purple">需要补充</Tag>
        <strong>{item.title}</strong>
      </div>
      {item.text !== item.title && <p className="agent-card-text">{item.text}</p>}
      {!!item.question?.options.length && (
        <div className="agent-question-options" aria-label="快捷答案">
          {item.question.options.map((option) => (
            <Button
              key={option.optionId}
              disabled={disabled}
              onClick={() => void submitAnswer(option.value)}
            >
              {option.label}
            </Button>
          ))}
        </div>
      )}
      {item.question?.allowFreeText && (
        <form
          className="agent-question-form"
          onSubmit={(event) => {
            event.preventDefault();
            void submitAnswer(answer);
          }}
        >
          <Input
            aria-label="补充回答"
            maxLength={2000}
            value={answer}
            disabled={disabled}
            placeholder="也可以直接输入答案"
            onChange={(event) => setAnswer(event.target.value)}
          />
          <Button
            htmlType="submit"
            type="primary"
            loading={submitting}
            disabled={disabled || !answer.trim()}
          >
            提交回答
          </Button>
        </form>
      )}
      {expired && <p className="agent-card-status">该问题已过期，请重新发起需求</p>}
      {submitted && <p className="agent-card-status">回答已提交</p>}
      <CardFields fields={item.fields} />
    </>
  );
}

const PLAN_TYPE_TEXT: Readonly<Record<string, string>> = {
  COMPREHENSIVE: '综合推荐',
  LOW_PRICE: '低价优先',
  TIME_FIRST: '时间优先',
};

function PlanCard({ item }: { item: AgentDisplayItem }) {
  return (
    <>
      <div className="agent-card-heading">
        <Tag color="blue">观影方案</Tag>
        <strong>{item.title}</strong>
      </div>
      <p className="agent-card-text">{item.text}</p>
      {item.plans?.length ? (
        <div className="agent-plan-list">
          {item.plans.map((plan) => {
            const expired = plan.expired || Date.parse(plan.expiresAt) <= Date.now();
            return (
              <section className="agent-plan" key={plan.showId}>
                <div className="agent-plan-heading">
                  <strong>{PLAN_TYPE_TEXT[plan.planType] ?? plan.planType}</strong>
                  <span className="agent-plan-price">
                    {plan.currency === 'CNY' ? '¥' : `${plan.currency} `}
                    {plan.price}
                  </span>
                </div>
                <h3>{plan.movieName}</h3>
                <p>{plan.cinemaName}</p>
                <dl className="agent-plan-details">
                  <div>
                    <dt>开场时间</dt>
                    <dd>{plan.startTime}</dd>
                  </div>
                  {plan.rating !== null && (
                    <div>
                      <dt>评分</dt>
                      <dd>{plan.rating}</dd>
                    </div>
                  )}
                  {plan.distanceMeters !== null && (
                    <div>
                      <dt>距离</dt>
                      <dd>{plan.distanceMeters} 米</dd>
                    </div>
                  )}
                  <div>
                    <dt>来源</dt>
                    <dd>{plan.source}</dd>
                  </div>
                  <div>
                    <dt>数据时间</dt>
                    <dd>{plan.dataAt}</dd>
                  </div>
                  <div>
                    <dt>有效期至</dt>
                    <dd>{plan.expiresAt}</dd>
                  </div>
                </dl>
                {!!plan.reasons.length && (
                  <ul className="agent-plan-reasons">
                    {plan.reasons.map((reason) => (
                      <li key={reason}>{reason}</li>
                    ))}
                  </ul>
                )}
                {(expired || !plan.purchaseEligible) && (
                  <p className="agent-card-status">{expired ? '方案已过期' : '当前不可购'}</p>
                )}
              </section>
            );
          })}
        </div>
      ) : (
        <p className="agent-card-status">暂无可用方案</p>
      )}
      {item.relaxationSuggestion && (
        <p className="agent-card-status">可调整条件：{item.relaxationSuggestion}</p>
      )}
      <CardFields fields={item.fields} />
    </>
  );
}

function RecommendationCard({ item }: { item: AgentDisplayItem }) {
  const isConfirmation = item.confirmation !== undefined;
  return (
    <>
      <div className="agent-card-heading">
        <Tag color={isConfirmation ? 'gold' : 'geekblue'}>
          {isConfirmation ? '操作确认' : '影片推荐'}
        </Tag>
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
export function AgentDisplayItemView({
  answerDisabled,
  item,
  onAnswer,
  onConfirm,
}: AgentDisplayItemViewProps) {
  let content: React.ReactNode;
  switch (item.kind) {
    case 'assistant-text':
    case 'user-text':
      content = <MessageBubble item={item} />;
      break;
    case 'question':
      content = <QuestionCard item={item} answerDisabled={answerDisabled} onAnswer={onAnswer} />;
      break;
    case 'movie-card':
      content = <RecommendationCard item={item} />;
      break;
    case 'plan-card':
      content = item.confirmation ? <RecommendationCard item={item} /> : <PlanCard item={item} />;
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
