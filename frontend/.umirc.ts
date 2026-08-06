import { defineConfig } from 'umi';

export default defineConfig({
  hash: true,
  proxy: {
    '/api': {
      target: process.env.CINEWISE_API_PROXY_TARGET ?? 'http://127.0.0.1:8080',
      changeOrigin: true,
    },
  },
  routes: [
    { path: '/login', component: '@/pages/login' },
    { path: '/register', component: '@/pages/register' },
    { path: '/password/reset', component: '@/pages/password/reset' },
    { path: '/privacy', component: '@/pages/privacy' },
    { path: '/403', component: '@/pages/forbidden' },
    {
      path: '/admin',
      component: '@/layouts/AdminLayout',
      wrappers: ['@/shared/auth/RequireAdmin'],
      routes: [
        { path: '/admin/', component: '@/pages/admin' },
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
        { path: '/cinemas/:cinemaId', component: '@/pages/cinemas/detail' },
        { path: '/shows', component: '@/pages/shows' },
        {
          path: '/assistant',
          component: '@/pages/assistant',
          wrappers: ['@/shared/auth/RequireAuth'],
        },
        {
          path: '/assistant/:sessionId',
          component: '@/pages/assistant',
          wrappers: ['@/shared/auth/RequireAuth'],
        },
        {
          path: '/shows/:showId/seats',
          component: '@/pages/seats',
          wrappers: ['@/shared/auth/RequireAuth'],
        },
        {
          path: '/orders/confirm',
          component: '@/pages/orders/confirm',
          wrappers: ['@/shared/auth/RequireAuth'],
        },
        {
          path: '/orders',
          component: '@/pages/orders',
          wrappers: ['@/shared/auth/RequireAuth'],
        },
        {
          path: '/orders/:orderNo/refund',
          component: '@/pages/orders/refund',
          wrappers: ['@/shared/auth/RequireAuth'],
        },
        {
          path: '/orders/:orderNo',
          component: '@/pages/orders/detail',
          wrappers: ['@/shared/auth/RequireAuth'],
        },
        {
          path: '/payments/:orderNo/result',
          component: '@/pages/payments/result',
          wrappers: ['@/shared/auth/RequireAuth'],
        },
        {
          path: '/payments/:orderNo',
          component: '@/pages/payments',
          wrappers: ['@/shared/auth/RequireAuth'],
        },
        {
          path: '/tickets/:ticketId',
          component: '@/pages/tickets',
          wrappers: ['@/shared/auth/RequireAuth'],
        },
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
