#!/usr/bin/env bash
set -euo pipefail

[[ $* == *https://api.deepseek.com/models* ]]
[[ $(cat) == 'Authorization: Bearer test-credential' ]]
printf '%s' "${CURL_STATUS:?}"
exit "${CURL_EXIT:-0}"
