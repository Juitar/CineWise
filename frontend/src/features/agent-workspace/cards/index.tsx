import { Button, Input, Tag } from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import Markdown, { defaultUrlTransform } from 'react-markdown';
import { Link } from 'umi';

import type { AgentDisplayItem } from '../../../modules/agent/projection';
import './index.css';

interface AgentDisplayItemViewProps {
  item: AgentDisplayItem;
  answerDisabled: boolean;
  planPresentation?: 'full' | 'summary';
  selectSeatsEnabled?: boolean;
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

function useTypingText(text: string, typing: boolean | undefined): string {
  const [visibleText, setVisibleText] = useState(typing ? '' : text);
  const visibleTextRef = useRef(typing ? '' : text);

  useEffect(() => {
    if (!typing) {
      visibleTextRef.current = text;
      setVisibleText(text);
      return undefined;
    }

    // SSE 后续分片到达时，不能从第一个字重新播放，否则用户会看到消息反复闪回。
    // 同一 streamId 的 text 只会追加；如果意外收到不相同的内容，安全地从头播放新内容。
    let index = text.startsWith(visibleTextRef.current) ? visibleTextRef.current.length : 0;
    if (index === 0 && visibleTextRef.current) {
      visibleTextRef.current = '';
      setVisibleText('');
    }
    const timer = window.setInterval(() => {
      index = Math.min(index + 1, text.length);
      const next = text.slice(0, index);
      visibleTextRef.current = next;
      setVisibleText(next);
      if (index >= text.length) window.clearInterval(timer);
    }, 35);
    return () => window.clearInterval(timer);
  }, [text, typing]);

  return visibleText;
}

function MessageBubble({ item }: { item: AgentDisplayItem }) {
  const text = useTypingText(item.text, item.typing);
  return (
    <div className="agent-card-markdown">
      <Markdown
        skipHtml
        urlTransform={defaultUrlTransform}
        components={{
          a: ({ children, href }) =>
            href ? (
              <a href={href} target="_blank" rel="noreferrer">
                {children}
              </a>
            ) : (
              <>{children}</>
            ),
        }}
      >
        {text}
      </Markdown>
      {item.typing && text.length < item.text.length && (
        <span className="agent-typing-caret" aria-label="正在输入" />
      )}
    </div>
  );
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
    const previousAnswer = answer;
    setAnswer(normalized);
    setSubmitted(true);
    setSubmitting(true);
    try {
      const sent = await onAnswer(item.key, normalized);
      if (!sent) {
        setAnswer(previousAnswer);
        setSubmitted(false);
      }
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

function PlanCard({
  item,
  presentation,
}: {
  item: AgentDisplayItem;
  presentation: 'full' | 'summary';
}) {
  if (presentation === 'summary') {
    const planCount = item.plans?.length ?? 0;
    return (
      <>
        <div className="agent-card-heading">
          <Tag color="blue">观影方案</Tag>
          <strong>{item.title}</strong>
        </div>
        <p className="agent-card-text">
          {planCount > 0
            ? `已生成 ${planCount} 个真实方案，请在左侧方案区选择后继续交流。`
            : '当前条件下暂无可购场次，可以更换日期或城市后重试。'}
        </p>
        {item.relaxationSuggestion && (
          <p className="agent-card-status">可调整条件：{item.relaxationSuggestion}</p>
        )}
      </>
    );
  }
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

function BusinessIntentCard({
  item,
  selectSeatsEnabled,
}: {
  item: AgentDisplayItem;
  selectSeatsEnabled: boolean;
}) {
  return (
    <>
      <div className="agent-card-heading">
        <Tag color="green">场次已确认</Tag>
        <strong>{item.title}</strong>
      </div>
      <p className="agent-card-text">{item.text}</p>
      <CardFields fields={item.fields} />
      {selectSeatsEnabled && item.selectSeatsPath && (
        <Link className="agent-card-primary-link" to={item.selectSeatsPath}>
          去选座
        </Link>
      )}
    </>
  );
}

function TravelAdviceCard({ item }: { item: AgentDisplayItem }) {
  return (
    <>
      <div className="agent-card-heading">
        <Tag color="cyan">出行建议</Tag>
        <strong>{item.title}</strong>
      </div>
      <p className="agent-card-text">{item.text}</p>
      <CardFields fields={item.fields} />
      {item.travelTaskId && (
        <Link
          className="agent-card-primary-link"
          to={`/travel/${encodeURIComponent(item.travelTaskId)}`}
        >
          查看出行建议详情
        </Link>
      )}
    </>
  );
}

function ThinkingBubble({ item }: { item: AgentDisplayItem }) {
  return (
    <div className="agent-thinking" role="status" aria-live="polite">
      <span className="agent-thinking-dots" aria-hidden="true">
        <i />
        <i />
        <i />
      </span>
      <span>
        <strong>思考中</strong>
        <span className="agent-thinking-stage">{item.text}</span>
      </span>
    </div>
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
  planPresentation = 'full',
  selectSeatsEnabled = true,
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
      content = item.confirmation ? (
        <RecommendationCard item={item} />
      ) : (
        <PlanCard item={item} presentation={planPresentation} />
      );
      break;
    case 'travel-advice-card':
      content = <TravelAdviceCard item={item} />;
      break;
    case 'business-intent':
      content = <BusinessIntentCard item={item} selectSeatsEnabled={selectSeatsEnabled} />;
      break;
    case 'thinking':
      content = <ThinkingBubble item={item} />;
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
