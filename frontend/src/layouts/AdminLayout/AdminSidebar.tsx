import React from 'react';
import { Menu } from 'antd';
import { Link, history, useLocation } from 'umi';
import { BrandLogoIcon } from '../../shared/components/icons';
import { ActivityIcon, DashboardIcon, OrderIcon } from '../../shared/components/icons/layout-icons';
import './index.css';

export function AdminSidebar() {
  const location = useLocation();
  const selectedKey = location.pathname === '/admin' ? '/admin/content' : location.pathname;

  const menuItems = [
    {
      key: '/admin/content',
      icon: <DashboardIcon size={18} />,
      label: '工作台',
    },
    {
      key: '/admin/orders',
      icon: <OrderIcon size={18} />,
      label: '订单管理',
    },
    {
      key: '/admin/agent-runs',
      icon: <ActivityIcon size={18} />,
      label: 'Agent轨迹',
    },
  ];

  return (
    <aside className="admin-sidebar">
      <div className="admin-sidebar-logo">
        <Link to="/admin/content" className="brand-logo-link">
          <BrandLogoIcon size={32} />
          <span className="brand-logo-text">
            妙语购票 <span className="admin-badge">管理端</span>
          </span>
        </Link>
      </div>

      <div className="admin-sidebar-menu-wrapper">
        <Menu
          mode="inline"
          selectedKeys={[selectedKey]}
          className="admin-sidebar-menu"
          items={menuItems}
          onClick={({ key }) => history.push(key)}
        />
      </div>
    </aside>
  );
}
