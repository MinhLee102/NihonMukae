#!/usr/bin/env bash
# One-time setup of a fresh Ubuntu 22.04 / 24.04 VPS: Docker, firewall, swap, app folder.
#
#   curl -fsSL https://raw.githubusercontent.com/<user>/<repo>/main/deploy/setup-vps.sh | sudo bash
# or copy the file to the server and run:   sudo bash setup-vps.sh
#
# Run it as root (sudo). It is safe to run again.
set -euo pipefail

APP_DIR="${APP_DIR:-/opt/nihongo-app}"
APP_USER="${SUDO_USER:-${USER}}"

if [ "$(id -u)" -ne 0 ]; then
  echo "Run as root: sudo bash $0" >&2
  exit 1
fi

echo "==> Installing Docker"
if ! command -v docker >/dev/null 2>&1; then
  curl -fsSL https://get.docker.com | sh
fi
systemctl enable --now docker
usermod -aG docker "$APP_USER"

echo "==> Firewall: allow SSH, HTTP, HTTPS only"
if command -v ufw >/dev/null 2>&1; then
  ufw allow OpenSSH
  ufw allow 80/tcp
  ufw allow 443/tcp
  ufw --force enable
fi

echo "==> Swap (Java, Postgres and Redis together are tight on a 1 GB server)"
if [ "$(swapon --show --noheadings | wc -l)" -eq 0 ]; then
  fallocate -l 2G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >> /etc/fstab
fi

echo "==> App folder $APP_DIR"
mkdir -p "$APP_DIR"
chown -R "$APP_USER":"$APP_USER" "$APP_DIR"

cat <<EOF

Done. Next steps:
  1. Log out and back in so the docker group applies to $APP_USER.
  2. Create $APP_DIR/.env from deploy/.env.prod.example and fill in the secrets:
       nano $APP_DIR/.env
  3. Add the GitHub secrets (VPS_HOST, VPS_USER, VPS_SSH_KEY) and push to main.
EOF
