#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)
PROJECT=${H03_COMPOSE_PROJECT:-flashsale-h03}
BASE_URL=${BASE_URL:-http://127.0.0.1:8080}
COMPOSE=(docker compose -p "$PROJECT" -f "$ROOT_DIR/docker-compose.yml")

usage() {
  cat <<'EOF'
Usage: run.sh <up|down|prepare|run|verify>

Environment:
  H03_COMPOSE_PROJECT  Compose project name (default: flashsale-h03)
  H03_SCENARIO         k6 scenario for run (default: product_read_cold)
  H03_SERVICES         Space separated extra services for up
  K6_IMAGE             k6 image (default: ghcr.io/grafana/k6:latest)
  H03_RESULTS_DIR      Result directory (default: load/h03/results/<timestamp>)

The script never touches the acceptance project or its named volumes. `down`
removes only this Compose project's temporary volumes.
EOF
}

base_services=(postgres redis nacos migration auth gateway)

up() {
  local services=("${base_services[@]}")
  case "${H03_SCENARIO:-product_read_cold}" in
    activity_burst|activity_limit) services+=(product activity order inventory rocketmq-namesrv rocketmq-broker rocketmq-init) ;;
    direct_purchase|duplicate_order) services+=(product order inventory rocketmq-namesrv rocketmq-broker rocketmq-init) ;;
    coupon_claim) services+=(coupon rocketmq-namesrv rocketmq-broker rocketmq-init) ;;
    payment_cancel) services+=(product order payment inventory rocketmq-namesrv rocketmq-broker rocketmq-init) ;;
    product_read*|*) services+=(product) ;;
  esac
  read -r -a extras <<< "${H03_SERVICES:-}"
  services+=("${extras[@]}")
  "${COMPOSE[@]}" up -d "${services[@]}"
}

down() { "${COMPOSE[@]}" down --volumes --remove-orphans; }

prepare() {
  command -v curl >/dev/null || { echo 'curl is required' >&2; exit 2; }
  command -v jq >/dev/null || { echo 'jq is required' >&2; exit 2; }
  : "${ADMIN_USERNAME:=admin}"; : "${ADMIN_PASSWORD:=local-admin-password}"
  local auth body operator product activity now later
  body=$(jq -nc --arg u "$ADMIN_USERNAME" --arg p "$ADMIN_PASSWORD" '{username:$u,password:$p}')
  auth=$(curl -fsS -X POST "$BASE_URL/api/v1/auth/login" -H 'Content-Type: application/json' -d "$body" | jq -r '.data.token // empty')
  [[ -n "$auth" ]] || { echo 'admin login failed' >&2; exit 1; }
  operator=$(curl -fsS -X POST "$BASE_URL/api/v1/admin/users" -H "Authorization: Bearer $auth" -H 'Content-Type: application/json' \
    -d '{"username":"h03-operator","password":"h03-operator-password","role":"OPERATOR"}')
  product=$(curl -fsS -X POST "$BASE_URL/api/v1/admin/products" -H "Authorization: Bearer $auth" -H 'Content-Type: application/json' \
    -d '{"sku":"H03-READ-001","name":"H03 Read Product","description":"H03 load product","list_price_minor":1999,"available_stock":100000}' | jq -r '.data.id')
  curl -fsS -X POST "$BASE_URL/api/v1/admin/products/$product/on-sale" -H "Authorization: Bearer $auth" >/dev/null
  now=$(date -u -d '-1 minute' +%Y-%m-%dT%H:%M:%SZ); later=$(date -u -d '+2 hours' +%Y-%m-%dT%H:%M:%SZ)
  activity=$(curl -fsS -X POST "$BASE_URL/api/v1/admin/activities" -H "Authorization: Bearer $auth" -H 'Content-Type: application/json' \
    -d "$(jq -nc --arg n 'H03 Burst Activity' --arg s "$now" --arg e "$later" --argjson p "$product" '{name:$n,product_id:$p,sale_price_minor:999,initial_stock:300,purchase_limit_per_user:2,starts_at:$s,ends_at:$e}')" | jq -r '.data.id')
  curl -fsS -X POST "$BASE_URL/api/v1/admin/activities/$activity/start" -H "Authorization: Bearer $auth" >/dev/null
  jq -n --arg operator "$operator" --argjson product "$product" --argjson activity "$activity" '{product_id:$product,activity_id:$activity}' > "${H03_DATA_FILE:-$ROOT_DIR/load/h03/data.json}"
  echo "Prepared product=$product activity=$activity; register CUSTOMER users separately and store JWTs in LOAD_TOKENS_FILE."
}

run_k6() {
  local results=${H03_RESULTS_DIR:-$ROOT_DIR/load/h03/results/$(date -u +%Y%m%dT%H%M%SZ)}
  mkdir -p "$results"
  local image=${K6_IMAGE:-ghcr.io/grafana/k6:latest}
  local load_tokens=${LOAD_TOKENS:-}
  if [[ -n "${LOAD_TOKENS_FILE:-}" ]]; then
    load_tokens=$(tr '\n' ',' < "$LOAD_TOKENS_FILE" | sed 's/,$//')
  fi
  docker run --rm --network host -e BASE_URL="$BASE_URL" -e SCENARIO="${H03_SCENARIO:-product_read_cold}" \
    -e PRODUCT_ID="${PRODUCT_ID:-1}" -e ACTIVITY_ID="${ACTIVITY_ID:-1}" \
    -e LOAD_TOKEN="${LOAD_TOKEN:-}" -e LOAD_TOKENS="$load_tokens" \
    -v "$ROOT_DIR/load/k6:/scripts:ro" -v "$results:/results" "$image" run --summary-export /results/summary-k6.json /scripts/h03.js
}

verify() {
  "${COMPOSE[@]}" exec -T postgres psql -U "${POSTGRES_USER:-flashsale}" -d "${POSTGRES_DB:-flashsale}" -f /dev/stdin < "$ROOT_DIR/load/h03/verify.sql"
}

case "${1:-}" in
  up) up ;; down) down ;; prepare) prepare ;; run) run_k6 ;; verify) verify ;; *) usage; exit 2 ;;
esac
