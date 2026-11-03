import { defineConfig } from '@playwright/test'
export default defineConfig({
  testDir: './e2e',
  timeout: 30000,
  fullyParallel: false,
  reporter: 'list',
  outputDir: '../.local/playwright-results',
  use: { baseURL: process.env.SCOUT_TEST_URL || 'http://127.0.0.1:3000', viewport: { width: 1440, height: 1050 }, screenshot: 'only-on-failure', launchOptions: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH ? { executablePath: process.env.PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH } : {} }
})
