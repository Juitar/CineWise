import { createReadStream } from 'node:fs';
import { stat } from 'node:fs/promises';
import { createServer } from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const distDirectory = path.resolve(scriptDirectory, '..', 'dist');
const host = '127.0.0.1';
const port = 4173;
const contentTypes = new Map([
  ['.css', 'text/css; charset=utf-8'],
  ['.html', 'text/html; charset=utf-8'],
  ['.ico', 'image/x-icon'],
  ['.js', 'text/javascript; charset=utf-8'],
  ['.json', 'application/json; charset=utf-8'],
  ['.png', 'image/png'],
  ['.svg', 'image/svg+xml'],
  ['.webp', 'image/webp'],
]);

const fileExists = async (filePath) => {
  try {
    return (await stat(filePath)).isFile();
  } catch {
    return false;
  }
};

export const server = createServer(async (request, response) => {
  const requestUrl = new URL(request.url ?? '/', `http://${host}:${port}`);
  let pathname;
  try {
    pathname = decodeURIComponent(requestUrl.pathname);
  } catch {
    response.writeHead(400).end('Bad Request');
    return;
  }

  const relativePath = pathname.replace(/^\/+/, '');
  const requestedPath = path.resolve(distDirectory, relativePath || 'index.html');
  const relativeToDist = path.relative(distDirectory, requestedPath);
  if (relativeToDist.startsWith('..') || path.isAbsolute(relativeToDist)) {
    response.writeHead(403).end('Forbidden');
    return;
  }

  const requestedFileExists = await fileExists(requestedPath);
  if (!requestedFileExists && path.extname(relativePath)) {
    response.writeHead(404).end('Not Found');
    return;
  }

  const filePath = requestedFileExists ? requestedPath : path.join(distDirectory, 'index.html');
  const contentType =
    contentTypes.get(path.extname(filePath).toLowerCase()) ?? 'application/octet-stream';
  response.writeHead(200, {
    'Cache-Control': 'no-store',
    'Content-Type': contentType,
  });
  createReadStream(filePath).pipe(response);
});

export const startProductionBuildServer = () =>
  new Promise((resolve, reject) => {
    const handleError = (error) => reject(error);
    server.once('error', handleError);
    server.listen(port, host, () => {
      server.off('error', handleError);
      console.log(`生产构建预览已启动：http://${host}:${port}`);
      resolve(server);
    });
  });
