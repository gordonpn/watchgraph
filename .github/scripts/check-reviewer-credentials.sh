#!/usr/bin/env bash
set -euo pipefail

if [[ -z ${DEEPSEEK_API_KEY:-} ]]; then
  echo '::error::Set the DEEPSEEK_API_KEY repository secret.'
  exit 1
fi

if [[ -z ${DEEPSEEK_BASE_URL:-} ]]; then
  echo '::error::Set the DeepSeek API base URL.'
  exit 1
fi

# Keep the bearer token out of process arguments and discard provider error bodies.
if ! status=$(printf 'Authorization: Bearer %s\n' "$DEEPSEEK_API_KEY" |
  curl --disable --silent --connect-timeout 5 --max-time 15 --proto '=https' \
    --header @- --output /dev/null --write-out '%{http_code}' \
    -- "${DEEPSEEK_BASE_URL%/}/models"); then
  echo '::error::The DeepSeek API endpoint could not be reached.'
  exit 1
fi

case "$status" in
  200) ;;
  401|403)
    echo '::error::DeepSeek rejected the repository credential.'
    exit 1
    ;;
  *)
    echo "::error::DeepSeek API credential check returned HTTP $status."
    exit 1
    ;;
esac
