import { readdir, readFile, realpath, writeFile } from 'node:fs/promises';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { secureHtml, validateApiOrigin } from './security-policy.mjs';

async function htmlFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true });
  const files = [];
  for (const entry of entries.sort((a, b) => a.name.localeCompare(b.name))) {
    const path = join(directory, entry.name);
    if (entry.isSymbolicLink()) throw new Error('O export não pode conter links simbólicos.');
    if (entry.isDirectory()) files.push(...await htmlFiles(path));
    else if (entry.isFile() && entry.name.toLowerCase().endsWith('.html')) files.push(path);
  }
  return files;
}

export async function secureExport(directory, apiBaseUrl) {
  validateApiOrigin(apiBaseUrl);
  const root = await realpath(directory);
  const files = await htmlFiles(root);
  if (!files.length) throw new Error('Nenhum HTML encontrado no export estático.');
  // Validar o conjunto inteiro antes de gravar: um HTML inválido faz o build falhar fechado.
  const changes = await Promise.all(files.map(async (path) => {
    const before = await readFile(path, 'utf8');
    return { path, before, after: secureHtml(before, apiBaseUrl) };
  }));
  const changed = changes.filter((item) => item.before !== item.after);
  for (const item of changed) await writeFile(item.path, item.after, 'utf8');
  return { files: files.length, changed: changed.length };
}

if (process.argv[1] && import.meta.url === pathToFileURL(resolve(process.argv[1])).href) {
  if (process.env.NEXT_OUTPUT !== 'export') {
    console.log('CSP do export: não se aplica a este modo de build.');
  } else {
    const directory = resolve(dirname(fileURLToPath(import.meta.url)), '../out');
    const result = await secureExport(directory, process.env.NEXT_PUBLIC_API_BASE_URL);
    console.log(`CSP gerada e verificada em ${result.files} HTML(s); ${result.changed} arquivo(s) atualizado(s).`);
  }
}
