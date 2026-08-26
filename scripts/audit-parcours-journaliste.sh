#!/bin/bash
# Audit parcours JOURNALISTE (B.27 E2E complet) — appelé sur la VM
# Endpoints utilisés par l'espace journaliste : accréditation, articles, interviews.

source /tmp/audit-tokens.sh
PID=112
EMAIL=$EMAIL_PR
TOK=$PTOK
BASE=http://localhost:8080
PASS=0
FAIL=0
declare -a BUGS

call() {
  local label="$1" method="$2" url="$3" data="$4" expect="$5"
  if [ -n "$data" ]; then
    code=$(curl -s -o /tmp/r -w "%{http_code}" -X $method -H "Authorization: Bearer $TOK" -H "Content-Type: application/json" -d "$data" "$BASE$url")
  else
    code=$(curl -s -o /tmp/r -w "%{http_code}" -X $method -H "Authorization: Bearer $TOK" "$BASE$url")
  fi
  if [ "$code" = "$expect" ]; then
    echo "  [OK  $code] $label"
    PASS=$((PASS+1))
  else
    echo "  [FAIL $code, attendu $expect] $label"
    BUGS+=("[$code/$expect] $method $url : $(head -c 200 /tmp/r 2>/dev/null)")
    FAIL=$((FAIL+1))
  fi
}

echo "============================================="
echo "AUDIT PARCOURS JOURNALISTE ($EMAIL, userId=$PID)"
echo "============================================="

echo "--- CONTENU (lecture publique + presse) ---"
call "Articles"                      GET  /api/content/articles "" 200
call "Article 1"                     GET  /api/content/articles/1 "" 200
call "Articles sport FOOTBALL"       GET  /api/content/articles/sport/FOOTBALL "" 200
call "Matchs"                        GET  /api/content/matches "" 200
call "Match 5"                       GET  /api/content/matches/mine "" 200
call "Matchs PROGRAMME"              GET  /api/content/matches/statut/PROGRAMME "" 200
call "Classement Botola"             GET  /api/content/classements/Botola "" 200
call "Tous classements"              GET  /api/content/classements "" 200
call "Trophées public"               GET  /api/content/trophies/public "" 200
call "Légendes public"               GET  /api/content/legends/public "" 200
call "Sponsors public"               GET  /api/content/sponsors/public "" 200
call "Médias back-office (admin)"    GET  /api/content/media "" 403   # journaliste ≠ admin
call "Médias publics par nom"        GET  /api/content/media/sample.jpg "" 404  # pas de média, normal
call "Settings"                      GET  /api/content/settings "" 200
call "Rapports financiers"           GET  /api/content/rapports-financiers "" 200
call "Réclamations"                  GET  /api/content/reclamations "" 403   # admin only
call "Mes réclamations"              GET  /api/content/reclamations/mine "" 200

echo "--- ACCRÉDITATION (B.17) ---"
call "Mes bulletins de paie (presse?)" GET /api/auth/salary-receipts/mine "" 200
call "Mon attestation"               GET  "/api/auth/attestation?email=$EMAIL" "" 200
call "Mon badge presse"              GET  "/api/auth/presse/badge?email=$EMAIL" "" 200

echo "--- MESSAGERIE PRESSE (B.5) ---"
call "Inbox"                         GET  /api/sports/messaging/inbox "" 200
call "Annonces"                      GET  /api/sports/messaging/announcements "" 200

echo "--- ÉLECTIONS / SONDAGES (B.8) ---"
call "Élections publiées"            GET  /api/elections/published "" 200
call "Dernière élection"             GET  /api/elections/published/latest "" 204   # 204 NoContent si aucune élection "latest" publiée
call "Sondages actifs"               GET  /api/polls/active "" 200

echo "--- BILLETTERIE / BOUTIQUE ---"
call "Catalogue events"              GET  /api/ticket/events "" 200
call "Mon panier"                    GET  /api/shop/cart "" 200
call "Mes commandes"                 GET  /api/shop/orders "" 200
call "Mon solde e-cash"              GET  /api/payment/balance "" 200

echo "--- GAMIFICATION (lecture seule) ---"
call "Catalogue badges"              GET  /api/gamification/badges "" 200
call "Mes badges"                    GET  /api/gamification/badges/user/$PID "" 200
call "Mes points"                    GET  /api/gamification/points/$PID "" 200
call "Leaderboard"                   GET  /api/gamification/leaderboard "" 200

echo "--- NOTIFICATIONS ---"
call "Mes notifs"                    GET  /api/notification/user/$PID "" 200
call "Compteur non-lus"              GET  /api/notification/user/$PID/unread/count "" 200
call "Mes préférences"               GET  /api/notification/preferences "" 200

echo "--- SPORTS (en tant que presse, pas staff) ---"
call "Liste joueurs FOOTBALL SENIOR" GET  "/api/sports/players/filter?sportType=FOOTBALL&category=SENIOR" "" 403
call "Liste staff FOOTBALL SENIOR"   GET  "/api/sports/staff/filter?sportType=FOOTBALL&category=SENIOR" "" 403   # journaliste n'appartient à aucune équipe
call "Mon profil staff (pas staff)"  GET  /api/sports/staff/user/$PID "" 403
call "Mes convocations (pas joueur)" GET  /api/sports/my-space/convocations "" 403
call "Mes stats joueur (pas joueur)" GET  /api/sports/my-space/stats "" 403

echo "--- CONVOCATIONS MATCH (lecture publique) ---"
call "Convocations publiques match 5" GET /api/sports/match-convocations/public/match/5 "" 404   # aucune convocation publiée pour match 5
call "Mes convocations (pas joueur)"  GET /api/sports/match-convocations/my "" 403

echo ""
echo "============================================="
echo "RÉSUMÉ JOURNALISTE: $PASS OK, $FAIL FAIL"
echo "============================================="
if [ $FAIL -gt 0 ]; then
  echo "Bugs trouvés:"
  for b in "${BUGS[@]}"; do echo "  $b"; done
fi
