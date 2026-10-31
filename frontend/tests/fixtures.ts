import { randomUUID } from 'node:crypto';
import { test as base, expect, type APIRequestContext, type Page, type TestInfo } from '@playwright/test';
import pg from 'pg';
import { apiURL, frontendURL, databaseURL } from './environment';

export { expect };
export type Conta = { id: string; nome: string; email: string; senha: string; token: string };
export type EventoDTO = {
  id: string; title: string; category: string; date: string; time: string; capacity: number;
  status: string; organizer: { id: string; name: string }; confirmed: string[]; waitlist: string[];
  comments: { author: string; text: string }[];
};
export type Evento = { id: string; titulo: string; organizador: Conta; futuro: boolean };
type DadosEvento = { title: string; category: string; date: string; time: string; cep: string;
  place: string; capacity: number; description: string };

export class Cenario {
  readonly prefixo: string;
  private sequencia = 0;
  private eventos = new Map<string, Evento>();

  constructor(private request: APIRequestContext, info: TestInfo) {
    // O UUID muda também quando retry/repeatEach recria o mesmo teste.
    this.prefixo = `E2E-${info.repeatEachIndex}-${info.retry}-${randomUUID()}`;
    databaseURL(); // Validar os três destinos antes da primeira mutação por API.
  }

  unico(apelido: string): string { return `${this.prefixo}-${apelido}-${++this.sequencia}`; }
  dadosConta(apelido = 'pessoa') {
    const identificador = this.unico(apelido);
    return { name: `${identificador} Teste`, email: `${identificador.toLowerCase()}@example.invalid`,
      birthDate: '1995-05-10', password: 'SenhaE2E2026!' };
  }
  dadosEvento(overrides: Partial<DadosEvento> = {}): DadosEvento {
    const data = new Date();
    data.setDate(data.getDate() + 5);
    return { title: this.unico('role'), category: 'Futebol', date: data.toISOString().slice(0, 10),
      time: '18:00', cep: '', place: 'Campo de testes, Rio de Janeiro', capacity: 3,
      description: 'Rolé fictício exclusivo desta tentativa de teste.', ...overrides };
  }
  endpoint(path: string): string { return `${apiURL.href}${path}`; }
  auth(conta: Conta) { return { Authorization: `Bearer ${conta.token}` }; }

  async criarConta(apelido = 'pessoa'): Promise<Conta> {
    const dados = this.dadosConta(apelido);
    const response = await this.request.post(this.endpoint('/auth/cadastro'), { data: dados });
    expect(response.status()).toBe(201);
    const { user, token } = await response.json();
    expect(user.id).toMatch(/^[a-f0-9-]{36}$/);
    expect(user.email).toBe(dados.email);
    return { id: user.id, nome: user.name, email: dados.email, senha: dados.password, token };
  }
  async criarEvento(organizador: Conta, overrides: Partial<DadosEvento> = {}): Promise<Evento> {
    const dados = this.dadosEvento(overrides);
    const response = await this.request.post(this.endpoint('/eventos'), { headers: this.auth(organizador), data: dados });
    expect(response.status()).toBe(201);
    const dto: EventoDTO = await response.json();
    expect(dto.title).toBe(dados.title);
    expect(dto.organizer.id).toBe(organizador.id);
    return this.rastrearEventoDTO(dto, organizador);
  }
  rastrearEventoDTO(dto: EventoDTO, organizador: Conta): Evento {
    expect(dto.id).toMatch(/^[a-f0-9-]{36}$/);
    expect(dto.organizer.id).toBe(organizador.id);
    expect(dto.title).toContain(this.prefixo);
    const evento = { id: dto.id, titulo: dto.title, organizador, futuro: true };
    this.eventos.set(evento.id, evento);
    return evento;
  }
  private proprio(evento: Evento) {
    if (this.eventos.get(evento.id) !== evento) throw new Error('A fixture só pode alterar o rolé que ela própria criou.');
  }
  async obterEvento(evento: Evento): Promise<EventoDTO> {
    const response = await this.request.get(this.endpoint(`/eventos/${evento.id}`));
    expect(response.status()).toBe(200);
    const dto = await response.json();
    expect(dto.id).toBe(evento.id);
    return dto;
  }
  // Ids de todos os rolés, próximos e anteriores, percorrendo o feed página por página.
  async idsDeTodosOsRoles(): Promise<string[]> {
    const ids: string[] = [];
    for (const secao of ['proximos', 'anteriores']) {
      for (let pagina = 0; ; pagina += 1) {
        const response = await this.request.get(this.endpoint(`/eventos/feed?secao=${secao}&pagina=${pagina}&tamanho=50`));
        expect(response.status()).toBe(200);
        const { items, hasNext } = await response.json();
        ids.push(...items.map((item: { id: string }) => item.id));
        if (!hasNext) break;
      }
    }
    return ids.sort();
  }
  async inscrever(evento: Evento, conta: Conta) {
    this.proprio(evento);
    const response = await this.request.post(this.endpoint(`/eventos/${evento.id}/inscricao`), { headers: this.auth(conta) });
    expect(response.status()).toBe(200);
    const dto = await response.json();
    expect(dto.eventoId).toBe(evento.id);
    expect(dto.usuarioId).toBe(conta.id);
    return dto as { eventoId: string; usuarioId: string; status: string };
  }
  async comentario(evento: Evento, conta: Conta, text: string) {
    this.proprio(evento);
    return this.request.post(this.endpoint(`/eventos/${evento.id}/comentarios`), { headers: this.auth(conta), data: { text } });
  }
  async moverParaPassado(evento: Evento) {
    this.proprio(evento);
    const client = new pg.Client({ connectionString: databaseURL() });
    try {
      await client.connect();
      const resultado = await client.query(
        `UPDATE evento SET data_hora_realizacao = now() - interval '2 hours'
         WHERE id_evento = $1 AND id_organizador = $2 AND titulo = $3`,
        [evento.id, evento.organizador.id, evento.titulo]);
      expect(resultado.rowCount).toBe(1);
      evento.futuro = false;
    } finally { await client.end(); }
  }
  async marcarPresenca(evento: Evento, conta: Conta) {
    this.proprio(evento);
    const response = await this.request.post(this.endpoint(`/eventos/${evento.id}/presencas`), {
      headers: this.auth(evento.organizador), data: { participanteId: conta.id } });
    expect(response.status()).toBe(204);
  }
  async concluir(evento: Evento) {
    this.proprio(evento);
    const response = await this.request.post(this.endpoint(`/eventos/${evento.id}/concluir`), { headers: this.auth(evento.organizador) });
    expect(response.status()).toBe(200);
    expect((await this.obterEvento(evento)).status).toBe('CONCLUIDO');
  }
  async limpar() {
    for (const evento of this.eventos.values()) {
      // Não apagar históricos ou eventos de terceiros. O banco é descartável.
      if (!evento.futuro) continue;
      const response = await this.request.delete(this.endpoint(`/eventos/${evento.id}`), { headers: this.auth(evento.organizador) });
      expect(response.status(), `Limpeza do rolé próprio ${evento.id}`).toBe(204);
    }
  }
}

export const test = base.extend<{ cenario: Cenario }>({
  context: async ({ context }, use) => {
    const externos: string[] = [];
    await context.route('**/*', async (route) => {
      const url = new URL(route.request().url());
      if (['http:', 'https:'].includes(url.protocol)
          && ![apiURL.origin, frontendURL.origin].includes(url.origin)) {
        externos.push(url.origin);
        await route.abort('blockedbyclient');
      } else await route.continue();
    });
    await use(context);
    expect(externos, 'O navegador só pode acessar as duas origens E2E configuradas, mesmo com build antigo.').toEqual([]);
  },
  cenario: async ({ request }, use, info) => {
    const cenario = new Cenario(request, info);
    try { await use(cenario); } finally { await cenario.limpar(); }
  },
});

// O health é disparado pelo useEffect inicial: aguardá-lo evita digitar no HTML antes da hidratação.
export async function abrirPagina(page: Page) {
  const inicio = page.waitForResponse((response) => new URL(response.url()).pathname.endsWith('/api/actuator/health'));
  await page.goto('/');
  await inicio;
  await page.evaluate(() => new Promise<void>((resolve) => requestAnimationFrame(() => resolve())));
}

export async function loginPelaUI(page: Page, conta: Pick<Conta, 'email' | 'senha'> & Partial<Pick<Conta, 'id'>>) {
  await abrirPagina(page);
  await page.getByTestId('input-email').fill(conta.email);
  await page.getByTestId('input-password').fill(conta.senha);
  const responsePromise = page.waitForResponse((response) => response.url().endsWith('/api/auth/login') && response.request().method() === 'POST');
  await page.getByTestId('btn-login').click();
  const response = await responsePromise;
  expect(response.status()).toBe(200);
  const { user } = await response.json();
  if (conta.id) expect(user.id).toBe(conta.id);
  await expect(page.locator('section[aria-label="Feed de eventos"]')).toBeVisible();
  return user;
}
