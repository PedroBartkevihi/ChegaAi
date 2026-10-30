import test from 'node:test';
import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { mkdtemp, mkdir, readFile, realpath, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { basename, dirname, join } from 'node:path';
import { secureHtml, validateApiOrigin, verifySecuredHtml } from '../scripts/security-policy.mjs';
import { secureExport } from '../scripts/secure-export.mjs';
import { parseRenderHeaders, readRenderHeaders } from '../scripts/render-headers.mjs';

const API = 'https://api.example.invalid/api';
const document = (head = '', body = '') => `<!doctype html><html><head><meta charset="utf-8">${head}</head><body>${body}</body></html>`;
const hash = (text) => `'sha256-${createHash('sha256').update(text, 'utf8').digest('base64')}'`;
const yaml = (headers, fields = '') => `services:\n  - name: chegaai-web\n    type: web\n    runtime: static\n${fields}    headers:\n${headers}`;
const rule = (name = 'X-Frame-Options', value = 'DENY', path = '/*') => `      - path: ${path}\n        name: ${name}\n        value: ${value}\n`;

test('hashes cobrem scripts do head e body, com Unicode, atributos e conteúdo preservados', () => {
  const theme = 'window.tema="escuro > claro";';
  const flight = 'self.__next_f.push([1,"Olá, rolé! ☀"]);';
  const input = document(`<script data-probe=">">${theme}</script><script src="/_next/app.js" async></script>`, `<script>${flight}</script><script>${theme}</script>`);
  const output = secureHtml(input, API);
  const result = verifySecuredHtml(output, API);
  assert.deepEqual(result.hashes, [hash(theme), hash(flight)].sort());
  assert.ok(output.includes(`<script data-probe=">">${theme}</script>`));
  assert.ok(output.includes(`<script>${flight}</script>`));
  assert.ok(output.indexOf('data-chegaai-csp') < output.indexOf('<script'));
  assert.ok(output.startsWith('<!doctype html><html><head><meta charset="utf-8"><meta http-equiv='));
  assert.match(result.policy, /script-src 'self' 'sha256-/);
  assert.match(result.policy, /script-src-attr 'none'/);
  assert.match(result.policy, /style-src 'self' 'unsafe-inline'/);
  assert.match(result.policy, /connect-src 'self' https:\/\/api\.example\.invalid/);
  assert.doesNotMatch(result.policy.split('; ').find((value) => value.startsWith('script-src ')), /unsafe-inline|unsafe-eval/);
  assert.doesNotMatch(result.policy, /report-uri|report-to|frame-ancestors/);
});

test('CRLF e CR são normalizados antes do hash, sem mudar o texto dos scripts além dos EOLs', () => {
  const script = 'window.a=1;\nwindow.b=2;\n';
  const original = document(`<script>${script}</script>`);
  const crlf = secureHtml(original.replaceAll('\n', '\r\n'), API);
  const cr = secureHtml(original.replaceAll('\n', '\r'), API);
  assert.equal(crlf, secureHtml(original, API));
  assert.equal(cr, crlf);
  assert.deepEqual(verifySecuredHtml(crlf, API).hashes, [hash(script)]);
  assert.ok(!crlf.includes('\r'));
});

test('segunda geração é idêntica e não duplica a meta', () => {
  const first = secureHtml(document('<script>window.a=1</script>'), API);
  assert.equal(secureHtml(first, API), first);
  assert.equal((first.match(/http-equiv="Content-Security-Policy"/g) || []).length, 1);
});

test('CSP gerada detecta mudança de script ou API e não concede cobertura silenciosamente', () => {
  const secured = secureHtml(document('<script>window.a=1</script>'), API);
  assert.throws(() => secureHtml(secured.replace('window.a=1', 'window.a=2'), API), /diverge/);
  assert.throws(() => secureHtml(secured.replace('</body>', '<script>window.novo=1</script></body>'), API), /diverge/);
  assert.throws(() => secureHtml(secured, 'https://outra-api.example.invalid/api'), /diverge/);
  assert.throws(() => verifySecuredHtml(document(), API), /única meta/);
});

test('CSP desconhecida, duplicada ou deslocada falha em vez de enfraquecer a política', () => {
  assert.throws(() => secureHtml(document('<meta http-equiv="Content-Security-Policy" content="default-src *">'), API), /não gerada/);
  assert.throws(() => secureHtml(document('<meta http-equiv="Content-Security-Policy-Report-Only" content="default-src *">'), API), /não gerada/);
  const secured = secureHtml(document('<script>window.a=1</script>'), API);
  const meta = secured.match(/<meta http-equiv="Content-Security-Policy"[^>]*>/)[0];
  assert.throws(() => secureHtml(secured.replace('</head>', `${meta}</head>`), API), /única meta/);
  assert.throws(() => secureHtml(secured.replace(meta, '').replace('</head>', `${meta}</head>`), API), /logo após/);
});

test('head e charset precisam estar explícitos, íntegros e antes dos recursos', () => {
  const invalid = [
    '<!doctype html><html><body>Sem head</body></html>',
    '<!doctype html><html><head><meta charset="utf-8"><body>Head sem fechamento</body></html>',
    document().replace('<meta charset="utf-8">', ''),
    document().replace('charset="utf-8"', 'charset="latin1"'),
    document('<meta charset="utf-8">'),
    document().replace('<meta charset="utf-8">', '<script>window.a=1</script><meta charset="utf-8">'),
    document().replace('<meta charset="utf-8">', '<link rel="preload" href="/x.js" as="script"><meta charset="utf-8">'),
    document().replace('</head>', '</head><head><meta charset="utf-8"></head>'),
    document('<script>sem fechamento'),
    document('Nulo\0'),
  ];
  for (const input of invalid) assert.throws(() => secureHtml(input, API), Error);
});

test('URL aceita HTTPS e apenas os hosts explícitos de loopback em HTTP', () => {
  assert.equal(validateApiOrigin(API), 'https://api.example.invalid');
  assert.equal(validateApiOrigin('HTTPS://API.EXAMPLE.INVALID:443/api'), 'https://api.example.invalid');
  assert.equal(validateApiOrigin('http://LOCALHOST:80/api'), 'http://localhost');
  for (const host of ['127.0.0.1', 'localhost', '[::1]']) {
    assert.equal(validateApiOrigin(`http://${host}:8080/api`), `http://${host}:8080`);
  }
  for (const value of [undefined, '', '/api', 'http://api.example.invalid/api', 'http://localhost.example.invalid/api',
    'http://0.0.0.0/api', 'https://u:p@api.example.invalid/api', 'https://api.example.invalid/api?x=1',
    'https://api.example.invalid/api#x', 'https://api.example.invalid/api/', 'https://api.example.invalid', 'javascript:alert(1)', ` ${API}`]) {
    assert.throws(() => validateApiOrigin(value), /NEXT_PUBLIC_API_BASE_URL/);
  }
});

test('URL rejeita delimitadores vazios de query e fragmento', () => {
  for (const base of [API, 'http://127.0.0.1:8086/api']) {
    for (const suffix of ['?', '#', '?#', '#?']) {
      const value = base + suffix;
      assert.throws(() => validateApiOrigin(value), /NEXT_PUBLIC_API_BASE_URL/, value);
      assert.throws(() => secureHtml(document(), value), /NEXT_PUBLIC_API_BASE_URL/, value);
    }
  }
});

async function directory(t) {
  const parent = await realpath(tmpdir());
  const root = await mkdtemp(join(parent, 'chegaai-security-'));
  t.after(async () => {
    const actual = await realpath(root);
    if (dirname(actual) !== parent || !basename(actual).startsWith('chegaai-security-')) throw new Error('Não remover diretório fora do teste.');
    await rm(actual, { recursive: true });
  });
  return root;
}

test('processa todos os HTML, inclusive 404 aninhado, e preserva arquivos de outro tipo', async (t) => {
  const root = await directory(t);
  await mkdir(join(root, '_not-found'));
  await writeFile(join(root, 'index.html'), document('<script>window.home=1</script>'));
  await writeFile(join(root, '404.html'), document('', '<script>window.erro=1</script>'));
  await writeFile(join(root, '_not-found', 'index.html'), document('<script>window.aninhado=1</script>'));
  await writeFile(join(root, 'asset.js'), 'window.asset=1;');
  assert.deepEqual(await secureExport(root, API), { files: 3, changed: 3 });
  for (const file of ['index.html', '404.html', '_not-found/index.html']) verifySecuredHtml(await readFile(join(root, file), 'utf8'), API);
  assert.equal(await readFile(join(root, 'asset.js'), 'utf8'), 'window.asset=1;');
  assert.deepEqual(await secureExport(root, API), { files: 3, changed: 0 });
});

test('valida todo o export antes de escrever e falha quando não há HTML', async (t) => {
  const root = await directory(t);
  await assert.rejects(secureExport(root, API), /Nenhum HTML/);
  const valid = document('<script>window.a=1</script>');
  await writeFile(join(root, 'index.html'), valid);
  await writeFile(join(root, '404.html'), '<!doctype html><html><head></head><body></body></html>');
  await assert.rejects(secureExport(root, API), /charset/);
  assert.equal(await readFile(join(root, 'index.html'), 'utf8'), valid);
});

test('cabeçalhos são lidos do YAML, com nomes normalizados e os valores exatos', () => {
  const headers = parseRenderHeaders(yaml(rule() + rule('Referrer-Policy', 'strict-origin-when-cross-origin')));
  assert.equal(headers['x-frame-options'], 'DENY');
  assert.equal(headers['referrer-policy'], 'strict-origin-when-cross-origin');
  assert.equal(Object.keys(headers).length, 2);
  assert.ok(Object.isFrozen(headers));
});

test('YAML inválido e cabeçalhos duplicados, inseguros ou fora do padrão global são recusados', () => {
  for (const source of [
    'services: [',
    yaml(rule()).replace('runtime: static', 'runtime: static\n    runtime: node'),
    yaml(rule()).replace('name: chegaai-web', 'name: outro-site'),
    yaml(rule()).replace('runtime: static', 'runtime: node'),
    yaml(rule()).replace('    type: web', '    type: worker'),
    yaml(''),
    yaml(rule() + rule('x-frame-options', 'SAMEORIGIN')),
    yaml(rule('X Invalido', 'DENY')),
    yaml(rule('X-Test', '"linha1\\nlinha2"')),
    yaml(rule('X-Test', '123')),
    yaml(rule('X-Test', 'ok', '/assets/*')),
    yaml(rule()).replace('path:', 'source:'),
    yaml(rule()).replace('    headers:', '    mais: &ref [x]\n    headers: *ref\n    antigo:'),
  ]) assert.throws(() => parseRenderHeaders(source), Error);
});

test('Render real é a fonte única e o header fixo não bloqueia os hashes da meta', async () => {
  const headers = await readRenderHeaders();
  assert.equal(headers['x-frame-options'], 'DENY');
  assert.equal(headers['x-content-type-options'], 'nosniff');
  assert.equal(headers['referrer-policy'], 'strict-origin-when-cross-origin');
  assert.match(headers['content-security-policy'], /frame-ancestors 'none'/);
  assert.doesNotMatch(headers['content-security-policy'], /default-src|script-src|connect-src/);
  assert.equal(Object.keys(headers).length, 4);
});
