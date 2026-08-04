import { defineConfig } from 'umi';

export default defineConfig({
  hash: true,
  proxy: {
    '/api': {
      target: 'http://127.0.0.1:8080',
      changeOrigin: true,
    },
  },
  routes: [
    { path: '/login', component: '@/pages/login' },
    { path: '/register', component: '@/pages/register' },
    { path: '/privacy', component: '@/pages/privacy' },
    { path: '/403', component: '@/pages/forbidden' },
    {
      path: '/admin',
      component: '@/layouts/AdminLayout',
      wrappers: ['@/shared/auth/RequireAdmin'],
      routes: [
        { path: '/admin/', component: '@/pages/admin/dashboard' },
        { path: '/admin/orders', component: '@/pages/admin/orders' },
        { path: '/admin/agent-logs', component: '@/pages/admin/agent-logs' },
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
        {
          path: '/profile',
          component: '@/pages/profile',
          wrappers: ['@/shared/auth/RequireAuth'],
        },
        { path: '*', component: '@/pages/not-found' },
      ],
    },
  ],
  npmClient: 'pnpm',
  utoopack: {},
});
