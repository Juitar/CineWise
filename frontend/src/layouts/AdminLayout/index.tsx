import React from 'react';
import { Outlet } from 'umi';
import { AdminSidebar } from './AdminSidebar';
import { AdminTopBar } from './AdminTopBar';
import './index.css';

export default function AdminLayout() {
  return (
    <div className="admin-layout">
      <AdminSidebar />
      <div className="admin-layout-main-column">
        <AdminTopBar />
        <main className="admin-layout-content">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
