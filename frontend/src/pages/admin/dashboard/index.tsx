import { Alert, Button, Empty, Input, Spin, Table, Tag, message } from 'antd';
import { useState } from 'react';

import { useAdminContentSync } from '../../../modules/admin-content/hooks';
import './index.css';

const format = (value: string | null) =>
  value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '—';

export default function AdminContentPage() {
  const [cityName, setCityName] = useState('长沙');
  const state = useAdminContentSync();
  const submitContentSync = async () => {
    const task = await state.submit(cityName.trim());
    if (task !== null) {
      message.success('已触发内容同步指令');
    }
  };
  return (
    <div className="admin-dashboard-container">
      <h1 className="page-title">内容同步状态</h1>
      <section className="dashboard-panel" aria-labelledby="content-sync-title">
        <h2 id="content-sync-title" className="panel-header">
          真实内容源
        </h2>
        {state.error && (
          <Alert
            type={state.error.status === 403 ? 'warning' : 'error'}
            message={state.error.status === 403 ? '无管理权限' : '内容状态获取失败'}
            description={state.error.message}
            action={
              <Button size="small" onClick={() => void state.refresh()}>
                重试
              </Button>
            }
          />
        )}
        {state.resultUnknown && (
          <Alert
            type="warning"
            message="同步结果未知"
            description="网络响应未收到，请按原请求标识查询结果，不会重新发起同步。"
            action={
              <Button size="small" loading={state.submitting} onClick={() => void state.recover()}>
                查询结果
              </Button>
            }
          />
        )}
        <div className="content-sync-action">
          <Input
            aria-label="同步城市"
            value={cityName}
            maxLength={64}
            onChange={(event) => setCityName(event.target.value)}
          />
          <Button
            type="primary"
            loading={state.submitting}
            disabled={state.resultUnknown || state.isTaskInProgress || cityName.trim().length === 0}
            onClick={() => void submitContentSync()}
          >
            手动同步
          </Button>
        </div>
        {state.task && (
          <Alert
            type="info"
            showIcon
            message={`同步任务：${state.task.status}`}
            description={`成功 ${state.task.successCount} 条，失败 ${state.task.failureCount} 条${state.task.failureCategory ? `；${state.task.failureCategory}` : ''}`}
          />
        )}
        {state.loading && (
          <div className="content-sync-loading">
            <Spin />
          </div>
        )}
        {!state.loading && state.sources?.length === 0 && (
          <Empty description="暂无内容源同步记录" />
        )}
        {!state.loading && state.sources && state.sources.length > 0 && (
          <Table
            rowKey={(item) => `${item.provider}-${item.resourceType}-${item.cityName}`}
            scroll={{ x: 980 }}
            pagination={false}
            dataSource={state.sources}
            columns={[
              { title: '来源', render: (_, item) => `${item.provider} / ${item.resourceType}` },
              { title: '城市', dataIndex: 'cityName' },
              { title: '许可说明', dataIndex: 'licenseNotice', width: 240 },
              { title: '最近成功时间', render: (_, item) => format(item.lastSuccessAt) },
              {
                title: '有效期',
                render: (_, item) => (
                  <Tag color={item.isExpired ? 'error' : 'success'}>
                    {item.isExpired ? '已过期' : format(item.expiresAt)}
                  </Tag>
                ),
              },
              {
                title: '数据量',
                render: (_, item) => `成功 ${item.successCount} / 失败 ${item.failureCount}`,
              },
              { title: '同步状态', render: (_, item) => <Tag>{item.status}</Tag> },
              { title: '错误信息', render: (_, item) => item.failureCategory ?? '—' },
            ]}
          />
        )}
      </section>
    </div>
  );
}
