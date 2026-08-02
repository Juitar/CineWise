import { defineConfig } from 'umi';

export default defineConfig({
  proxy: {
    '/api': {
      target: 'http://127.0.0.1:8080',
      changeOrigin: true,
    },
  },
  routes: [
    {
      path: '/',
      component: '@/layouts/UserLayout',
      routes: [
        { path: '/', component: '@/pages/home' },
        { path: '*', component: '@/pages/not-found' },
      ],
    },
  ],
  npmClient: 'pnpm',
  utoopack: {},
});
