import React, { useState } from 'react';
import { Alert, Button, Card, DatePicker, Form, Result, Select, Space, Typography } from 'antd';
import dayjs from 'dayjs';
import { useCinemaList } from '../../modules/content/useCinemaList';
import { ApiError } from '../../shared/api/ApiError';
import {
  importExternalShowtimeReferences,
  type ExternalShowtimeImportResponse,
} from '../../modules/ticketing/adminImport';
import './index.css';

/** 受控导入面板只提交管理员明确选择的范围，不在浏览器自动拉取或自动重试。 */
export function ExternalShowtimeImportPanel() {
  const [form] = Form.useForm<{ cinemaIds: string[]; showDate: dayjs.Dayjs }>();
  const cinemasQuery = useCinemaList({ location: '430100', page: 1, size: 50 });
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [result, setResult] = useState<ExternalShowtimeImportResponse | null>(null);
  const [error, setError] = useState<ApiError | null>(null);

  const handleSubmit = async () => {
    const values = await form.validateFields();
    setIsSubmitting(true);
    setResult(null);
    setError(null);
    try {
      setResult(
        await importExternalShowtimeReferences({
          cinemaIds: values.cinemaIds,
          showDate: values.showDate.format('YYYY-MM-DD'),
        }),
      );
    } catch (requestError: unknown) {
      setError(
        requestError instanceof ApiError
          ? requestError
          : new ApiError('导入请求失败', { kind: 'INVALID_RESPONSE' }),
      );
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <section aria-labelledby="external-showtime-import-title">
      <Card title={<span id="external-showtime-import-title">外部排期沙箱导入</span>}>
        <Typography.Paragraph type="secondary">
          开场参考来自 D；影厅、座位、库存与票价由 A 本地沙箱生成，不代表外部真实库存。
        </Typography.Paragraph>
        <Form form={form} layout="vertical" initialValues={{ showDate: dayjs() }}>
          <Form.Item
            label="场次日期"
            name="showDate"
            rules={[{ required: true, message: '请选择场次日期' }]}
          >
            <DatePicker allowClear={false} className="external-showtime-import-date" />
          </Form.Item>
          <Form.Item
            label="影院"
            name="cinemaIds"
            rules={[{ required: true, message: '请选择至少一家影院' }]}
          >
            <Select
              allowClear
              disabled={cinemasQuery.isLoading || cinemasQuery.error !== null}
              loading={cinemasQuery.isLoading || cinemasQuery.isRefreshing}
              mode="multiple"
              optionFilterProp="label"
              placeholder="搜索并选择影院名称，最多 50 家"
              options={(cinemasQuery.data?.records ?? []).map((cinema) => ({
                label: cinema.address ? `${cinema.name}（${cinema.address}）` : cinema.name,
                value: cinema.cinemaId,
              }))}
              showSearch
            />
          </Form.Item>
          {cinemasQuery.error ? (
            <Alert
              showIcon
              type="warning"
              message="影院列表暂不可用，请稍后重试"
              description={
                cinemasQuery.error.traceId ? `TraceId: ${cinemasQuery.error.traceId}` : undefined
              }
            />
          ) : null}
          <Button loading={isSubmitting} type="primary" onClick={handleSubmit}>
            导入本地沙箱场次
          </Button>
        </Form>
        {error ? (
          <Alert
            className="external-showtime-import-feedback"
            showIcon
            type="error"
            message={error.status === 403 ? '无权执行导入' : error.message}
            description={error.traceId ? `TraceId: ${error.traceId}` : undefined}
          />
        ) : null}
        {result ? (
          <Result
            className="external-showtime-import-result"
            status={result.truncated ? 'warning' : result.showIds.length === 0 ? 'info' : 'success'}
            title={
              result.truncated
                ? '候选结果被截断，未执行导入'
                : result.showIds.length === 0
                  ? '该日期和影院没有可导入场次'
                  : `已处理 ${result.showIds.length} 个本地场次`
            }
            subTitle={
              result.truncated
                ? '请缩小影院范围后重新提交。'
                : result.showIds.length === 0
                  ? '请尝试未来 1～7 天的日期，或选择其他影院。'
                  : undefined
            }
            extra={
              <Space wrap>
                {result.showIds.map((showId) => (
                  <Typography.Text code key={showId}>
                    {showId}
                  </Typography.Text>
                ))}
              </Space>
            }
          />
        ) : null}
      </Card>
    </section>
  );
}
