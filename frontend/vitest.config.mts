import { defineConfig } from 'vitest/config';

export default defineConfig({
  test: {
    clearMocks: true,
    environment: 'jsdom',
    include: ['src/**/*.test.{ts,tsx}'],
    restoreMocks: true,
    setupFiles: ['./tests/setup.ts'],
    silent: 'passed-only',
    testTimeout: 10_000,
    coverage: {
      exclude: ['src/.umi/**', 'src/.umi-production/**', 'src/.umi-test/**'],
      provider: 'v8',
      reporter: ['text', 'html'],
    },
  },
});
