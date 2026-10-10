import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false, // Mantém falso para rodar os cenários em ordem limpa
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  workers: 1, // Excelente para ambiente de desenvolvimento local e logs ordenados
  
  // TAREFA 1: CONFIGURAÇÃO DE EVIDÊNCIAS E LOGS
  reporter: [
    ['list'],                                           // Mostra o log detalhado no terminal
    ['html', { outputFolder: 'playwright-report' }],    // Relatório HTML rico
    ['json', { outputFile: 'test-results/report.json' }]// Relatório JSON para Rastreabilidade
  ],
  
  use: {
    baseURL: 'http://localhost:3000', // URL local do Next.js
    
    // TAREFA 1: Screenshots e Traces (Evidências)
    trace: 'on',         // Gera um "raio-x" visual sempre (ótimo para rastreabilidade)
    screenshot: 'on',    // Tira print ao final de CADA teste (sucesso ou falha)
    video: 'retain-on-failure',
  },

  // Sobe o Next.js antes dos testes (a API Spring Boot precisa estar rodando na porta 8080)
  webServer: {
    // no CI o workflow já fez o build de produção
    command: process.env.CI ? 'npm run start' : 'npm run dev',
    url: 'http://localhost:3000',
    reuseExistingServer: !process.env.CI,
  },

  /* Focaremos no Chromium (Chrome) para acelerar seus testes acadêmicos */
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
    // { name: 'firefox', use: { ...devices['Desktop Firefox'] } },
  ],
});