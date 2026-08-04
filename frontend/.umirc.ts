import { defineConfig } from 'umi';

export default defineConfig({
  proxy: {
    '/api': {
      target: 'http://127.0.0.1:8080',
      changeOrigin: true,
    },
  },
  routes: [
    { path: '/login', component: '@/pages/login' },
    { path: '/admin/login', component: '@/pages/login' },
    { path: '/register', component: '@/pages/register' },
    { path: '/privacy', component: '@/pages/privacy' },
    {
      path: '/admin',
      component: '@/layouts/AdminLayout',
      routes: [
        { path: '/admin/content', component: '@/pages/admin/dashboard' },
        { path: '/admin/orders', component: '@/pages/admin/orders' },
        { path: '/admin/agent-runs', component: '@/pages/admin/agent-logs' },
        { path: '*', component: '@/pages/not-found' },
      ],
    },
    {
      path: '/',
      component: '@/layouts/UserLayout',
      routes: [
        { path: '/', component: '@/pages/home' },
        { path: '/movies', component: '@/pages/movies' },
        { path: '/cinemas', component: '@/pages/cinemas' },
        { path: '/profile', component: '@/pages/profile' },
        { path: '*', component: '@/pages/not-found' },
      ],
    },
  ],
  npmClient: 'pnpm',
  utoopack: {},
});
