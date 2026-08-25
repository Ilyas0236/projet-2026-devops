#!/bin/bash
# ═══════════════════════════════════════════════════════════
# QA AUDIT — Boutons morts : chaque handler (click) des templates
# doit exister comme méthode publique du composant TS correspondant.
# Rend les écarts handler utilisé mais non défini.
# ═══════════════════════════════════════════════════════════
cd "$(dirname "$0")/../wydad-frontend" || exit 1

FAIL=0
for html in $(find src/app/pages src/app/components src/app/layouts -name "*.html"); do
  ts="${html%.html}.ts"
  [ -f "$ts" ] || continue

  # Handlers extraits du template (forme simple methode(...) et methode($event))
  handlers=$(grep -oE '\(click\)="[a-zA-Z_][a-zA-Z0-9_]*\(' "$html" | sed 's/(click)="//;s/($//' | sort -u)
  for h in $handlers; do
    # méthode définie ? (signature TS: nom( ... ) { ou nom = () =>)
    if ! grep -qE "(public |private )?${h}\s*\(|${h}\s*=\s*(async )?\(" "$ts"; then
      echo "MORT: $h() appelé dans $html mais absent de ${ts##*/}"
      FAIL=1
    fi
  done
done
[ $FAIL -eq 0 ] && echo "OK — tous les handlers (click) sont définis"
exit $FAIL
