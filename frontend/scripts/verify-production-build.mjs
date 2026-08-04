import { access, readdir, readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = path.dirname(fileURLToPath(import.meta.url));
const distDirectory = path.resolve(scriptDirectory, '..', 'dist');
const indexPath = path.join(distDirectory, 'index.html');
const hashPattern = /(?:^|[.-])[a-f0-9]{8,}(?=[.-])/i;

const indexHtml = await readFile(indexPath, 'utf8');
const scriptReferences = [
  ...indexHtml.matchAll(/<script\b[^>]*\bsrc=["']([^"']+)["'][^>]*>/gi),
].map((match) => match[1]);
const styleReferences = [
  ...indexHtml.matchAll(/<link\b[^>]*\bhref=["']([^"']+\.css(?:[?#][^"']*)?)["'][^>]*>/gi),
].map((match) => match[1]);
const localAssets = [...scriptReferences, ...styleReferences].filter(
  (reference) =>
    !/^(?:[a-z][a-z\d+.-]*:|\/\/|#)/i.test(reference) && /\.(?:js|css)(?:[?#]|$)/i.test(reference),
);

if (scriptReferences.length === 0) {
  throw new Error('dist/index.html 没有入口 JS。');
}

const entryScript = scriptReferences.find((reference) =>
  /^\/?umi(?:[.-][a-f0-9]{8,})\.js(?:[?#].*)?$/i.test(reference),
);
if (!entryScript) {
  throw new Error(`入口 JS 未带内容哈希：${scriptReferences.join(', ')}`);
}

for (const reference of localAssets) {
  const pathname = decodeURIComponent(reference.split(/[?#]/, 1)[0]).replace(/^\/+/, '');
  const assetPath = path.resolve(distDirectory, pathname);
  const relativePath = path.relative(distDirectory, assetPath);
  if (relativePath.startsWith('..') || path.isAbsolute(relativePath)) {
    throw new Error(`HTML 资源路径超出 dist：${reference}`);
  }
  try {
    await access(assetPath);
  } catch {
    throw new Error(`HTML 引用了不存在的资源：${reference}`);
  }
}

const collectAssets = async (directory) => {
  const entries = await readdir(directory, { withFileTypes: true });
  const assets = [];
  for (const entry of entries) {
    const entryPath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      assets.push(...(await collectAssets(entryPath)));
    } else if (/\.(?:js|css)$/i.test(entry.name)) {
      assets.push(entryPath);
    }
  }
  return assets;
};

const productionAssets = await collectAssets(distDirectory);
const assetsWithoutHash = productionAssets
  .filter((assetPath) => !hashPattern.test(path.basename(assetPath)))
  .map((assetPath) => path.relative(distDirectory, assetPath).replaceAll('\\', '/'));

if (assetsWithoutHash.length > 0) {
  throw new Error(`以下生产 JS/CSS 文件未带内容哈希：${assetsWithoutHash.join(', ')}`);
}

console.log(
  `生产构建检查通过：入口 ${entryScript}，HTML 引用 ${localAssets.length} 个本地 JS/CSS，` +
    `dist 共 ${productionAssets.length} 个带哈希 JS/CSS。`,
);
