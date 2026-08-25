#!/usr/bin/env bash
# Sauvegarde PostgreSQL — toutes les bases du conteneur wydad-postgres.
# Appelé par cron sur la VM Azure (voir scripts/install-backup-cron.sh).
# Garde 7 jours de backups dans ~/backups, compresse en gzip.
set -euo pipefail

BACKUP_DIR="$HOME/backups"
STAMP=$(date +%Y%m%d-%H%M%S)
KEEP_DAYS=7

mkdir -p "$BACKUP_DIR"
OUT="$BACKUP_DIR/pg-all-$STAMP.sql.gz"

docker exec wydad-postgres pg_dumpall -U "${POSTGRES_USER:-wydad}" | gzip > "$OUT"

# Purge des backups plus vieux que KEEP_DAYS jours
find "$BACKUP_DIR" -name 'pg-all-*.sql.gz' -mtime +$KEEP_DAYS -delete

echo "$(date '+%F %T') backup OK -> $OUT ($(du -h "$OUT" | cut -f1))" >> "$HOME/backups/backup.log"
