import { defineConfig } from 'umi';

const amapWebKey = process.env.AMAP_WEB_JS_KEY ?? process.env.UMI_APP_AMAP_WEB_JS_KEY ?? '';
const amapSecurityJsCode = process.env.AMAP_WEB_SECURITY_JS_CODE
  ?? process.env.UMI_APP_AMAP_WEB_SECURITY_JS_CODE
  ?? '';

export default defineConfig({
  hash: true,
  title: '妙语购票',
  headScripts: amapSecurityJsCode
    ? [{ content: `window._AMapSecurityConfig = { securityJsCode: ${JSON.stringify(amapSecurityJsCode)} };` }]
    : [],
  define: {
    'process.env.AMAP_WEB_JS_KEY': amapWebKey,
    'process.env.AMAP_WEB_SECURITY_JS_CODE': amapSecurityJsCode,
  },
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
        { path: '/movies/:movieId', component: '@/pages/movies/available-cinemas' },
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
          path: '/recommendations',
          component: '@/pages/recommendations',
          wrappers: ['@/shared/auth/RequireAuth'],
        },
        {
          path: '/recommendations/:sessionId/plan',
          component: '@/pages/recommendations',
          wrappers: ['@/shared/auth/RequireAuth'],
        },
        {
          path: '/recommendations/:sessionId/movies/:movieId',
          component: '@/pages/recommendations',
          wrappers: ['@/shared/auth/RequireAuth'],
        },
        {
          path: '/recommendations/:sessionId/shows',
          component: '@/pages/recommendations',
          wrappers: ['@/shared/auth/RequireAuth'],
        },
        {
          path: '/recommendations/:sessionId/shows/:showId/seats',
          component: '@/pages/recommendations',
          wrappers: ['@/shared/auth/RequireAuth'],
        },
        {
          path: '/recommendations/:sessionId/orders/confirm',
          component: '@/pages/recommendations',
          wrappers: ['@/shared/auth/RequireAuth'],
        },
        {
          path: '/recommendations/:sessionId/orders',
          component: '@/pages/recommendations',
          wrappers: ['@/shared/auth/RequireAuth'],
        },
        {
          path: '/recommendations/:sessionId/orders/:orderNo',
          component: '@/pages/recommendations',
          wrappers: ['@/shared/auth/RequireAuth'],
        },
        {
          path: '/recommendations/:sessionId/payments/:orderNo/result',
          component: '@/pages/recommendations',
          wrappers: ['@/shared/auth/RequireAuth'],
        },
        {
          path: '/recommendations/:sessionId/payments/:orderNo',
          component: '@/pages/recommendations',
          wrappers: ['@/shared/auth/RequireAuth'],
        },
        {
          path: '/recommendations/:sessionId/tickets/:ticketId',
          component: '@/pages/recommendations',
          wrappers: ['@/shared/auth/RequireAuth'],
        },
        {
          path: '/recommendations/:sessionId',
          component: '@/pages/recommendations',
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
        {
          path: '/travel/:taskId',
          component: '@/pages/travel',
          wrappers: ['@/shared/auth/RequireAuth'],
        },
        { path: '*', component: '@/pages/not-found' },
      ],
    },
  ],
  npmClient: 'pnpm',
  utoopack: {},
});
