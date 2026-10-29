import { createHash } from 'node:crypto';
import { parse } from 'parse5';

const GENERATED_ATTRIBUTE = 'data-chegaai-csp';
const GENERATED_VERSION = 'export-v1';
const LOCAL_HOSTS = new Set(['localhost', '127.0.0.1', '[::1]']);

export function validateApiOrigin(value) {
  const message = 'NEXT_PUBLIC_API_BASE_URL deve ser uma URL HTTPS terminada em /api, sem credenciais, query ou fragmento; HTTP só é aceito em loopback.';
  if (typeof value !== 'string' || !value || value !== value.trim()) throw new Error(message);
  let url;
  try { url = new URL(value); } catch { throw new Error(message); }
  if (url.username || url.password || url.search || url.hash || url.pathname !== '/api'
      || (url.protocol !== 'https:' && !(url.protocol === 'http:' && LOCAL_HOSTS.has(url.hostname)))) {
    throw new Error(message);
  }
  return url.origin;
}

function nodesIn(node) {
  return [node, ...(node.childNodes || []).flatMap(nodesIn), ...(node.content ? nodesIn(node.content) : [])];
}
function attribute(node, name) {
  return node.attrs?.find((item) => item.name === name)?.value;
}
function sourceLocation(node) {
  const location = node.sourceCodeLocation;
  if (!location?.startTag || !location.endTag) throw new Error(`Elemento ${node.tagName} incompleto no export.`);
  return location;
}
function inspect(input) {
  if (typeof input !== 'string' || input.includes('\0')) throw new Error('HTML inválido no export.');
  const html = input.replace(/\r\n?/g, '\n');
  const errors = [];
  const document = parse(html, { sourceCodeLocationInfo: true, onParseError: (error) => errors.push(error.code) });
  if (errors.length) throw new Error(`HTML inválido no export: ${[...new Set(errors)].join(', ')}.`);
  const nodes = nodesIn(document);
  const heads = nodes.filter((node) => node.tagName === 'head');
  if (heads.length !== 1) throw new Error('O export precisa de um head explícito e único.');
  const head = heads[0];
  const headLocation = sourceLocation(head);
  const charsets = (head.childNodes || []).filter((node) => node.tagName === 'meta' && attribute(node, 'charset') !== undefined);
  if (charsets.length !== 1 || attribute(charsets[0], 'charset').toLowerCase() !== 'utf-8'
      || !charsets[0].sourceCodeLocation?.endOffset) {
    throw new Error('O head precisa de um único meta charset UTF-8.');
  }
  const charset = charsets[0];
  const insertion = charset.sourceCodeLocation.endOffset;
  if (insertion > headLocation.endTag.startOffset) throw new Error('Charset fora do head.');
  const scripts = nodes.filter((node) => node.tagName === 'script');
  const resources = nodes.filter((node) => node.tagName === 'script'
    || (node.tagName === 'link' && /(?:^|\s)(?:preload|modulepreload|stylesheet)(?:\s|$)/i.test(attribute(node, 'rel') || '')));
  if (resources.some((node) => !node.sourceCodeLocation || node.sourceCodeLocation.startOffset < insertion)) {
    throw new Error('Charset precisa vir antes dos scripts e recursos do export.');
  }
  const hashes = new Set();
  for (const script of scripts) {
    const location = sourceLocation(script);
    if (attribute(script, 'src') === undefined) {
      const text = html.slice(location.startTag.endOffset, location.endTag.startOffset);
      hashes.add(`'sha256-${createHash('sha256').update(text, 'utf8').digest('base64')}'`);
    }
  }
  const policies = nodes.filter((node) => node.tagName === 'meta'
    && /^content-security-policy(?:-report-only)?$/i.test((attribute(node, 'http-equiv') || '').trim()));
  return { html, head, charset, insertion, policies, hashes: [...hashes].sort() };
}

export function contentPolicy(apiOrigin, hashes) {
  return [
    "default-src 'self'",
    `script-src 'self'${hashes.length ? ` ${hashes.join(' ')}` : ''}`,
    "script-src-attr 'none'",
    "style-src 'self' 'unsafe-inline'",
    "font-src 'self'",
    "img-src 'self' data:",
    `connect-src 'self' ${apiOrigin}`,
    "object-src 'none'",
    "frame-src 'none'",
    "base-uri 'none'",
    "form-action 'self'",
  ].join('; ');
}

function validateExisting(result, policy) {
  if (result.policies.length !== 1) throw new Error('O export deve ter uma única meta CSP gerada.');
  const meta = result.policies[0];
  if (meta.parentNode !== result.head || attribute(meta, GENERATED_ATTRIBUTE) !== GENERATED_VERSION
      || attribute(meta, 'http-equiv')?.toLowerCase() !== 'content-security-policy') {
    throw new Error('Já existe uma CSP não gerada por este processo.');
  }
  if (attribute(meta, 'content') !== policy) throw new Error('CSP existente diverge dos scripts ou da origem da API; gere um export novo.');
  const location = meta.sourceCodeLocation;
  if (!location || location.startOffset < result.insertion
      || result.html.slice(result.insertion, location.startOffset).trim() !== '') {
    throw new Error('A meta CSP precisa vir logo após o charset.');
  }
}

export function verifySecuredHtml(input, apiBaseUrl) {
  const result = inspect(input);
  const policy = contentPolicy(validateApiOrigin(apiBaseUrl), result.hashes);
  validateExisting(result, policy);
  return { policy, hashes: result.hashes };
}

export function secureHtml(input, apiBaseUrl) {
  const result = inspect(input);
  const policy = contentPolicy(validateApiOrigin(apiBaseUrl), result.hashes);
  if (result.policies.length) {
    // Uma segunda execução só é aceita se a política já cobre exatamente o mesmo conteúdo.
    validateExisting(result, policy);
    return result.html;
  }
  const escaped = policy.replaceAll('&', '&amp;').replaceAll('"', '&quot;').replaceAll('<', '&lt;');
  const meta = `<meta http-equiv="Content-Security-Policy" content="${escaped}" ${GENERATED_ATTRIBUTE}="${GENERATED_VERSION}"/>`;
  const secured = result.html.slice(0, result.insertion) + meta + result.html.slice(result.insertion);
  verifySecuredHtml(secured, apiBaseUrl);
  return secured;
}
