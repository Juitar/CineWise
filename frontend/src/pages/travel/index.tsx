import { Alert, Button, Card, Descriptions, Empty, Spin, Tag } from 'antd';
import React, { useEffect, useState } from 'react';
import { history, useParams } from 'umi';
import { formatOrderDateTime, parseOrderDateTime } from '../../modules/order/formatters';
import type { TravelTaskStatus } from '../../modules/travel/types';
import { useTravelTask } from '../../modules/travel/useTravelTask';
import { isTravelAdviceAvailable } from '../../modules/travel/advice-availability';
import './index.css';

const STATUS_LABELS: Record<TravelTaskStatus, string> = {
  PENDING: '等待生成建议',
  GENERATING: '正在生成建议',
  READY: '建议已生成',
  NOTIFIED: '提醒已发送',
  COMPLETED: '观影已结束',
  CANCELLED: '任务已取消',
  FAILED: '任务处理失败',
};

const FALLBACK_LABELS: Record<string, string> = {
  DEMO: '当前展示已标明的演示天气，请出发前自行确认',
  NO_WEATHER: '天气暂不可用，通用交通建议仍可查看',
};

function inputDateTime(value: string | null): string {
  return value?.slice(0, 16) ?? '';
}

function businessDateTime(value: string): string | null {
  const candidate = `${value}:00+08:00`;
  return parseOrderDateTime(candidate) ? candidate : null;
}

function statusColor(status: TravelTaskStatus): string {
  if (status === 'READY' || status === 'NOTIFIED') return 'success';
  if (status === 'PENDING' || status === 'GENERATING') return 'processing';
  return 'default';
}

export default function TravelPage() {
  const { taskId = '' } = useParams<{ taskId: string }>();
  const travel = useTravelTask(taskId);
  const [triggerAt, setTriggerAt] = useState('');
  const [inputNotice, setInputNotice] = useState<string | null>(null);

  useEffect(() => {
    setTriggerAt(inputDateTime(travel.task?.triggerAt ?? null));
  }, [travel.task?.triggerAt]);

  if (travel.isLoading) {
    return (
      <main className="travel-page" aria-busy="true">
        <div className="travel-loading">
          <Spin size="large" />
          <span>正在加载出行建议</span>
        </div>
      </main>
    );
  }

  if (travel.error) {
    const missing = travel.error.status === 404 || travel.error.code === 207001;
    return (
      <main className="travel-page">
        <Alert
          type="error"
          showIcon
          message={missing ? '出行任务不存在或无访问权限' : '出行建议加载失败'}
          description={
            missing ? '请从本人订单重新进入，或稍后确认任务是否已经建立。' : travel.error.message
          }
          action={
            <Button onClick={missing ? () => history.push('/orders') : () => void travel.reload()}>
              {missing ? '返回订单' : '重新加载'}
            </Button>
          }
        />
      </main>
    );
  }

  if (!travel.task) {
    return (
      <main className="travel-page">
        <Empty description="暂无可查看的出行任务" />
      </main>
    );
  }

  const { task, advice } = travel;
  const isAdviceGenerationPending = !isTravelAdviceAvailable(task.order.showStartTime);

  if (!advice && isAdviceGenerationPending) {
    return (
      <main className="travel-page">
        <header className="travel-header">
          <div>
            <h1>观影出行建议</h1>
            <p>
              {task.movie.title} · {task.cinema.name}
            </p>
          </div>
          <Tag color={statusColor(task.status)}>{STATUS_LABELS[task.status]}</Tag>
        </header>
        <Card title="出行建议">
          <Empty description="出行建议将在开场前 2 小时生成，请稍后查看" />
        </Card>
      </main>
    );
  }

  if (!advice) {
    return (
      <main className="travel-page">
        <Empty description="出行建议暂不可用，请稍后重试" />
      </main>
    );
  }

  const isTerminal = ['CANCELLED', 'COMPLETED', 'FAILED'].includes(task.status);
  const isReadOnly = isTerminal || advice.isExpired;

  const handleSaveReminder = async () => {
    const nextTriggerAt = businessDateTime(triggerAt);
    if (!nextTriggerAt) {
      setInputNotice('请选择有效的提醒时间');
      return;
    }
    setInputNotice(null);
    await travel.updateReminder({ triggerAt: nextTriggerAt, version: task.version });
  };

  return (
    <main className="travel-page">
      <header className="travel-header">
        <div>
          <h1>观影出行建议</h1>
          <p>
            {task.movie.title} · {task.cinema.name}
          </p>
        </div>
        <Tag color={statusColor(task.status)}>{STATUS_LABELS[task.status]}</Tag>
      </header>

      {isReadOnly && (
        <Alert showIcon type="warning" message="该出行任务已失效，仅保留已有信息供参考" />
      )}
      {advice.degraded && (
        <Alert
          showIcon
          type="info"
          message="部分动态数据暂不可用"
          description={
            advice.fallbackType
              ? (FALLBACK_LABELS[advice.fallbackType] ?? '请根据页面标明的来源和时间自行确认')
              : '请根据页面标明的来源和时间自行确认'
          }
        />
      )}
      {travel.notice && <Alert showIcon type="info" message={travel.notice} />}
      {inputNotice && <Alert showIcon type="warning" message={inputNotice} />}

      <section className="travel-grid">
        <Card title="观影与影院">
          <Descriptions column={1} size="small">
            <Descriptions.Item label="影片">{task.movie.title}</Descriptions.Item>
            <Descriptions.Item label="开场时间">
              {formatOrderDateTime(task.order.showStartTime)}
            </Descriptions.Item>
            <Descriptions.Item label="影院">{task.cinema.name}</Descriptions.Item>
            <Descriptions.Item label="区域">
              {task.cinema.area ?? '区域信息暂不可用'}
            </Descriptions.Item>
            <Descriptions.Item label="地址">
              {task.cinema.address ?? '地址信息暂不可用'}
            </Descriptions.Item>
            <Descriptions.Item label="内容来源">{task.cinema.source}</Descriptions.Item>
          </Descriptions>
        </Card>

        <Card title="邮件提醒时间">
          <p className="travel-current-reminder">
            当前提醒：{formatOrderDateTime(task.triggerAt, '尚未设置提醒时间')}
          </p>
          <label className="travel-reminder-field" htmlFor="travel-trigger-at">
            修改提醒时间
            <input
              id="travel-trigger-at"
              type="datetime-local"
              value={triggerAt}
              disabled={isReadOnly || travel.isUpdatingReminder || travel.isReminderResultUnknown}
              onChange={(event) => setTriggerAt(event.target.value)}
            />
          </label>
          <div className="travel-actions">
            <Button
              type="primary"
              disabled={isReadOnly || travel.isUpdatingReminder || travel.isReminderResultUnknown}
              loading={travel.isUpdatingReminder}
              onClick={() => void handleSaveReminder()}
            >
              更新提醒时间
            </Button>
            {travel.isReminderResultUnknown && (
              <Button onClick={() => void travel.reload()}>重新查询任务</Button>
            )}
          </div>
          <p className="travel-channel-note">提醒渠道为认证邮箱，本期不申请浏览器通知权限。</p>
        </Card>
      </section>

      <Card
        title="天气与通用交通建议"
        extra={
          <Button
            disabled={isReadOnly || isAdviceGenerationPending || travel.isRefreshing}
            loading={travel.isRefreshing}
            onClick={() => void travel.refresh()}
          >
            刷新建议
          </Button>
        }
      >
        {!advice.available ? (
          <Empty description="出行建议将在开场前 2 小时生成，请稍后查看" />
        ) : (
          <div className="travel-advice-content">
            {advice.weather ? (
              <Descriptions column={1} size="small">
                <Descriptions.Item label="天气区域">
                  {advice.weather.area ?? '区域信息暂不可用'}
                </Descriptions.Item>
                <Descriptions.Item label="天气">
                  {advice.weather.condition ?? '天气信息暂不可用'}
                </Descriptions.Item>
                <Descriptions.Item label="风险提示">
                  {advice.weather.risk ?? '暂无风险提示'}
                </Descriptions.Item>
              </Descriptions>
            ) : (
              <p>天气信息暂不可用，以下通用交通建议仍可参考。</p>
            )}
            {advice.advice.length > 0 ? (
              <ul className="travel-advice-list">
                {advice.advice.map((item, index) => (
                  <li key={`${item.type}-${index}`}>{item.text}</li>
                ))}
              </ul>
            ) : (
              <Empty description="暂无可展示的建议" />
            )}
          </div>
        )}
        <p className="travel-freshness">
          来源：{advice.source ?? '暂不可用'} · 数据时间：
          {formatOrderDateTime(advice.dataAt, '暂不可用')} · 有效期：
          {formatOrderDateTime(advice.expiresAt, '暂不可用')}
        </p>
      </Card>
    </main>
  );
}
