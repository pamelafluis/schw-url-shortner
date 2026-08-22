#!/usr/bin/env bash
# Manual end-to-end demo of the step-4 API and step-5 cache against a real docker-compose stack.
# Usage: ./scripts/demo.sh          (brings the stack up, runs the demo, leaves it running)
#        ./scripts/demo.sh --down   (also tears the stack down afterwards)
set -euo pipefail
cd "$(dirname "$0")/.."

BASE_URL="http://localhost:8080"
API_KEY="dev-key"

hr() { printf '\n\033[1;36m== %s ==\033[0m\n' "$1"; }
req() { printf '\033[2m$ %s\033[0m\n' "$*"; }
pause() { read -r -p $'\033[2mPress Enter to continue...\033[0m' _ < /dev/tty; }
step() { hr "$1"; pause; }

hr "Starting app + Postgres (docker compose up -d --build)"
docker compose up -d --build

hr "Waiting for /actuator/health"
for i in $(seq 1 60); do
  code=$(curl -s -o /dev/null -w '%{http_code}' "$BASE_URL/actuator/health" || true)
  [ "$code" = "200" ] && break
  sleep 1
done
curl -s "$BASE_URL/actuator/health"; echo

step "Create a ShortLink"
req "POST $BASE_URL/api/v1/links  (X-API-Key: $API_KEY)"
CREATE_BODY=$(curl -s -X POST "$BASE_URL/api/v1/links" \
  -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
  -d '{"targetUrl":"https://example.com/some/long/path"}')
echo "$CREATE_BODY" | jq .
CODE=$(echo "$CREATE_BODY" | jq -r .code)

step "Create a ShortLink with a custom alias"
ALIAS="launch-demo-$(date +%s)"
req "POST $BASE_URL/api/v1/links  alias=$ALIAS"
curl -s -X POST "$BASE_URL/api/v1/links" \
  -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
  -d "{\"targetUrl\":\"https://example.com/launch\",\"alias\":\"$ALIAS\"}" | jq .

step "Create with that same alias again (expect 409, already taken)"
req "POST $BASE_URL/api/v1/links  alias=$ALIAS (duplicate)"
curl -s -i -X POST "$BASE_URL/api/v1/links" \
  -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
  -d "{\"targetUrl\":\"https://example.com/other\",\"alias\":\"$ALIAS\"}" | grep -E '^HTTP'
curl -s -X POST "$BASE_URL/api/v1/links" \
  -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
  -d "{\"targetUrl\":\"https://example.com/other\",\"alias\":\"$ALIAS\"}" | jq .

step "Resolve it (expect 302, Cache-Control: no-store)"
req "GET $BASE_URL/$CODE"
curl -s -i "$BASE_URL/$CODE" | grep -E '^(HTTP|Location|Cache-Control)'

step "Get metadata (expect 200)"
req "GET $BASE_URL/api/v1/links/$CODE  (X-API-Key: $API_KEY)"
curl -s "$BASE_URL/api/v1/links/$CODE" -H "X-API-Key: $API_KEY" | jq .

step "Get metadata without an API key (expect 401)"
req "GET $BASE_URL/api/v1/links/$CODE  (no key)"
curl -s -i "$BASE_URL/api/v1/links/$CODE" | grep -E '^HTTP'
curl -s "$BASE_URL/api/v1/links/$CODE" | jq .

step "Resolve it again, a few times (cache hit — no Postgres round trip; watch the timings drop)"
req "GET $BASE_URL/$CODE  x3, timed"
curl -s -o /dev/null -w "  resolve #1 (already cached from the step above): %{time_total}s, HTTP %{http_code}\n" "$BASE_URL/$CODE"
curl -s -o /dev/null -w "  resolve #2 (cache hit):                          %{time_total}s, HTTP %{http_code}\n" "$BASE_URL/$CODE"
curl -s -o /dev/null -w "  resolve #3 (cache hit):                          %{time_total}s, HTTP %{http_code}\n" "$BASE_URL/$CODE"

step "Resolve a never-issued code twice (negative caching — repeated scans stay off Postgres, per ADR-0004)"
NEG_CODE="cache-demo-neg-$(date +%s)"
req "GET $BASE_URL/$NEG_CODE  x2, timed"
curl -s -o /dev/null -w "  resolve #1 (cache miss -> Postgres, 404): %{time_total}s, HTTP %{http_code}\n" "$BASE_URL/$NEG_CODE"
curl -s -o /dev/null -w "  resolve #2 (negative cache hit, 404):     %{time_total}s, HTTP %{http_code}\n" "$BASE_URL/$NEG_CODE"

step "Create a link that expires in 5s, resolve it now, then again after it expires (cache HIT still re-checks expiry)"
SHORT_EXPIRES=$(date -u -d "+5 seconds" +%Y-%m-%dT%H:%M:%SZ)
req "POST $BASE_URL/api/v1/links  expiresAt=$SHORT_EXPIRES"
SHORT_BODY=$(curl -s -X POST "$BASE_URL/api/v1/links" \
  -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
  -d "{\"targetUrl\":\"https://example.com/short-lived\",\"expiresAt\":\"$SHORT_EXPIRES\"}")
echo "$SHORT_BODY" | jq .
SHORT_CODE=$(echo "$SHORT_BODY" | jq -r .code)
req "GET $BASE_URL/$SHORT_CODE  (before expiresAt, populates the cache)"
curl -s -i "$BASE_URL/$SHORT_CODE" | grep -E '^HTTP'
echo "  sleeping 6s, past expiresAt but well inside the cache's 60s TTL..."
sleep 6
req "GET $BASE_URL/$SHORT_CODE  (still cached, but expired — expect 410, not a stale 302)"
curl -s -o /dev/null -w "  %{time_total}s, HTTP %{http_code}\n" "$BASE_URL/$SHORT_CODE"

step "Deactivate it (expect 204)"
req "DELETE $BASE_URL/api/v1/links/$CODE  (X-API-Key: $API_KEY)"
curl -s -i -X DELETE "$BASE_URL/api/v1/links/$CODE" -H "X-API-Key: $API_KEY" | grep -E '^HTTP'

step "Resolve after deactivation, immediately (expect 410 — deactivate invalidates the local cache entry, no 60s wait needed)"
req "GET $BASE_URL/$CODE"
curl -s -i "$BASE_URL/$CODE" | grep -E '^HTTP'
curl -s "$BASE_URL/$CODE" | jq .

step "Resolve a code that was never issued (expect 404)"
req "GET $BASE_URL/nvrissued"
curl -s -i "$BASE_URL/nvrissued" | grep -E '^HTTP'
curl -s "$BASE_URL/nvrissued" | jq .

step "Create with an SSRF-blocked target (expect 400)"
req "POST $BASE_URL/api/v1/links  target=http://169.254.169.254/latest/meta-data"
curl -s -i -X POST "$BASE_URL/api/v1/links" \
  -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
  -d '{"targetUrl":"http://169.254.169.254/latest/meta-data"}' | grep -E '^HTTP'
curl -s -X POST "$BASE_URL/api/v1/links" \
  -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
  -d '{"targetUrl":"http://169.254.169.254/latest/meta-data"}' | jq .

step "Create with a reserved alias (expect 409)"
req "POST $BASE_URL/api/v1/links  alias=health"
curl -s -X POST "$BASE_URL/api/v1/links" \
  -H "X-API-Key: $API_KEY" -H 'Content-Type: application/json' \
  -d '{"targetUrl":"https://example.com/x","alias":"health"}' | jq .

if [ "${1:-}" = "--down" ]; then
  hr "Tearing down (docker compose down)"
  docker compose down
else
  hr "Done. Stack is still running — 'docker compose down' when you're finished."
fi
