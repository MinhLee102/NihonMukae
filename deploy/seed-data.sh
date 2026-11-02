#!/usr/bin/env bash
# Loads the dictionary into the production database. Run ON THE VPS, once, after the first deploy
# (the backend has to be up so Flyway has created the tables).
#
#   bash deploy/seed-data.sh /path/to/data
#
# The data folder must contain (they are too big and not licensed for git, upload them with scp):
#   mazii_vocabs.csv  meaning_detail.csv  examples.csv  kanji.csv   (Mazii export)
#   JMdict_e                                                         (optional, word frequency)
#
# Nothing is installed on the server: the Python scripts run inside a throwaway container
# that joins the compose network and talks to Postgres directly.
set -euo pipefail

DATA_DIR="${1:?usage: seed-data.sh <data-dir>}"
APP_DIR="${APP_DIR:-/opt/nihongo-app}"
NETWORK="${COMPOSE_NETWORK:-nihongo-app_default}"

cd "$APP_DIR"
set -a
# shellcheck disable=SC1091
. ./.env
set +a

for f in mazii_vocabs.csv meaning_detail.csv examples.csv kanji.csv; do
  [ -f "$DATA_DIR/$f" ] || { echo "Missing $DATA_DIR/$f" >&2; exit 1; }
done

DB_URL="postgresql://${POSTGRES_USER}:${POSTGRES_PASSWORD}@postgres:5432/${POSTGRES_DB}"

run_py() {
  docker run --rm --network "$NETWORK" \
    -v "$APP_DIR/scripts:/work/scripts:ro" \
    -v "$(cd "$DATA_DIR" && pwd):/data:ro" \
    -w /work \
    -e PYTHONUNBUFFERED=1 -e PYTHONDONTWRITEBYTECODE=1 \
    -e MAZII_DB_URL="$DB_URL" -e MAZII_DATA_DIR=/data \
    python:3.12-slim sh -c "$1"
}

echo "==> Importing Mazii data (skipped automatically if the words table already has rows)"
run_py "pip install -q -r scripts/mazii/requirements.txt && python scripts/mazii/ensure_seeded.py"

if [ -f "$DATA_DIR/JMdict_e" ]; then
  echo "==> Backfilling word frequency from JMdict"
  run_py "pip install -q -r scripts/jmdict/requirements.txt && python scripts/jmdict/backfill_frequency.py --jmdict-path /data/JMdict_e --db-url '$DB_URL'"
else
  echo "==> JMdict_e not found, skipping the frequency backfill (search ranking will be less accurate)"
fi

echo "Done."
