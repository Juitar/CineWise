import { Button, DatePicker, Input, Pagination, Select, Table } from 'antd';
import React, { useState } from 'react';
import './index.css';

const { RangePicker } = DatePicker;

export default function AdminOrdersPage() {
  const [data] = useState([
    {
      key: '1',
      orderId: 'MO202505220001',
      user: 'u***@example.com',
      movie: '流浪地球2',
      cinema: '杭州UME影城 (西湖店)',
      showtime: '2025-05-22 19:30 2号厅',
      amount: '¥78.00',
      status: '待支付',
      time: '2025-05-22 10:18:22',
    },
    {
      key: '2',
      orderId: 'MO202505220002',
      user: 'm***@example.com',
      movie: '哈尔的移动城堡',
      cinema: '万达影城 (武林广场店)',
      showtime: '2025-05-22 20:15 3号厅',
      amount: '¥56.00',
      status: '已完成',
      time: '2025-05-22 10:17:45',
    },
    {
      key: '3',
      orderId: 'MO202505220003',
      user: 'c***@example.com',
      movie: '复仇者联盟4',
      cinema: 'CGV影城 (滨江店)',
      showtime: '2025-05-22 21:30 IMAX厅',
      amount: '¥128.00',
      status: '已取消',
      time: '2025-05-22 10:16:03',
    },
    {
      key: '4',
      orderId: 'MO202505220004',
      user: 'g***@example.com',
      movie: '灌篮高手',
      cinema: 'SFC上影影城 (庆春店)',
      showtime: '2025-05-22 18:45 1号厅',
      amount: '¥62.00',
      status: '处理中',
      time: '2025-05-22 10:14:21',
    },
  ]);

  const renderStatus = (status: string) => {
    switch (status) {
      case '待支付':
        return <span className="order-status-tag status-purple">待支付</span>;
      case '已完成':
        return <span className="order-status-tag status-green">已完成</span>;
      case '已取消':
        return <span className="order-status-tag status-blue">已取消</span>;
      case '处理中':
        return <span className="order-status-tag status-orange">处理中</span>;
      default:
        return <span>{status}</span>;
    }
  };

  const columns = [
    { title: '订单号', dataIndex: 'orderId', key: 'orderId' },
    { title: '用户', dataIndex: 'user', key: 'user' },
    { title: '影片', dataIndex: 'movie', key: 'movie' },
    { title: '影院', dataIndex: 'cinema', key: 'cinema' },
    { title: '场次', dataIndex: 'showtime', key: 'showtime' },
    { title: '金额', dataIndex: 'amount', key: 'amount' },
    { title: '状态', dataIndex: 'status', key: 'status', render: renderStatus },
    { title: '下单时间', dataIndex: 'time', key: 'time' },
  ];

  return (
    <div className="admin-orders-page">
      <nav className="admin-breadcrumb" aria-label="面包屑">
        <span>管理端</span>
        <span aria-hidden="true">&gt;</span>
        <span className="current">订单管理</span>
      </nav>

      <div className="orders-filter-card">
        <div className="filter-grid">
          <div className="filter-item">
            <div className="filter-label">订单号</div>
            <Input placeholder="请输入订单号" />
          </div>
          <div className="filter-item">
            <div className="filter-label">用户关键词</div>
            <Input placeholder="请输入脱敏邮箱或用户标识" />
          </div>
          <div className="filter-item">
            <div className="filter-label">影片名称</div>
            <Input placeholder="请输入影片名称" />
          </div>
          <div className="filter-item">
            <div className="filter-label">状态</div>
            <Select defaultValue="全部状态" className="filter-control-full-width">
              <Select.Option value="全部状态">全部状态</Select.Option>
              <Select.Option value="待支付">待支付</Select.Option>
              <Select.Option value="已完成">已完成</Select.Option>
            </Select>
          </div>
          <div className="filter-item filter-date">
            <div className="filter-label">下单时间</div>
            <RangePicker className="filter-control-full-width" />
          </div>
        </div>
        <div className="filter-actions">
          <Button>重置</Button>
          <Button type="primary">查询</Button>
        </div>
      </div>

      <div className="orders-table-card">
        <Table columns={columns} dataSource={data} pagination={false} className="admin-ant-table" />
        <div className="orders-pagination">
          <div className="total-text">共 120 条</div>
          <Pagination defaultCurrent={1} total={120} showSizeChanger />
        </div>
      </div>
    </div>
  );
}
