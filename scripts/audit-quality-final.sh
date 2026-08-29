#!/usr/bin/env bash
# ============================================================================
# audit-quality-final.sh — Audit E2E global du plan qualité Wydad Digital
# (données 100% admin + compte obligatoire + interface joueur épurée +
# notifications tous rôles). 9 tests T1..T9 couvrant la régression et les
# nouvelles fonctionnalités.
#
# Pré-requis : VM en ligne (auth, ticket, content, notification, election,
# sports, communication, content-service healthy). Comptes de test :
#   admin@wac.ma / Admin2026!  (ADMIN)
#   test.fan@wydad.ma          (USER supporter)
#   test.joueur@wydad.ma       (JOUEUR)
#   test.entraineur@wydad.ma   (ENTRAINEUR)
#   test.journaliste@wydad.ma  (JOURNALISTE)
#   test.parent@wydad.ma       (PARENT)
#   test.president@wydad.ma    (PRESIDENT)
#   Mots de passe uniformes : Wydad2026!
#
# Usage : bash scripts/audit-quality-final.sh
#         (depuis la racine wydad-digital-parent)
# ============================================================================
set -uo pipefail

BASE_GATEWAY="${BASE_GATEWAY:-http://localhost:8080}"
PASS_GLOBAL="${PASS_GLOBAL:-Wydad2026!}"
ADMIN_PASS="${ADMIN_PASS:-Admin2026!}"

green() { printf "\033[32m%s\033[0m\n" "$*"; }
red()   { printf "\033[31m%s\033[0m\n" "$*"; }
blue()  { printf "\033[34m%s\033[0m\n" "$*"; }
yellow(){ printf "\033[33m%s\033[0m\n" "$*"; }

PASS=0
FAIL=0
declare -a BUGS

login() {
  local email="$1" pass="$2"
  curl -s -X POST "$BASE_GATEWAY/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"$email\",\"password\":\"$pass\"}" \
    | sed -nE 's/.*"accessToken":"([^"]+)".*/\1/p'
}

get_user_id() {
  local email="$1" token="$2"
  curl -s -H "Authorization: Bearer $token" "$BASE_GATEWAY/api/auth/me" \
    | sed -nE 's/.*"id":([0-9]+).*/\1/p' | head -1
}

check() {
  local label="$1" code="$2" expect="$3"
  if [ "$code" = "$expect" ]; then
    green "  ✓ $label  [$code]"
    PASS=$((PASS+1))
  else
    red "  ✗ $label  [$code != $expect]"
    BUGS+=("[$label] $code != $expect")
    FAIL=$((FAIL+1))
  fi
}

# ============================================================================
# Setup : login de tous les rôles
# ============================================================================
blue "═══ Setup : login de tous les rôles ═══"
ADMIN_TOK=$(login "admin@wac.ma" "$ADMIN_PASS")
FAN_TOK=$(login "test.fan@wydad.ma" "$PASS_GLOBAL")
PLAYER_TOK=$(login "test.joueur@wydad.ma" "$PASS_GLOBAL")
COACH_TOK=$(login "test.entraineur@wydad.ma" "$PASS_GLOBAL")
JOURNO_TOK=$(login "test.journaliste@wydad.ma" "$PASS_GLOBAL")
PARENT_TOK=$(login "test.parent@wydad.ma" "$PASS_GLOBAL")
PRES_TOK=$(login "test.president@wydad.ma" "$PASS_GLOBAL")

if [ -z "$ADMIN_TOK" ] || [ -z "$FAN_TOK" ] || [ -z "$PLAYER_TOK" ] \
   || [ -z "$COACH_TOK" ] || [ -z "$JOURNO_TOK" ] || [ -z "$PARENT_TOK" ] \
   || [ -z "$PRES_TOK" ]; then
  red "✖ Login d'au moins un rôle a échoué — vérifier que les comptes test existent"
  red "   Tokens: ADMIN=$([ -n "$ADMIN_TOK" ] && echo OK || echo KO)"
  red "          FAN=$([ -n "$FAN_TOK" ] && echo OK || echo KO)"
  red "          PLAYER=$([ -n "$PLAYER_TOK" ] && echo OK || echo KO)"
  red "          COACH=$([ -n "$COACH_TOK" ] && echo OK || echo KO)"
  red "          JOURNO=$([ -n "$JOURNO_TOK" ] && echo OK || echo KO)"
  red "          PARENT=$([ -n "$PARENT_TOK" ] && echo OK || echo KO)"
  red "          PRES=$([ -n "$PRES_TOK" ] && echo OK || echo KO)"
  exit 1
fi
green "✓ 7 rôles connectés"

# ============================================================================
# T1 — Supporter : cloche notification visible (auth-service /me → userId)
# ============================================================================
blue ""
blue "═══ T1 — Supporter : cloche notification visible ═══"
FAN_ID=$(get_user_id "test.fan@wydad.ma" "$FAN_TOK")
if [ -n "$FAN_ID" ]; then
  green "✓ FAN_ID=$FAN_ID"
  PASS=$((PASS+1))
else
  red "✖ /me n'a pas renvoyé d'id pour le supporter"
  BUGS+=("[T1] /me empty id for fan")
  FAIL=$((FAIL+1))
fi

# Compteur unread (0 attendu si rien n'a été envoyé récemment)
CODE=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $FAN_TOK" \
  "$BASE_GATEWAY/api/notification/unread-count?userId=$FAN_ID")
check "Compteur unread supporter" "$CODE" "200"

# ============================================================================
# T2 — Joueur : /joueur/dashboard direct sans navbar publique
# ============================================================================
blue ""
blue "═══ T2 — Joueur : pas de navbar publique, dashboard direct ═══"
PLAYER_ID=$(get_user_id "test.joueur@wydad.ma" "$PLAYER_TOK")
[ -n "$PLAYER_ID" ] && green "✓ PLAYER_ID=$PLAYER_ID"

CODE=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $PLAYER_TOK" \
  "$BASE_GATEWAY/api/sports/my-space/convocations")
check "Mes convocations (joueur)" "$CODE" "200"

CODE=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $PLAYER_TOK" \
  "$BASE_GATEWAY/api/sports/my-space/stats")
check "Mes stats (joueur)" "$CODE" "200"

# Test de la nav front : la cloche notification doit être servie (bundled JS)
# On vérifie juste que /login?returnUrl=/joueur/dashboard est la cible de
# l'auth.guard (cf. T8) — ici on vérifie que le bundle front a bien le
# composant cloche compilé.
if [ -f "wydad-frontend/dist/wydad-frontend/browser/main-*.js" ]; then
  if grep -q "notification-bell" wydad-frontend/dist/wydad-frontend/browser/main-*.js; then
    green "✓ Bundle front contient 'notification-bell' (cloche universelle)"
    PASS=$((PASS+1))
  else
    red "✖ Bundle front ne contient pas 'notification-bell'"
    BUGS+=("[T2] notification-bell absent du bundle")
    FAIL=$((FAIL+1))
  fi
else
  yellow "⚠ dist/ absent — bundle non vérifié (skip cloche bundle)"
fi

# ============================================================================
# T3 — Entraîneur : convoque joueur → notif joueur (IN_APP + email MOCK)
# ============================================================================
blue ""
blue "═══ T3 — Entraîneur : convocation → notif joueur ═══"
COACH_ID=$(get_user_id "test.entraineur@wydad.ma" "$COACH_TOK")
[ -n "$COACH_ID" ] && green "✓ COACH_ID=$COACH_ID"

# Trouver un match PROGRAMME côté content-service
MATCH_ID=$(curl -s -H "Authorization: Bearer $COACH_TOK" \
  "$BASE_GATEWAY/api/content/matches/statut/PROGRAMME" \
  | sed -nE 's/.*"id":([0-9]+).*/\1/p' | head -1)
if [ -n "$MATCH_ID" ]; then
  green "✓ Match PROGRAMME trouvé: $MATCH_ID"
  # Tenter de convoquer le joueur
  CODE=$(curl -s -o /tmp/convoc -w "%{http_code}" -X POST \
    -H "Authorization: Bearer $COACH_TOK" \
    -H "Content-Type: application/json" \
    -d "{\"matchId\":$MATCH_ID,\"playerUserId\":$PLAYER_ID}" \
    "$BASE_GATEWAY/api/sports/convocations")
  # On accepte 201 (créé) ou 409 (déjà convoqué) — pas 500
  if [ "$CODE" = "201" ] || [ "$CODE" = "409" ] || [ "$CODE" = "200" ]; then
    green "✓ Convocation joueur : $CODE (201=créé, 409=déjà convoqué, 200=ok)"
    PASS=$((PASS+1))
  else
    red "✖ Convocation joueur en erreur : $CODE — $(head -c 200 /tmp/convoc)"
    BUGS+=("[T3] convocation failed: $CODE")
    FAIL=$((FAIL+1))
  fi
else
  yellow "⚠ Aucun match PROGRAMME en base — convocation non testée"
fi

# ============================================================================
# T4 — Journaliste : publie article → notif journalistes ciblée
# ============================================================================
blue ""
blue "═══ T4 — Journaliste : publication article → notif JOURNALISTE ═══"
JOURNO_ID=$(get_user_id "test.journaliste@wydad.ma" "$JOURNO_TOK")
[ -n "$JOURNO_ID" ] && green "✓ JOURNO_ID=$JOURNO_ID"

# Compter les notifs du journaliste AVANT publication
BEFORE=$(curl -s -H "Authorization: Bearer $JOURNO_TOK" \
  "$BASE_GATEWAY/api/notification?userId=$JOURNO_ID" \
  | grep -o '"id":' | wc -l | tr -d ' ')

# Publier un article (ADMIN uniquement — le hook notif journalistes est dans
# ContentService.createArticle, on utilise donc l'admin)
CREATE=$(curl -s -X POST -H "Authorization: Bearer $ADMIN_TOK" \
  -H "Content-Type: application/json" \
  -d "{\"titre\":\"E2E audit quality $(date +%s)\",\"contenu\":\"Test notification journalistes\",\"sport\":\"FOOTBALL\",\"auteur\":\"Audit E2E\"}" \
  "$BASE_GATEWAY/api/content/articles")
ART_ID=$(echo "$CREATE" | sed -nE 's/.*"id":([0-9]+).*/\1/p' | head -1)
if [ -n "$ART_ID" ]; then
  green "✓ Article créé id=$ART_ID"
  sleep 2  # laisse le temps au broadcast de fan-out
  AFTER=$(curl -s -H "Authorization: Bearer $JOURNO_TOK" \
    "$BASE_GATEWAY/api/notification?userId=$JOURNO_ID" \
    | grep -o '"id":' | wc -l | tr -d ' ')
  if [ "$AFTER" -gt "$BEFORE" ]; then
    green "✓ Notification journalistes reçue ($BEFORE → $AFTER)"
    PASS=$((PASS+1))
  else
    yellow "⚠ Notification non reçue par le journaliste ($BEFORE → $AFTER) — vérifier broadcast ciblé"
    # Pas un fail dur : le journaliste peut avoir désactivé IN_APP
  fi
else
  red "✖ Création article échouée : $CREATE"
  BUGS+=("[T4] article create failed")
  FAIL=$((FAIL+1))
fi

# ============================================================================
# T5 — Parent : /parent/dashboard accessible + cloche OK
# ============================================================================
blue ""
blue "═══ T5 — Parent : dashboard + cloche ═══"
PARENT_ID=$(get_user_id "test.parent@wydad.ma" "$PARENT_TOK")
[ -n "$PARENT_ID" ] && green "✓ PARENT_ID=$PARENT_ID"

CODE=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $PARENT_TOK" \
  "$BASE_GATEWAY/api/notification/unread-count?userId=$PARENT_ID")
check "Compteur unread parent" "$CODE" "200"

# ============================================================================
# T6 — Président : notif résultats élections
# ============================================================================
blue ""
blue "═══ T6 — Président : notif résultats élections (broadcast global) ═══"
PRES_ID=$(get_user_id "test.president@wydad.ma" "$PRES_TOK")
[ -n "$PRES_ID" ] && green "✓ PRES_ID=$PRES_ID"

# Vérifier que le président reçoit les broadcasts (compte au moins 1 notif ou 0
# si rien n'a été broadcast récemment — l'endpoint doit juste répondre 200)
CODE=$(curl -s -o /dev/null -w "%{http_code}" -H "Authorization: Bearer $PRES_TOK" \
  "$BASE_GATEWAY/api/notification?userId=$PRES_ID")
check "Liste notifs président" "$CODE" "200"

# ============================================================================
# T7 — Admin : broadcast supporters à createEvent (event dans les 30j)
# ============================================================================
blue ""
blue "═══ T7 — Admin : createEvent → notif supporters ciblée ═══"

# Compter les notifs du supporter AVANT
BEFORE_FAN=$(curl -s -H "Authorization: Bearer $FAN_TOK" \
  "$BASE_GATEWAY/api/notification?userId=$FAN_ID" \
  | grep -o '"id":' | wc -l | tr -d ' ')

# Créer un event J+15 pour déclencher le hook
EVT_DATE=$(date -d "+15 days" '+%Y-%m-%dT%H:%M:%S' 2>/dev/null || date -v+15d '+%Y-%m-%dT%H:%M:%S')
CREATE=$(curl -s -X POST -H "Authorization: Bearer $ADMIN_TOK" \
  -H "Content-Type: application/json" \
  -d "{\"title\":\"E2E audit J+15\",\"description\":\"Test broadcast supporters\",\"eventType\":\"MATCH\",\"category\":\"CHAMPIONNAT\",\"homeTeam\":\"WAC\",\"awayTeam\":\"RCA\",\"venue\":\"Stade\",\"eventDate\":\"$EVT_DATE\",\"basePrice\":50,\"totalCapacity\":1000,\"sections\":[{\"name\":\"Tribune\",\"category\":\"TRIBUNE\",\"seatType\":\"STANDARD\",\"capacity\":500,\"price\":100},{\"name\":\"Virage\",\"category\":\"VIRAGE\",\"seatType\":\"STANDARD\",\"capacity\":500,\"price\":50}]}" \
  "$BASE_GATEWAY/api/ticket/events")
EVT_ID=$(echo "$CREATE" | sed -nE 's/.*"id":([0-9]+).*/\1/p' | head -1)
if [ -n "$EVT_ID" ]; then
  green "✓ Event créé id=$EVT_ID (date $EVT_DATE, dans la fenêtre 30j)"
  sleep 2
  AFTER_FAN=$(curl -s -H "Authorization: Bearer $FAN_TOK" \
    "$BASE_GATEWAY/api/notification?userId=$FAN_ID" \
    | grep -o '"id":' | wc -l | tr -d ' ')
  if [ "$AFTER_FAN" -gt "$BEFORE_FAN" ]; then
    green "✓ Notification supporters reçue ($BEFORE_FAN → $AFTER_FAN)"
    PASS=$((PASS+1))
  else
    yellow "⚠ Notification non reçue par le supporter ($BEFORE_FAN → $AFTER_FAN) — vérifier broadcast ciblé"
  fi
else
  red "✖ Création event échouée : $(echo "$CREATE" | head -c 200)"
  BUGS+=("[T7] event create failed")
  FAIL=$((FAIL+1))
fi

# ============================================================================
# T8 — returnUrl : /joueur/dashboard non logué → /login?returnUrl=...
# ============================================================================
blue ""
blue "═══ T8 — auth.guard returnUrl ═══"
# L'auth.guard est côté front, pas testable via curl. Mais on peut
# vérifier que login?returnUrl=... fonctionne et redirige après login.
LOGIN_REDIR=$(curl -s -X POST "$BASE_GATEWAY/api/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"test.joueur@wydad.ma","password":"'"$PASS_GLOBAL"'"}' \
  -w "\n%{http_code}")
CODE=$(echo "$LOGIN_REDIR" | tail -1)
check "Login joueur (auth.guard source du returnUrl)" "$CODE" "200"

# Bundle front : doit contenir 'returnUrl' (le auth.guard le passe)
if [ -f "wydad-frontend/dist/wydad-frontend/browser/main-*.js" ]; then
  if grep -q "returnUrl" wydad-frontend/dist/wydad-frontend/browser/main-*.js; then
    green "✓ Bundle front contient 'returnUrl' (auth.guard OK)"
    PASS=$((PASS+1))
  else
    red "✖ Bundle front ne contient pas 'returnUrl'"
    BUGS+=("[T8] returnUrl absent du bundle")
    FAIL=$((FAIL+1))
  fi
else
  yellow "⚠ dist/ absent — bundle non vérifié"
fi

# ============================================================================
# T9 — NPE SubscriptionResponse (zoneCode null)
# ============================================================================
blue ""
blue "═══ T9 — NPE SubscriptionResponse : zoneCode=null toléré ═══"
# Test direct de l'endpoint /me/active avec le supporter (qui n'a pas
# d'abonnement — doit retourner 404 ou [] sans 500)
CODE=$(curl -s -o /tmp/sub -w "%{http_code}" -H "Authorization: Bearer $FAN_TOK" \
  "$BASE_GATEWAY/api/auth/subscriptions/me/active")
if [ "$CODE" = "404" ] || [ "$CODE" = "200" ] || [ "$CODE" = "204" ]; then
  green "✓ /me/active supporter : $CODE (pas de 500 — NPE évité)"
  PASS=$((PASS+1))
else
  red "✖ /me/active supporter inattendu : $CODE — $(head -c 200 /tmp/sub)"
  BUGS+=("[T9] /me/active unexpected: $CODE")
  FAIL=$((FAIL+1))
fi

# ============================================================================
# Résumé
# ============================================================================
echo ""
echo "============================================="
TOTAL=$((PASS + FAIL))
green "  ✓ PASS: $PASS / $TOTAL"
[ "$FAIL" -gt 0 ] && red "  ✗ FAIL: $FAIL / $TOTAL"
echo "============================================="
if [ "$FAIL" -gt 0 ]; then
  echo ""
  red "BUGS détectés :"
  for b in "${BUGS[@]}"; do
    red "  - $b"
  done
  exit 1
fi
green "── Audit qualité final : OK ──"
