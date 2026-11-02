#!/usr/bin/env bash
# Runs ON THE VPS (called by the GitHub Actions deploy job, or by hand).
# Pulls the newest backend image, restarts what changed and waits until the backend is healthy.
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/nihongo-app}"
COMPOSE=(docker compose -f docker-compose.prod.yml --env-file .env)
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-180}"

cd "$APP_DIR"

if [ ! -f .env ]; then
  echo "Missing $APP_DIR/.env (copy deploy/.env.prod.example and fill it in)" >&2
  exit 1
fi

echo "==> Pulling images"
"${COMPOSE[@]}" pull backend

echo "==> Starting stack"
"${COMPOSE[@]}" up -d --remove-orphans

echo "==> Waiting for the backend to become healthy (max ${HEALTH_TIMEOUT}s)"
backend_id="$("${COMPOSE[@]}" ps -q backend)"
elapsed=0
while true; do
  status="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$backend_id")"
  if [ "$status" = "healthy" ]; then
    break
  fi
  if [ "$elapsed" -ge "$HEALTH_TIMEOUT" ]; then
    echo "Backend did not become healthy (status: $status). Last logs:" >&2
    "${COMPOSE[@]}" logs --tail 60 backend >&2
    exit 1
  fi
  sleep 5
  elapsed=$((elapsed + 5))
done

echo "==> Cleaning old images"
docker image prune -f >/dev/null

echo "Deployed at $(date)"
