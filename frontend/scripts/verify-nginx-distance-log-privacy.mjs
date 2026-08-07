import { readFileSync } from 'node:fs';

const config = readFileSync(new URL('../nginx.conf', import.meta.url), 'utf8');
const protectedPath =
  '/api/v1/recommendation/distance-contexts/11111111-2222-4333-8444-555555555555/location';
const protectedQuery = 'privacy-canary=distance-context';
const redactedPath = '/api/v1/recommendation/distance-contexts/[redacted]/location';

function assertConfig(condition, message) {
  if (!condition) throw new Error(`Nginx 距离上传日志隐私检查失败：${message}`);
}

const targetMap = config.match(/map\s+\$uri\s+\$privacy_request_target\s*\{(?<body>[\s\S]*?)\n\}/);
const refererMap = config.match(/map\s+\$uri\s+\$privacy_referer\s*\{(?<body>[\s\S]*?)\n\}/);
const logFormat = config.match(/log_format\s+cinewise_privacy(?<body>[\s\S]*?);/);

assertConfig(targetMap?.groups?.body, '缺少 privacy_request_target map');
assertConfig(
  targetMap.groups.body.includes('~^/api/v1/recommendation/distance-contexts/[^/]+/location$'),
  '没有匹配距离上传路径',
);
assertConfig(targetMap.groups.body.includes(redactedPath), '没有使用固定脱敏路径');
assertConfig(!targetMap.groups.body.includes('$args'), '受保护目标仍拼接查询参数');
assertConfig(
  refererMap?.groups?.body?.includes('distance-contexts/[^/]+/location$ "-"'),
  '距离上传请求没有隐藏 Referer',
);
assertConfig(logFormat?.groups?.body, '缺少 cinewise_privacy log_format');
assertConfig(logFormat.groups.body.includes('$privacy_request_target'), '日志没有使用脱敏目标');
assertConfig(!/\$request(?!_)/.test(logFormat.groups.body), '日志仍引用包含原始 URI 的 $request');
assertConfig(
  /access_log\s+\/var\/log\/nginx\/access\.log\s+cinewise_privacy\s*;/.test(config),
  'server 没有覆盖继承的默认 access log',
);

const protectedPattern = /^\/api\/v1\/recommendation\/distance-contexts\/[^/]+\/location$/;
const requestTarget = protectedPattern.test(protectedPath)
  ? redactedPath
  : `${protectedPath}?${protectedQuery}`;
const sample = `127.0.0.1 - - [07/Aug/2026:15:00:00 +0800] "POST ${requestTarget} HTTP/1.1" 502 0 "-" "privacy-test"`;

assertConfig(!sample.includes('11111111-2222-4333-8444-555555555555'), '样例日志包含 canary UUID');
assertConfig(!sample.includes(protectedQuery), '样例日志包含查询参数');

console.log(`Nginx 距离上传日志隐私检查通过：${sample}`);
