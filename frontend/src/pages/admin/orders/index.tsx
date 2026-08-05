import React, { useMemo, useState } from 'react';
import { AdminOrderDetailDrawer } from '../../../features/admin-order-detail/AdminOrderDetailDrawer';
import {
  AdminOrderFilters,
  type AdminOrderFiltersParams,
} from '../../../features/admin-order-filters/AdminOrderFilters';
import { AdminOrderList } from '../../../features/admin-order-list/AdminOrderList';
import { AdminOrderError } from '../../../features/admin-order/components/AdminOrderError';
import {
  resolveAdminDetailError,
  resolveAdminOrderErrorState,
} from '../../../modules/admin/errors';
import { useAdminOrderDetail, useAdminOrders } from '../../../modules/admin/hooks';
import type { AdminOrderListQuery } from '../../../modules/admin/types';
import './index.css';

export default function AdminOrdersPage() {
  // 分页状态
  const [page, setPage] = useState(1);
  const [size, setSize] = useState(20);
  const [filters, setFilters] = useState<AdminOrderFiltersParams>({});

  // 抽屉状态
  const [detailOpen, setDetailOpen] = useState(false);
  const [selectedOrderNo, setSelectedOrderNo] = useState<string | null>(null);

  const query = useMemo<AdminOrderListQuery>(
    () => ({ ...filters, page, size }),
    [filters, page, size],
  );
  const ordersQuery = useAdminOrders(query);
  const detailQuery = useAdminOrderDetail(selectedOrderNo);
  const listErrorState = ordersQuery.error ? resolveAdminOrderErrorState(ordersQuery.error) : null;
  const detailError = detailQuery.error ? resolveAdminDetailError(detailQuery.error) : null;

  const handleSearch = (nextFilters: AdminOrderFiltersParams) => {
    setFilters(nextFilters);
    setPage(1);
  };

  const handleReset = () => {
    setFilters({});
    setPage(1);
  };

  const handlePageChange = (newPage: number, newSize: number) => {
    setPage(newPage);
    setSize(newSize);
  };

  const handleViewDetail = (orderNo: string) => {
    setDetailOpen(true);
    setSelectedOrderNo(orderNo);
  };

  const closeDetail = () => {
    setDetailOpen(false);
    setSelectedOrderNo(null);
  };

  const showStaleList =
    ordersQuery.data !== null &&
    (listErrorState === 'DIRECTORY_UNAVAILABLE' || listErrorState === 'GENERAL_ERROR');
  const showList = listErrorState === null || showStaleList;

  return (
    <div className="admin-orders-page">
      <nav className="admin-breadcrumb" aria-label="面包屑">
        <span>管理端</span>
        <span aria-hidden="true">&gt;</span>
        <span className="current">订单管理</span>
      </nav>

      <AdminOrderFilters
        loading={ordersQuery.isLoading || ordersQuery.isRefreshing}
        onSearch={handleSearch}
        onReset={handleReset}
      />

      {listErrorState && ordersQuery.error ? (
        <AdminOrderError
          onRetry={ordersQuery.retry}
          state={listErrorState}
          traceId={ordersQuery.error.traceId}
        />
      ) : null}

      {showList ? (
        <AdminOrderList
          orders={ordersQuery.data?.records ?? []}
          total={ordersQuery.data?.total ?? 0}
          page={ordersQuery.data?.page ?? page}
          size={ordersQuery.data?.size ?? size}
          loading={ordersQuery.isLoading || ordersQuery.isRefreshing}
          onPageChange={handlePageChange}
          onViewDetail={handleViewDetail}
        />
      ) : null}

      <AdminOrderDetailDrawer
        canRetry={detailError?.canRetry}
        detail={detailQuery.data}
        errorMessage={detailError?.message}
        loading={
          detailQuery.isLoading ||
          (selectedOrderNo !== null && detailQuery.data === null && detailQuery.error === null)
        }
        onClose={closeDetail}
        onRetry={detailQuery.retry}
        open={detailOpen}
        traceId={detailQuery.error?.traceId}
      />
    </div>
  );
}
