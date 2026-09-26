#!/usr/bin/env bash
set -euo pipefail

BASE_URL=${BASE_URL:-http://127.0.0.1:8080}
COUNT=${H03_USER_COUNT:-9000}
PASSWORD=${H03_USER_PASSWORD:-h03-user-password}
OUT=${LOAD_TOKENS_FILE:-load/h03/tokens.txt}
PARALLELISM=${H03_USER_PARALLELISM:-32}
mkdir -p "$(dirname "$OUT")"
tmp=$(mktemp)
trap 'rm -f "$tmp"' EXIT

export BASE_URL PASSWORD tmp
register_and_login() {
  local n="$1" user response token
  user=$(printf 'h03-user-%05d' "$n")
  curl -sS --max-time 20 -X POST "$BASE_URL/api/v1/auth/register" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$user\",\"password\":\"$PASSWORD\"}" >/dev/null || true
  response=$(curl -fsS --max-time 20 -X POST "$BASE_URL/api/v1/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"username\":\"$user\",\"password\":\"$PASSWORD\"}") || return 1
  token=$(printf '%s' "$response" | jq -er '.data.token') || return 1
  printf '%05d\t%s\n' "$n" "$token" >> "$tmp"
}
export -f register_and_login

seq 1 "$COUNT" | xargs -P "$PARALLELISM" -n 1 bash -c 'register_and_login "$1"' _
sort -n "$tmp" | cut -f2 > "$OUT"
actual=$(wc -l < "$OUT")
[[ "$actual" -eq "$COUNT" ]] || { echo "expected $COUNT tokens, got $actual" >&2; exit 1; }
echo "prepared $actual CUSTOMER JWTs in $OUT"
