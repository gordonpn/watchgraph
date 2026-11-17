#!/usr/bin/env bash
set -euo pipefail

fake_bin=$(mktemp -d)
trap 'rm -rf "$fake_bin"' EXIT
ln -s "$PWD/.github/scripts/fixtures/reviewer-curl.sh" "$fake_bin/curl"
export PATH="$fake_bin:$PATH"

check() {
  local expected=$1 diagnostic=$2 output status=0
  shift 2
  output=$(env "$@" bash .github/scripts/check-reviewer-credentials.sh 2>&1) || status=$?
  if [[ $status != "$expected" || $output != *"$diagnostic"* || $output == *test-credential* ]]; then
    printf 'Unexpected status or credential disclosure (exit %s)\n' "$status" >&2
    exit 1
  fi
}

check 1 'Set the DEEPSEEK_API_KEY' DEEPSEEK_API_KEY= DEEPSEEK_BASE_URL=https://api.deepseek.com CURL_STATUS=200
check 1 'Set the DeepSeek API base URL' DEEPSEEK_API_KEY=test-credential DEEPSEEK_BASE_URL= CURL_STATUS=200
for status in 401 403; do
  check 1 "rejected the repository credential" DEEPSEEK_API_KEY=test-credential DEEPSEEK_BASE_URL=https://api.deepseek.com CURL_STATUS="$status"
done
for status in 301 404 429 503; do
  check 1 "returned HTTP $status" DEEPSEEK_API_KEY=test-credential DEEPSEEK_BASE_URL=https://api.deepseek.com CURL_STATUS="$status"
done
check 1 "endpoint could not be reached" DEEPSEEK_API_KEY=test-credential DEEPSEEK_BASE_URL=https://api.deepseek.com CURL_EXIT=28 CURL_STATUS=000
check 0 "" DEEPSEEK_API_KEY=test-credential DEEPSEEK_BASE_URL=https://api.deepseek.com CURL_STATUS=200
printf 'Reviewer preflight cases passed\n'
