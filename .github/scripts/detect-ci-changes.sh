#!/usr/bin/env bash
set -Eeuo pipefail

if [[ "$#" -eq 0 ]]; then
  echo "At least one watched path is required." >&2
  exit 2
fi

base_sha="${CI_BASE_SHA:-}"
head_sha="${CI_HEAD_SHA:-${GITHUB_SHA:-}}"
event_name="${CI_EVENT_NAME:-${GITHUB_EVENT_NAME:-}}"

write_result() {
  local run_value="$1"
  local reason="$2"

  if [[ -n "${GITHUB_OUTPUT:-}" ]]; then
    {
      echo "run=${run_value}"
      echo "reason=${reason}"
    } >>"$GITHUB_OUTPUT"
  fi

  echo "CI change detection: run=${run_value}, reason=${reason}"
}

fallback_to_full_verification() {
  local reason="$1"
  echo "Unable to determine a safe diff (${reason}); running full verification." >&2
  write_result "true" "$reason"
  exit 0
}

if [[ -z "$base_sha" || -z "$head_sha" ]]; then
  fallback_to_full_verification "missing-sha"
fi

if [[ "$base_sha" =~ ^0+$ || "$head_sha" =~ ^0+$ ]]; then
  fallback_to_full_verification "zero-sha"
fi

if ! git cat-file -e "${base_sha}^{commit}" 2>/dev/null; then
  fallback_to_full_verification "missing-base-object"
fi

if ! git cat-file -e "${head_sha}^{commit}" 2>/dev/null; then
  fallback_to_full_verification "missing-head-object"
fi

if [[ "$event_name" == "pull_request" ]]; then
  diff_range="${base_sha}...${head_sha}"
else
  diff_range="${base_sha}..${head_sha}"
fi

echo "Comparing ${diff_range} for watched paths: $*"
set +e
git diff --quiet "$diff_range" -- "$@"
diff_status="$?"
set -e

case "$diff_status" in
  0)
    write_result "false" "no-matched-paths"
    ;;
  1)
    write_result "true" "matched-paths"
    ;;
  *)
    fallback_to_full_verification "git-diff-error"
    ;;
esac
