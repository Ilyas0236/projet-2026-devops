#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════
# AUDIT COMPLET Wydad Digital — tous logins, toutes fonctionnalités.
# Exécuter SUR LA VM : source .env && ADMIN_PASSWORD=... bash scripts/audit-complet.sh
# ═══════════════════════════════════════════════════════════════════
set -o pipefail
HOST="${HOST:-http://localhost:8080}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@wac.ma}"
PASS=0; FAIL=0; TOTAL=0; FAILED_TESTS=""

say()  { echo ""; echo "── $* ─────────────────────────────────────────"; }
check(){ TOTAL=$((TOTAL+1))
  if [ "$2" = "0" ]; then PASS=$((PASS+1)); echo "  [PASS] $1"
  else FAIL=$((FAIL+1)); FAILED_TESTS="$FAILED_TESTS\n    ✗ $1 (détail: $3)"; echo "  [FAIL] $1 ($3)"; fi }
jqget() { python3 -c "import sys,json;d=json.load(sys.stdin);print(eval(sys.argv[1]))" "$1" 2>/dev/null; }

login() { curl -s -X POST "$HOST/api/auth/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$1\",\"password\":\"$2\"}" | jqget "d['accessToken']"; }
code_of(){ curl -s -o /tmp/body.$$ -w '%{http_code}' "$@"; }
reg(){ # reg email first last [extra_json_sans_accolades]
  local EXTRA="${4:-}"
  local BODY="{\"email\":\"$1\",\"password\":\"Passw0rd!234\",\"phone\":\"06${RANDOM}${RANDOM}\",\"firstName\":\"$2\",\"lastName\":\"$3\""
  if [ -n "$EXTRA" ]; then BODY="$BODY,$EXTRA"; fi
  BODY="$BODY}"
  curl -s -o /dev/null -w '%{http_code}' -X POST "$HOST/api/auth/register" -H 'Content-Type: application/json' -d "$BODY"; }
validate_user(){ docker exec wydad-postgres psql -U wydad -d auth_db -c "UPDATE users SET statut_compte='VALIDE' WHERE email='$1'" >/dev/null; }
uid_of(){ docker exec wydad-postgres psql -U wydad -d auth_db -tAc "SELECT id FROM users WHERE email='$1'" | tr -d '[:space:]'; }

TS=$(date +%s)

# ══ 0. SANTÉ INFRA ══
say "0. Santé des conteneurs"
for C in wydad-auth-service wydad-sports-service wydad-content-service wydad-ticket-service \
         wydad-shop-service wydad-payment-service wydad-communication-service wydad-election-service \
         wydad-notification-service wydad-gamification-service wydad-postgres wydad-redis \
         wydad-api-gateway wydad-frontend; do
  ST=$(docker inspect --format '{{.State.Health.Status}}' $C 2>/dev/null)
  [ "$ST" = "healthy" ]; check "conteneur $C healthy" $? "$ST"
done

# ══ 1. AUTH — TOUS LES LOGINS ══
say "1. Auth : circuit complet par rôle (7 profils)"
A_TOK=$(login "$ADMIN_EMAIL" "${ADMIN_PASSWORD:-}")
[ "$(echo $A_TOK | awk -F. '{print NF}')" = "3" ]; check "Login ADMIN → JWT" $? "$(echo $A_TOK | head -c 20)"
R=$(curl -s "$HOST/api/auth/me" -H "Authorization: Bearer $A_TOK" | jqget "d['role']")
[ "$R" = "ADMIN" ]; check "/me admin → ADMIN" $? "$R"

ADH="adh.$TS@t.wac.ma"; JOU="jou.$TS@t.wac.ma"; ENT="ent.$TS@t.wac.ma"
STA="sta.$TS@t.wac.ma"; JOU2="jou2.$TS@t.wac.ma"; PRE="pre.$TS@t.wac.ma"; JR="jr.$TS@t.wac.ma"

C=$(reg "$ADH" Test adh);                                        [ "$C" = "200" ] || [ "$C" = "202" ]; check "Inscription ADHERENT → 200/202 (auto-VALIDE)" $? "$C"
C=$(reg "$JOU" Test jou "\"disciplineDemandee\":\"FOOTBALL\",\"categorieDemandee\":\"U17\",\"demandeRole\":\"JOUEUR\"");   [ "$C" = "202" ]; check "Inscription JOUEUR → 202 Accepted EN_ATTENTE" $? "$C"
C=$(reg "$ENT" Test ent "\"disciplineDemandee\":\"BASKETBALL\",\"categorieDemandee\":\"SENIOR\",\"demandeRole\":\"ENTRAINEUR\""); [ "$C" = "202" ]; check "Inscription ENTRAINEUR → 202 Accepted EN_ATTENTE" $? "$C"
C=$(reg "$STA" Test sta "\"disciplineDemandee\":\"HANDBALL\",\"categorieDemandee\":\"U20\",\"demandeRole\":\"STAFF\"");   [ "$C" = "202" ]; check "Inscription STAFF → 202 Accepted EN_ATTENTE" $? "$C"
C=$(reg "$JOU2" Test jou2 "\"disciplineDemandee\":\"VOLLEYBALL\",\"categorieDemandee\":\"SENIOR\",\"demandeRole\":\"JOUEUR\""); [ "$C" = "202" ]; check "Inscription JOUEUR 2 → 202 Accepted EN_ATTENTE" $? "$C"
# PRESIDENT n'est PAS sollicitable à l'inscription (design §B.8 : il émerge
# des élections). On crée le compte via l'admin puis on change son rôle.
C=$(code_of -X POST "$HOST/api/auth/admin/users/create" -H "Authorization: Bearer $A_TOK" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$PRE\",\"password\":\"Passw0rd!234\",\"phone\":\"06${RANDOM}${RANDOM}\",\"firstName\":\"Test\",\"lastName\":\"pre\",\"role\":\"PRESIDENT\"}")
[ "$C" = "200" ] || [ "$C" = "201" ]; check "Création PRESIDENT par admin (non sollicitable publiquement) ($C)" $? "$C"
# JOURNALISTE exige un match RÉEL du calendrier (§17) — on crée d'abord un match.
MTCH=$(curl -s -X POST "$HOST/api/content/matches" -H "Authorization: Bearer $A_TOK" -H 'Content-Type: application/json' -d "{\"adversaire\":\"AuditX\",\"date\":\"2026-10-01\",\"heure\":\"19:00\",\"competition\":\"Botola\",\"lieu\":\"Complexe\",\"statut\":\"PROGRAMME\",\"sport\":\"FOOTBALL\",\"categorie\":\"U17\"}")
MID=$(echo "$MTCH" | jqget "d['id']")
[ -n "$MID" ] && [ "$MID" != "None" ]; check "Création match admin → id=$MID (pour accréditation)" $? "$(echo $MTCH | head -c 80)"
C=$(reg "$JR" Jour presse "\"demandeRole\":\"JOURNALISTE\",\"organismePresse\":\"Le Matin\",\"matchId\":$MID"); [ "$C" = "202" ]; check "Inscription JOURNALISTE + match réel → 202" $? "$C"

for M in "$JOU" "$ENT" "$STA" "$PRE" "$JR"; do
  LGC=$(code_of -X POST "$HOST/api/auth/login" -H 'Content-Type: application/json' -d "{\"email\":\"$M\",\"password\":\"Passw0rd!234\"}")
  [ "$LGC" != "200" ]; check "EN_ATTENTE ($(basename $M .\$TS@t.wac.ma)) ne peut PAS se connecter" $? "$LGC"
done
LGC=$(code_of -X POST "$HOST/api/auth/login" -H 'Content-Type: application/json' -d "{\"email\":\"$ADH\",\"password\":\"Passw0rd!234\"}")
[ "$LGC" = "200" ]; check "ADHERENT auto-validé peut se connecter ($LGC)" $? "$LGC"
CODE=$(code_of -X POST "$HOST/api/auth/login" -H 'Content-Type: application/json' -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"WRONG\"}")
[ "$CODE" = "401" ]; check "Mauvais mot de passe admin → 401 ($CODE)" $? "$CODE"

# ══ 2. Admin : validation VIA L'API (le bug corrigé) ══
say "2. Admin : file d'attente + validation API"
PEND=$(curl -s "$HOST/api/auth/admin/accounts/pending" -H "Authorization: Bearer $A_TOK")
NP=$(echo "$PEND" | python3 -c "import sys,json;d=json.load(sys.stdin);print(sum(1 for u in d if u['email'].endswith('@t.wac.ma')))" 2>/dev/null)
[ "$NP" -ge 5 ]; check "pending liste les 5 demandes EN_ATTENTE ($NP vues)" $? "$NP"
for M in "$JOU" "$ENT" "$STA" "$JOU2" "$JR"; do
  ID=$(uid_of "$M")
  C=$(code_of -X PATCH "$HOST/api/auth/admin/accounts/$ID/validate" -H "Authorization: Bearer $A_TOK" -H 'Content-Type: application/json')
  [ "$C" = "200" ]; check "PATCH validate $(echo $M | cut -d. -f1) → 200" $? "$C"
done
# Le compte PRESIDENT créé par l'admin : vérifier son statut directement
PST=$(docker exec wydad-postgres psql -U wydad -d auth_db -tAc "SELECT statut_compte FROM users WHERE email='$PRE'")
[ "$PST" = "VALIDE" ]; check "Compte PRESIDENT admin-created VALIDE ($PST)" $? "$PST"

T_JOU=$(login "$JOU" Passw0rd!234); T_ENT=$(login "$ENT" Passw0rd!234); T_STA=$(login "$STA" Passw0rd!234)
T_PRE=$(login "$PRE" Passw0rd!234); T_JR=$(login "$JR" Passw0rd!234); T_ADH=$(login "$ADH" Passw0rd!234)
R=$(curl -s "$HOST/api/auth/me" -H "Authorization: Bearer $T_JOU" | jqget "d['role']")
[ "$R" = "JOUEUR" ]; check "Login post-validation JOUEUR + rôle" $? "$R"
R=$(curl -s "$HOST/api/auth/me" -H "Authorization: Bearer $T_ENT" | jqget "d['role']")
[ "$R" = "ENTRAINEUR" ]; check "Login post-validation ENTRAINEUR + rôle" $? "$R"
R=$(curl -s "$HOST/api/auth/me" -H "Authorization: Bearer $T_STA" | jqget "d['role']")
[ "$R" = "STAFF" ]; check "Login post-validation STAFF + rôle" $? "$R"
R=$(curl -s "$HOST/api/auth/me" -H "Authorization: Bearer $T_PRE" | jqget "d['role']")
[ "$R" = "PRESIDENT" ]; check "Login post-validation PRESIDENT + rôle" $? "$R"
R=$(curl -s "$HOST/api/auth/me" -H "Authorization: Bearer $T_JR" | jqget "d['role']")
[ "$R" = "JOURNALISTE" ]; check "Login post-validation JOURNALISTE + rôle" $? "$R"

# ══ 3. Admin : refus de compte ══
say "3. Admin : refus de compte"
X2="x2.$TS@t.wac.ma"
reg "$X2" X deux "\"demandeRole\":\"JOUEUR\",\"disciplineDemandee\":\"FOOTBALL\",\"categorieDemandee\":\"SENIOR\"" >/dev/null
# Refus sans token → 401/403 d'abord
C=$(code_of -X PATCH "$HOST/api/auth/admin/accounts/$(uid_of $X2)/refuse" -H 'Content-Type: application/json' -d '{"motif":"x"}')
[ "$C" = "401" ] || [ "$C" = "403" ]; check "Refus SANS authentification refusé ($C)" $? "$C"
IDX=$(uid_of "$X2")
C=$(code_of -X PATCH "$HOST/api/auth/admin/accounts/$IDX/refuse" -H "Authorization: Bearer $A_TOK" -H 'Content-Type: application/json' -d '{}')
[ "$C" = "400" ]; check "Refus AVEC token SANS motif → 400 ($C)" $? "$C"
C=$(code_of -X PATCH "$HOST/api/auth/admin/accounts/$IDX/refuse" -H "Authorization: Bearer $A_TOK" -H 'Content-Type: application/json' -d '{"motif":"Documents non conformes"}')
[ "$C" = "200" ]; check "Refus AVEC motif → 200 ($C)" $? "$C"
MOTIF=$(docker exec wydad-postgres psql -U wydad -d auth_db -tAc "SELECT motif_refus FROM users WHERE email='$X2'")
[ "$MOTIF" = "Documents non conformes" ]; check "Motif persisté en base" $? "$MOTIF"
LGC=$(code_of -X POST "$HOST/api/auth/login" -H 'Content-Type: application/json' -d "{\"email\":\"$X2\",\"password\":\"Passw0rd!234\"}")
[ "$LGC" != "200" ]; check "Compte REFUSE ne peut pas se connecter ($LGC)" $? "$LGC"

# ══ 4. Admin : rôles & création ══
say "4. Admin : gestion rôles & création utilisateur"
C=$(code_of -X PATCH "$HOST/api/auth/admin/users/$IDX/role?newRole=VISITEUR" -H "Authorization: Bearer $A_TOK")
[ "$C" = "200" ]; check "changeUserRole → VISITEUR ($C)" $? "$C"
C=$(code_of -X POST "$HOST/api/auth/admin/users/create" -H "Authorization: Bearer $A_TOK" -H 'Content-Type: application/json' \
  -d "{\"email\":\"adm2.$TS@t.wac.ma\",\"password\":\"Passw0rd!234\",\"phone\":\"06${RANDOM}${RANDOM}\",\"firstName\":\"Sub\",\"lastName\":\"Admin\",\"role\":\"STAFF\"}")
[ "$C" = "200" ] || [ "$C" = "201" ]; check "adminCreateUser STAFF ($C)" $? "$C"
C=$(code_of "$HOST/api/auth/admin/users" -H "Authorization: Bearer $T_JOU")
[ "$C" = "403" ]; check "Liste users refusée à JOUEUR ($C)" $? "$C"

# ══ 5. Sécurité endpoints admin ══
say "5. Sécurité : protection des endpoints admin"
C=$(code_of -X PATCH "$HOST/api/auth/admin/accounts/$IDX/validate" -H "Authorization: Bearer $T_JOU")
[ "$C" = "403" ]; check "validate refusé à JOUEUR ($C)" $? "$C"
C=$(code_of -X GET "$HOST/api/auth/admin/accounts/pending" -H "Authorization: Bearer $T_ENT")
[ "$C" = "403" ]; check "pending refusé à ENTRAINEUR ($C)" $? "$C"
FORGED=$(curl -s "$HOST/api/auth/me" -H "Authorization: Bearer $T_JOU" -H "X-User-Role: ADMIN")
echo "$FORGED" | jqget "d['role']" | grep -q 'JOUEUR'; check "Forge X-User-Role ignorée" $? "$(cat /tmp/body.$$ | head -c 60)"

# ══ 6. SPORTS ══
say "6. Sports : joueurs, staff, espaces"
PL=$(curl -s "$HOST/api/sports/players/filter?sportType=FOOTBALL&category=U17" -H "Authorization: Bearer $A_TOK")
echo "$PL" | python3 -c "import sys,json;json.load(sys.stdin)" 2>/dev/null
check "players/filter admin OK" $? "$(echo $PL | head -c 60)"
C=$(code_of "$HOST/api/sports/players/filter?sportType=BASKETBALL&category=SENIOR" -H "Authorization: Bearer $T_JOU")
[ "$C" = "403" ]; check "players/filter refusé à JOUEUR ($C)" $? "$C"
CCODE=$(code_of "$HOST/api/sports/match-convocations/my" -H "Authorization: Bearer $T_ENT")
[ "$CCODE" != "500" ] && [ "$CCODE" != "000" ] && [ "$CCODE" != "404" ]; check "convocations /my ENTRAINEUR pas d'erreur ($CCODE)" $? "$CCODE $(head -c 100 /tmp/body.$$)"
CCODE=$(code_of "$HOST/api/sports/match-convocations/my" -H "Authorization: Bearer $T_JOU")
[ "$CCODE" = "200" ] || [ "$CCODE" = "403" ]; check "convocations /my JOUEUR répond ($CCODE)" $? "$CCODE"
JID=$(uid_of "$JOU")
MED=$(code_of "$HOST/api/sports/my-space/presence" -H "Authorization: Bearer $T_ENT")
[ "$MED" = "403" ]; check "espace joueur (presence) inaccessible à ENTRAINEUR ($MED)" $? "$MED"
PRES=$(code_of "$HOST/api/sports/my-space/presence" -H "Authorization: Bearer $T_JOU")
[ "$PRES" = "200" ]; check "espace joueur presence par JOUEUR ($PRES)" $? "$(head -c 100 /tmp/body.$$)"
STATS=$(code_of "$HOST/api/sports/my-space/stats" -H "Authorization: Bearer $T_JOU")
[ "$STATS" = "200" ]; check "espace joueur stats par JOUEUR ($STATS)" $? "$(head -c 100 /tmp/body.$$)"

# ══ 7. CONTENT ══
say "7. Content : matchs, articles"
MTCH=$(curl -s -X POST "$HOST/api/content/matches" -H "Authorization: Bearer $A_TOK" -H 'Content-Type: application/json' -d "{\"adversaire\":\"AuditX\",\"date\":\"2026-10-01\",\"heure\":\"19:00\",\"competition\":\"Botola\",\"lieu\":\"Complexe\",\"statut\":\"PROGRAMME\",\"sport\":\"FOOTBALL\",\"categorie\":\"U17\"}")
MID=$(echo "$MTCH" | jqget "d['id']")
[ -n "$MID" ] && [ "$MID" != "None" ]; check "Création match admin → id=$MID" $? "$(echo $MTCH | head -c 100)"
C=$(code_of -X DELETE "$HOST/api/content/matches/$MID" -H "Authorization: Bearer $T_JOU")
[ "$C" = "403" ] || [ "$C" = "405" ]; check "Suppression match refusée à JOUEUR ($C)" $? "$C"
ART=$(curl -s "$HOST/api/content/articles")
echo "$ART" | python3 -c "import sys,json;json.load(sys.stdin)" 2>/dev/null
check "articles publics OK" $? "$(echo $ART | head -c 60)"
ARTC=$(code_of -X POST "$HOST/api/content/articles" -H "Authorization: Bearer $A_TOK" -H 'Content-Type: application/json' -d '{"titre":"Audit","contenu":"Test audit","sport":"FOOTBALL","auteur":"AuditBot"}')
[ "$ARTC" = "200" ] || [ "$ARTC" = "201" ]; check "Création article admin ($ARTC)" $? "$ARTC"

# ══ 8. TICKET ══
say "8. Billetterie"
EV=$(curl -s "$HOST/api/ticket/events")
echo "$EV" | python3 -c "import sys,json;json.load(sys.stdin)" 2>/dev/null
check "events publics OK" $? "$(echo $EV | head -c 60)"
TKC=$(code_of "$HOST/api/ticket/tickets/user/$JID" -H "Authorization: Bearer $T_JOU")
[ "$TKC" = "200" ] || [ "$TKC" = "404" ]; check "tickets/user/{soi} JOUEUR autorisé ($TKC)" $? "$TKC"
JID2=$(uid_of "$JOU2")
TKC=$(code_of "$HOST/api/ticket/tickets/user/$JID2" -H "Authorization: Bearer $T_JOU")
[ "$TKC" = "403" ]; check "tickets/user/{AUTRUI} refusé anti-IDOR ($TKC)" $? "$TKC"

# ══ 9. SHOP & PAYMENT ══
say "9. Boutique & paiement"
PR=$(curl -s "$HOST/api/shop/products")
echo "$PR" | python3 -c "import sys,json;json.load(sys.stdin)" 2>/dev/null
check "produits publics OK" $? "$(echo $PR | head -c 60)"
ORD=$(code_of -X POST "$HOST/api/shop/orders" -H 'Content-Type: application/json' -d '{}')
[ "$ORD" = "401" ] || [ "$ORD" = "403" ]; check "commande sans token refusée ($ORD)" $? "$ORD"
# Contrat PaymentController : wallet réservé ADHERENT/ADMIN (circuit cotisations).
WAL=$(code_of "$HOST/api/payment/balance" -H "Authorization: Bearer $T_JOU")
[ "$WAL" = "403" ]; check "wallet balance refusé à JOUEUR (réservé ADHERENT/ADMIN) ($WAL)" $? "$WAL"
WALA=$(code_of "$HOST/api/payment/balance" -H "Authorization: Bearer $T_ADH")
[ "$WALA" = "200" ]; check "wallet balance accessible à ADHERENT ($WALA)" $? "$(head -c 80 /tmp/body.$$)"

# ══ 10. COMMUNICATION ══
say "10. Communication : newsletter, notifications"
NEWS=$(code_of -X POST "$HOST/api/notification/newsletter/subscribe" -H 'Content-Type: application/json' -d "{\"email\":\"news.$TS@t.wac.ma\"}")
[ "$NEWS" = "200" ] || [ "$NEWS" = "201" ]; check "newsletter subscribe ($NEWS)" $? "$NEWS"
NOTIF=$(code_of "$HOST/api/notification/user/$JID" -H "Authorization: Bearer $A_TOK")
[ "$NOTIF" = "200" ]; check "notifications du joueur (admin) ($NOTIF)" $? "$(head -c 100 /tmp/body.$$)"
NDEC=$(docker exec wydad-postgres psql -U wydad -d notification_db -tAc "SELECT COUNT(*) FROM notifications WHERE title IN ('Compte validé','Demande de compte refusée') AND user_id=$JID" 2>/dev/null | tr -d '[:space:]')
[ "$NDEC" -ge 1 ]; check "Notification in-app décision compte reçue ($NDEC)" $? "$NDEC"

# ══ 11. ELECTION & GAMIFICATION ══
say "11. Élections & gamification"
EL=$(curl -s "$HOST/api/elections/published")
echo "$EL" | python3 -c "import sys,json;json.load(sys.stdin)" 2>/dev/null
check "résultats publiés élections OK (public)" $? "$(echo $EL | head -c 60)"
LB=$(code_of "$HOST/api/gamification/leaderboard")
[ "$LB" = "200" ]; check "leaderboard public OK ($LB)" $? "$(head -c 80 /tmp/body.$$)"
GAM=$(code_of "$HOST/api/gamification/points/$JID" -H "Authorization: Bearer $T_JOU")
[ "$GAM" != "500" ] && [ "$GAM" != "000" ]; check "gamification points joueur pas d'erreur serveur ($GAM)" $? "$(head -c 100 /tmp/body.$$)"
BADGES=$(code_of "$HOST/api/gamification/badges/user/$JID" -H "Authorization: Bearer $T_JOU")
[ "$BADGES" != "" ] && echo "$BADGES" | python3 -c "import sys,json;json.load(sys.stdin)" 2>/dev/null
check "badges du joueur OK" $? "$(head -c 60 /tmp/body.$$)"

# ══ 12. FRONTEND ══
say "12. Frontend SPA (routes principales)"
for ROUTE in "/" "/login" "/register" "/boutique" "/billetterie" "/actualites" "/elections" "/profil" "/admin" "/espace-entraineur" "/espace-journaliste" "/espace-president"; do
  C=$(code_of "http://localhost:4200$ROUTE")
  [ "$C" = "200" ]; check "route $ROUTE → 200" $? "$C"
done

# ══ NETTOYAGE ══
say "13. Nettoyage"
docker exec wydad-postgres psql -U wydad -d auth_db -c "DELETE FROM users WHERE email LIKE '%.$TS@t.wac.ma'" >/dev/null 2>&1
[ -n "$MID" ] && curl -s -o /dev/null -X DELETE "$HOST/api/content/matches/$MID" -H "Authorization: Bearer $A_TOK"
LEFT=$(docker exec wydad-postgres psql -U wydad -d auth_db -tAc "SELECT COUNT(*) FROM users WHERE email LIKE '%.$TS@t.wac.ma'")
[ "$LEFT" = "0" ]; check "Comptes de test supprimés ($LEFT restants)" $? "$LEFT"

say "RÉSUMÉ"
echo "  TOTAL=$TOTAL  PASS=$PASS  FAIL=$FAIL"
if [ $FAIL -ne 0 ]; then echo -e "  Tests en échec:\n$FAILED_TESTS"; fi
[ $FAIL -eq 0 ] && echo "  ✅ AUDIT VERT" || echo "  ❌ CORRECTIONS REQUISES"
exit $([ $FAIL -eq 0 ] && echo 0 || echo 1)
