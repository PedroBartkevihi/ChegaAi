import { createServer } from 'node:http';
import { createReadStream } from 'node:fs';
import { realpath, stat } from 'node:fs/promises';
import { dirname, extname, isAbsolute, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const url = new URL(process.env.E2E_BASE_URL || 'http://127.0.0.1:3000');
if (url.protocol !== 'http:' || !['localhost', '127.0.0.1', '[::1]'].includes(url.hostname)
    || url.pathname !== '/' || url.search || url.hash || url.username || url.password) {
  throw new Error('Servidor E2E aceita somente uma origem HTTP de loopback.');
}
const root = await realpath(resolve(dirname(fileURLToPath(import.meta.url)), '../out'));
const mime = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8', '.json': 'application/json', '.svg': 'image/svg+xml',
  '.png': 'image/png', '.jpg': 'image/jpeg', '.ico': 'image/x-icon', '.woff2': 'font/woff2', '.txt': 'text/plain; charset=utf-8' };
function dentro(path) {
  const caminho = relative(root, path);
  return caminho === '' || (!caminho.startsWith('..') && !isAbsolute(caminho));
}
createServer(async (request, response) => {
  if (!['GET', 'HEAD'].includes(request.method)) { response.writeHead(405).end(); return; }
  try {
    const pathname = decodeURIComponent(new URL(request.url, url).pathname);
    if (pathname.includes('\\') || pathname.includes('\0')) { response.writeHead(400).end(); return; }
    let caminho = resolve(root, `.${pathname}`);
    if (!dentro(caminho)) { response.writeHead(403).end(); return; }
    let info = await stat(caminho);
    if (info.isDirectory()) { caminho = resolve(caminho, 'index.html'); info = await stat(caminho); }
    caminho = await realpath(caminho);
    if (!dentro(caminho) || !info.isFile()) { response.writeHead(403).end(); return; }
    response.writeHead(200, { 'Content-Type': mime[extname(caminho)] || 'application/octet-stream',
      'Content-Length': info.size, 'Cache-Control': 'no-store' });
    if (request.method === 'HEAD') response.end();
    else createReadStream(caminho).pipe(response);
  } catch { response.writeHead(404).end(); }
}).listen(Number(url.port || 80), url.hostname.replace(/^\[|\]$/g, ''), () => {
  console.log(`Export estático E2E em ${url.origin}`);
});
