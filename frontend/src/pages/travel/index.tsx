import { Alert, Button, Card, Checkbox, Descriptions, Empty, Input, Radio, Spin, Tag } from 'antd';
import React, { useEffect, useState } from 'react';
import { history, useParams } from 'umi';
import { formatOrderDateTime, parseOrderDateTime } from '../../modules/order/formatters';
import {
  MAX_MANUAL_PLACE_LENGTH,
  type TravelMode,
  type TravelTaskStatus,
} from '../../modules/travel/types';
import { useTravelTask } from '../../modules/travel/useTravelTask';
import { useTravelRoute } from '../../modules/travel/useTravelRoute';
import { isTravelAdviceAvailable } from '../../modules/travel/advice-availability';
import { formatTravelSource } from '../../modules/travel/source-labels';
import {
  ORDERS_BREADCRUMB_ITEM,
  PROFILE_BREADCRUMB_ITEM,
  TransactionBreadcrumb,
} from '../../features/transaction-breadcrumb/TransactionBreadcrumb';
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

type RouteOriginChoice = 'CURRENT_LOCATION' | 'MANUAL_PLACE';

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
  const travelRoute = useTravelRoute(taskId);
  const [triggerAt, setTriggerAt] = useState('');
  const [inputNotice, setInputNotice] = useState<string | null>(null);
  const [travelMode, setTravelMode] = useState<TravelMode>('DRIVING');
  const [routeOrigin, setRouteOrigin] = useState<RouteOriginChoice>('CURRENT_LOCATION');
  const [manualPlaceText, setManualPlaceText] = useState('');
  const [sharingConfirmed, setSharingConfirmed] = useState(false);

  useEffect(() => {
    setTriggerAt(inputDateTime(travel.task?.triggerAt ?? null));
  }, [travel.task?.triggerAt]);

  useEffect(() => {
    setTravelMode('DRIVING');
    setRouteOrigin('CURRENT_LOCATION');
    setManualPlaceText('');
    setSharingConfirmed(false);
  }, [taskId]);

  const breadcrumb = (
    <TransactionBreadcrumb
      items={
        travel.task?.order.orderNo
          ? [
              PROFILE_BREADCRUMB_ITEM,
              ORDERS_BREADCRUMB_ITEM,
              {
                label: '订单详情',
                to: `/orders/${encodeURIComponent(travel.task.order.orderNo)}`,
              },
              { label: '出行建议' },
            ]
          : [PROFILE_BREADCRUMB_ITEM, ORDERS_BREADCRUMB_ITEM, { label: '出行建议' }]
      }
    />
  );

  if (travel.isLoading) {
    return (
      <main className="travel-page" aria-busy="true">
        {breadcrumb}
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
        {breadcrumb}
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
        {breadcrumb}
        <Empty description="暂无可查看的出行任务" />
      </main>
    );
  }

  const { task, advice } = travel;
  const isAdviceGenerationPending = !isTravelAdviceAvailable(task.order.showStartTime);

  if (!advice && isAdviceGenerationPending) {
    return (
      <main className="travel-page">
        {breadcrumb}
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
        {breadcrumb}
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

  const handlePlanRoute = async () => {
    const outcome =
      routeOrigin === 'CURRENT_LOCATION'
        ? await travelRoute.plan(travelMode, sharingConfirmed)
        : await travelRoute.planFromManualPlace(manualPlaceText, travelMode, sharingConfirmed);
    if (routeOrigin === 'MANUAL_PLACE') setManualPlaceText('');
    setSharingConfirmed(false);
    if (outcome === 'task-unavailable') await travel.reload();
  };

  const handleRouteOriginChange = (nextOrigin: RouteOriginChoice) => {
    setRouteOrigin(nextOrigin);
    if (nextOrigin === 'CURRENT_LOCATION') setManualPlaceText('');
  };

  return (
    <main className="travel-page">
      {breadcrumb}
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
            <Descriptions.Item label="内容来源">
              {formatTravelSource(task.cinema.source, 'CONTENT')}
            </Descriptions.Item>
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

      <Card title="规划前往影院的路线">
        <p className="travel-route-disclosure">
          {routeOrigin === 'CURRENT_LOCATION'
            ? '规划时会把本次浏览器定位的经纬度发送给高德路线服务，仅用于本次请求；页面不会保存当前位置。'
            : '规划时会把本次输入地点发送给高德路线服务，仅用于本次请求；页面不会保存该地点。'}
        </p>
        <div className="travel-route-controls">
          <Radio.Group
            aria-label="出行方式"
            value={travelMode}
            disabled={isReadOnly || travelRoute.isPlanning}
            onChange={(event) => setTravelMode(event.target.value as TravelMode)}
            options={[
              { label: '驾车', value: 'DRIVING' },
              { label: '步行', value: 'WALKING' },
            ]}
          />
          <Radio.Group
            aria-label="出发位置方式"
            value={routeOrigin}
            disabled={isReadOnly || travelRoute.isPlanning}
            onChange={(event) => handleRouteOriginChange(event.target.value as RouteOriginChoice)}
            options={[
              { label: '当前位置', value: 'CURRENT_LOCATION' },
              { label: '手动输入地点', value: 'MANUAL_PLACE' },
            ]}
          />
          {routeOrigin === 'MANUAL_PLACE' && (
            <label className="travel-manual-place-field" htmlFor="travel-manual-place">
              出发地点
              <Input
                id="travel-manual-place"
                value={manualPlaceText}
                maxLength={MAX_MANUAL_PLACE_LENGTH}
                disabled={isReadOnly || travelRoute.isPlanning}
                placeholder="例如：长沙市雨花区万家丽中路 1 号"
                onChange={(event) => setManualPlaceText(event.target.value)}
              />
            </label>
          )}
          <Checkbox
            checked={sharingConfirmed}
            disabled={isReadOnly || travelRoute.isPlanning}
            onChange={(event) => setSharingConfirmed(event.target.checked)}
          >
            我确认将本次{routeOrigin === 'CURRENT_LOCATION' ? '当前位置' : '输入地点'}
            发送给高德路线服务
          </Checkbox>
          <Button
            type="primary"
            loading={travelRoute.isPlanning}
            disabled={
              isReadOnly ||
              !sharingConfirmed ||
              travelRoute.isPlanning ||
              (routeOrigin === 'MANUAL_PLACE' && manualPlaceText.trim().length === 0)
            }
            onClick={() => void handlePlanRoute()}
          >
            规划路线
          </Button>
        </div>
        {travelRoute.notice && (
          <Alert
            className="travel-route-notice"
            showIcon
            type="warning"
            message={travelRoute.notice}
          />
        )}
        {travelRoute.route && (
          <div className="travel-route-result" aria-live="polite">
            <Descriptions column={1} size="small">
              <Descriptions.Item label="出行方式">
                {travelRoute.route.travelMode === 'DRIVING' ? '驾车' : '步行'}
              </Descriptions.Item>
              <Descriptions.Item label="预计耗时">
                {travelRoute.route.durationMinutes} 分钟
              </Descriptions.Item>
              <Descriptions.Item label="预计出发时间">
                {formatOrderDateTime(travelRoute.route.suggestedDepartureAt)}
              </Descriptions.Item>
              <Descriptions.Item label="路线来源">
                {formatTravelSource(travelRoute.route.source, 'ROUTE')}
              </Descriptions.Item>
              <Descriptions.Item label="数据时间">
                {formatOrderDateTime(travelRoute.route.dataTime)}
              </Descriptions.Item>
              <Descriptions.Item label="有效期">
                {formatOrderDateTime(travelRoute.route.expiresAt)}
              </Descriptions.Item>
            </Descriptions>
            {(travelRoute.route.degraded || travelRoute.route.isExpired) && (
              <Tag color="warning">
                {travelRoute.route.isExpired ? '路线结果已过期' : '当前为降级路线结果'}
              </Tag>
            )}
          </div>
        )}
      </Card>

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
          来源：{formatTravelSource(advice.source, 'WEATHER')} · 数据时间：
          {formatOrderDateTime(advice.dataAt, '暂不可用')} · 有效期：
          {formatOrderDateTime(advice.expiresAt, '暂不可用')}
        </p>
      </Card>
    </main>
  );
}
