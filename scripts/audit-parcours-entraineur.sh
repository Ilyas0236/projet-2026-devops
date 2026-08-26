#!/bin/bash
# Audit parcours ENTRAINEUR (B.27 E2E complet) — appelé sur la VM
# Tous les endpoints staff du frontend utilisés par l'espace entraîneur.

source /tmp/audit-tokens.sh
EID=111
EMAIL=$EMAIL_E
TOK=$ETOK
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
echo "AUDIT PARCOURS ENTRAINEUR ($EMAIL, userId=$EID)"
echo "============================================="

echo "--- ESPACE ENTRAINEUR (my-space staff) ---"
call "Mes convocations match"        GET  /api/sports/match-convocations/my "" 200
call "Sélect° joueurs pour match 5"  GET  /api/sports/match-convocations/match/5/selectable "" 200
call "Liste séances"                 GET  /api/sports/sessions/filter?sportType=FOOTBALL\&category=SENIOR "" 200
call "Mes joueurs de l'équipe"       GET  /api/sports/players/filter?sportType=FOOTBALL\&category=SENIOR "" 200
call "Mon staff encadrant"           GET  /api/sports/staff/filter?sportType=FOOTBALL\&category=SENIOR "" 200
call "Mon profil staff"              GET  /api/sports/staff/user/$EID "" 200
call "Tous les staff (ADMIN)"        GET  /api/sports/staff "" 403   # entraîneur ≠ ADMIN, normal
call "Stats joueur userId 110"       GET  /api/sports/my-space/staff/stats?joueurUserId=110 "" 200

echo "--- CONVOCATIONS DE MATCH (création / publication) ---"
call "Soumettre feuille match 5"     POST /api/sports/match-convocations/match/5/submit "" 200
call "Liste submitted"               GET  /api/sports/match-convocations/admin/submitted "" 403  # pas ADMIN
call "Convocations match 5"          GET  /api/sports/match-convocations/match/5 "" 200

echo "--- MESSAGERIE (B.5) ---"
call "Inbox"                         GET  /api/sports/messaging/inbox "" 200
call "Annonces"                      GET  /api/sports/messaging/announcements "" 200

echo "--- CHAT DE GROUPE (équipe) ---"
call "Membres équipe"                GET  /api/sports/team-chat/FOOTBALL/SENIOR/members "" 200
call "Messages groupe"               GET  /api/sports/team-chat/FOOTBALL/SENIOR/messages "" 200

echo "--- MÉDIAS D'ÉQUIPE (B.7) ---"
call "Médias envoyés"                GET  /api/sports/my-space/staff/media/sent "" 200

echo "--- APPELS LIVEKIT (B.12) ---"
call "Mes appels"                    GET  /api/sports/calls/mine "" 200
call "Statut média"                  GET  /api/sports/calls/media-status "" 200

echo "--- E-CASH (B.10) ---"
call "Mon solde"                     GET  /api/payment/balance "" 200
call "Mes transactions"              GET  /api/payment/transactions "" 200

echo "--- BILLETTERIE / BOUTIQUE (consulte seulement) ---"
call "Catalogue events"              GET  /api/ticket/events "" 200
call "Mon panier"                    GET  /api/shop/cart "" 200
call "Mes commandes"                 GET  /api/shop/orders "" 200

echo "--- ESPACE JOUEUR (my-space) ---"
call "Mes convocations (espace)"     GET  /api/sports/my-space/convocations "" 200
call "Ma présence"                   GET  /api/sports/my-space/presence "" 200
call "Mes documents"                 GET  /api/sports/my-space/documents "" 200
call "Mes stats"                     GET  /api/sports/my-space/stats "" 200

echo "--- NOTIFICATIONS (B.11) ---"
call "Mes notifs"                    GET  /api/notification/user/$EID "" 200
call "Compteur non-lus"              GET  /api/notification/user/$EID/unread/count "" 200

echo "--- CONTENU (B.1) ---"
call "Articles"                      GET  /api/content/articles "" 200
call "Matchs"                        GET  /api/content/matches "" 200
call "Classements"                   GET  /api/content/classements "" 200
call "Sponsors public"               GET  /api/content/sponsors/public "" 200

echo "--- ÉLECTIONS / SONDAGES ---"
call "Élections publiées"            GET  /api/elections/published "" 200
call "Sondages actifs"               GET  /api/polls/active "" 200

echo "--- GAMIFICATION ---"
call "Catalogue badges"              GET  /api/gamification/badges "" 200
call "Mes badges"                    GET  /api/gamification/badges/user/$EID "" 200
call "Mes points"                    GET  /api/gamification/points/$EID "" 200

echo "--- FICHIERS MÉDIA (admin seulement) ---"
call "Liste médias (back-office)"    GET  /api/content/media "" 403  # pas ADMIN, normal

echo "--- SALARY RECEIPTS (ENTRAINEUR a des bulletins) ---"
call "Mes bulletins de paie"         GET  /api/auth/salary-receipts/mine "" 200

echo ""
echo "============================================="
echo "RÉSUMÉ ENTRAINEUR: $PASS OK, $FAIL FAIL"
echo "============================================="
if [ $FAIL -gt 0 ]; then
  echo "Bugs trouvés:"
  for b in "${BUGS[@]}"; do echo "  $b"; done
fi
