#!/usr/bin/env bash
set -Eeuo pipefail

frontend_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
run_id="${GITHUB_RUN_ID:-local}-$$"
network_name="cinewise-production-smoke-${run_id}"
backend_container="cinewise-production-backend-${run_id}"
frontend_container="cinewise-production-frontend-${run_id}"
frontend_image="cinewise-frontend-production-smoke:local"
host_port="${PRODUCTION_NGINX_PORT:-18080}"

cleanup() {
  docker rm --force "$frontend_container" "$backend_container" >/dev/null 2>&1 || true
  docker network rm "$network_name" >/dev/null 2>&1 || true
}
trap cleanup EXIT

cd "$frontend_dir"
docker network create "$network_name" >/dev/null

# The stub uses the same Docker network alias as production. Its marker header
# proves that /api/** reached an upstream instead of the SPA fallback.
docker run --detach \
  --name "$backend_container" \
  --network "$network_name" \
  --network-alias backend \
  node:24.18.0-alpine \
  node -e '
    const http = require("node:http");
    const body = JSON.stringify({
      code: 0,
      message: "OK",
      data: { records: [], page: 1, size: 20, total: 0 },
      traceId: "production-nginx-smoke",
    });
    http.createServer((_request, response) => {
      response.writeHead(200, {
        "Content-Type": "application/json; charset=utf-8",
        "X-CineWise-Api-Stub": "true",
      });
      response.end(body);
    }).listen(8080, "0.0.0.0");
  ' >/dev/null

docker build --progress=plain --tag "$frontend_image" .
docker run --detach \
  --name "$frontend_container" \
  --network "$network_name" \
  --publish "127.0.0.1:${host_port}:80" \
  "$frontend_image" >/dev/null

for attempt in $(seq 1 30); do
  if curl --fail --silent --show-error "http://127.0.0.1:${host_port}/index.html" >/dev/null; then
    break
  fi

  if [[ "$attempt" -eq 30 ]]; then
    docker logs "$frontend_container"
    exit 1
  fi

  sleep 2
done

PLAYWRIGHT_BASE_URL="http://127.0.0.1:${host_port}" \
  pnpm exec playwright test e2e/production-nginx.spec.ts \
  --config=playwright.production.config.ts

# The one-time distanceContextId is carried in the upload path. Exercise the
# real production Nginx image, then prove neither the ID nor query string was
# written to its access log. The upstream response is irrelevant to logging.
privacy_canary_uuid="11111111-2222-4333-8444-555555555555"
privacy_canary_query="privacy-canary=distance-context"
privacy_redacted_path="/api/v1/recommendation/distance-contexts/[redacted]/location"

curl --silent --show-error \
  --request POST \
  --header "Content-Type: application/json" \
  --data '{"longitude":112.9388,"latitude":28.2282}' \
  "http://127.0.0.1:${host_port}/api/v1/recommendation/distance-contexts/${privacy_canary_uuid}/location?${privacy_canary_query}" \
  >/dev/null

frontend_logs="$(docker logs "$frontend_container" 2>&1)"
if grep --fixed-strings --quiet "$privacy_canary_uuid" <<<"$frontend_logs"; then
  echo "Frontend Nginx access log leaked distanceContextId." >&2
  exit 1
fi
if grep --fixed-strings --quiet "$privacy_canary_query" <<<"$frontend_logs"; then
  echo "Frontend Nginx access log leaked distance upload query parameters." >&2
  exit 1
fi
if ! grep --fixed-strings --quiet "$privacy_redacted_path" <<<"$frontend_logs"; then
  echo "Frontend Nginx access log did not contain the expected redacted path." >&2
  exit 1
fi

echo "Frontend Nginx privacy log sample:"
grep --fixed-strings "$privacy_redacted_path" <<<"$frontend_logs" | tail -n 1
