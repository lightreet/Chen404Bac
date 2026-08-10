#!/usr/bin/env bash

set -euo pipefail

ARTIFACT="${1:?Usage: deploy-backend.sh <artifact-path>}"
RELEASE_ROOT="/srv/chen404/backend/releases"
CURRENT_LINK="/srv/chen404/backend/current"
SERVICE_NAME="chen404bac"
HEALTH_CHECK_URL="http://127.0.0.1:10404/api/site/config"
HEALTH_CHECK_ATTEMPTS=30
HEALTH_CHECK_INTERVAL_SECONDS=2

NOW=$(date +%Y%m%d%H%M%S)
RELEASE_DIR="${RELEASE_ROOT}/${NOW}"
PREVIOUS_RELEASE=$(readlink -f "${CURRENT_LINK}" || true)

if [[ ! -f "${ARTIFACT}" ]]; then
  echo "Artifact not found: ${ARTIFACT}" >&2
  exit 1
fi

# Fail before switching releases if the uploaded JAR is incomplete.
unzip -tqq "${ARTIFACT}" >/dev/null

mkdir -p "${RELEASE_DIR}/logs"
cp "${ARTIFACT}" "${RELEASE_DIR}/chen404bac.jar"
chmod 777 "${RELEASE_DIR}/logs"

ln -sfn "${RELEASE_DIR}" "${CURRENT_LINK}"
sudo -n systemctl restart "${SERVICE_NAME}"

healthy=false
for ((attempt = 1; attempt <= HEALTH_CHECK_ATTEMPTS; attempt++)); do
  if curl --fail --silent --show-error \
    --connect-timeout 2 \
    --max-time 5 \
    "${HEALTH_CHECK_URL}" >/dev/null; then
    healthy=true
    break
  fi

  echo "Backend health check failed on attempt ${attempt}/${HEALTH_CHECK_ATTEMPTS}; retrying..."
  sleep "${HEALTH_CHECK_INTERVAL_SECONDS}"
done

if [[ "${healthy}" != "true" ]]; then
  echo "Backend health check failed, rolling back..." >&2
  sudo -n systemctl status "${SERVICE_NAME}" --no-pager -l || true
  sudo -n journalctl -u "${SERVICE_NAME}" -n 80 --no-pager || true

  if [[ -n "${PREVIOUS_RELEASE}" ]]; then
    ln -sfn "${PREVIOUS_RELEASE}" "${CURRENT_LINK}"
    sudo -n systemctl restart "${SERVICE_NAME}"
  fi

  exit 1
fi

echo "Backend deployed successfully: ${RELEASE_DIR}"
