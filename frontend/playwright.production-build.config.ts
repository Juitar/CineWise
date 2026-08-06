import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  testMatch: /(?:agent-workspace|auth|production-build)\.spec\.ts/,
  globalSetup: './scripts/production-e2e-global-setup.mjs',
  fullyParallel: true,
  forbidOnly: Boolean(process.env.CI),
  retries: process.env.CI ? 2 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: 'http://127.0.0.1:4173',
    screenshot: 'only-on-failure',
    trace: 'on-first-retry',
  },
  projects: [
    {
      name: 'production-desktop-chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'production-mobile-chromium',
      use: { ...devices['Pixel 7'] },
    },
  ],
});
