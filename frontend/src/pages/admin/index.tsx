import { Navigate } from 'umi';

/** 管理端根路径只负责进入正式内容工作台，不承载另一份页面状态。 */
export default function AdminIndexPage() {
  return <Navigate replace to="/admin/content" />;
}
