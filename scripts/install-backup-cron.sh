#!/usr/bin/env bash
# Installe le cron de sauvegarde DB sur la VM Azure (à lancer UNE fois sur la VM).
# Cron : tous les jours à 04h17 (heure VM), script scripts/backup-db.sh du repo.
set -euo pipefail
REPO_DIR="$(cd "$(dirname "$0")/.." && pwd)"
chmod +x "$REPO_DIR/scripts/backup-db.sh"

CRON_LINE="17 4 * * * $REPO_DIR/scripts/backup-db.sh >> $HOME/backups/cron.log 2>&1"

# grep -v sort en 1 si le crontab est vide/sans la ligne : ne pas laisser
# set -e tuer l'installation dans ce cas (premiere installation).
( { crontab -l 2>/dev/null || true; } | { grep -v 'backup-db.sh' || true; } ; echo "$CRON_LINE" ) | crontab -
echo "Cron installé : $CRON_LINE"
crontab -l | grep backup
