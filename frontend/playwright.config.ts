import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false, // Mantém falso para rodar os cenários em ordem limpa
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1, // Excelente para ambiente de desenvolvimento local
  reporter: [
    ['html', { outputFolder: 'playwright-report' }],
    ['list'] // Mostra o log detalhado direto no terminal
  ],
  use: {
    baseURL: 'http://localhost:5173', // Sua URL local do Vite
    trace: 'retain-on-failure',       // Gera um "raio-x" do teste se falhar
    screenshot: 'only-on-failure',    // Tira print se der erro
    video: 'retain-on-failure',       // Grava a tela se der erro
  },

  /* Focaremos no Chromium (Chrome) para acelerar seus testes acadêmicos */
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    {
      name: 'firefox',
      use: { ...devices['Desktop Firefox'] },
    },
  ],
});