import { readFile } from 'node:fs/promises';
import { validateHeaderName, validateHeaderValue } from 'node:http';
import { parseDocument } from 'yaml';

export function parseRenderHeaders(source) {
  const document = parseDocument(source, { version: '1.2', uniqueKeys: true, prettyErrors: false });
  if (document.errors.length || document.warnings.length) throw new Error('render.yaml inválido: corrija erros ou avisos do YAML.');
  const config = document.toJS({ maxAliasCount: 0 });
  if (!Array.isArray(config?.services)) throw new Error('render.yaml precisa declarar services.');
  const services = config.services.filter((service) => service?.name === 'chegaai-web');
  if (services.length !== 1 || services[0].runtime !== 'static' || services[0].type !== 'web') {
    throw new Error('É necessário um único serviço web estático chamado chegaai-web.');
  }
  const rules = services[0].headers;
  if (!Array.isArray(rules) || !rules.length) throw new Error('Cabeçalhos do site estático não foram declarados.');
  const headers = Object.create(null);
  for (const rule of rules) {
    if (!rule || rule.path !== '/*' || Object.keys(rule).some((key) => !['path', 'name', 'value'].includes(key))) {
      throw new Error('O servidor E2E aceita apenas cabeçalhos globais path: /* do Render.');
    }
    if (typeof rule.name !== 'string' || typeof rule.value !== 'string') throw new Error('Nome e valor de cabeçalho precisam ser strings.');
    validateHeaderName(rule.name);
    validateHeaderValue(rule.name, rule.value);
    const name = rule.name.toLowerCase();
    if (Object.hasOwn(headers, name)) throw new Error(`Cabeçalho duplicado no Render: ${rule.name}`);
    headers[name] = rule.value;
  }
  return Object.freeze(headers);
}

export async function readRenderHeaders(path = new URL('../../render.yaml', import.meta.url)) {
  return parseRenderHeaders(await readFile(path, 'utf8'));
}
