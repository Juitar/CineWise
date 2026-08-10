import React from 'react';
import { Link } from 'umi';
import './index.css';

export interface TransactionBreadcrumbItem {
  /** 显示给用户的业务层级名称。 */
  label: string;
  /** 上级页面的规范业务路径；缺省表示当前页面，不渲染为链接。 */
  to?: string;
}

export interface TransactionBreadcrumbProps {
  items: TransactionBreadcrumbItem[];
}

/** 个人订单及其后续页面统一使用的个人中心根层级。 */
export const PROFILE_BREADCRUMB_ITEM: TransactionBreadcrumbItem = {
  label: '个人中心',
  to: '/profile',
};

/** 订单详情、支付、电子票、退票和出行建议统一使用的订单列表层级。 */
export const ORDERS_BREADCRUMB_ITEM: TransactionBreadcrumbItem = {
  label: '我的订单',
  to: '/orders',
};

/**
 * A 交易页复用 C 的原生语义化面包屑模式。
 *
 * <p>路径由页面容器根据权威业务 ID 组装，组件只负责渲染；当前项不生成链接，避免把面包屑
 * 错当成浏览器历史返回或重复提交交易操作。</p>
 */
export const TransactionBreadcrumb: React.FC<TransactionBreadcrumbProps> = ({ items }) => {
  return (
    <nav aria-label="面包屑" className="transaction-breadcrumb">
      {items.map((item, index) => (
        <React.Fragment key={`${item.label}-${item.to ?? 'current'}-${index}`}>
          {index > 0 && <span aria-hidden="true"> / </span>}
          {item.to ? (
            <Link to={item.to}>{item.label}</Link>
          ) : (
            <span className="current">{item.label}</span>
          )}
        </React.Fragment>
      ))}
    </nav>
  );
};

export default TransactionBreadcrumb;
