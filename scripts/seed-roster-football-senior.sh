#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════════
# Seed — Roster FOOTBALL SENIOR (5 joueurs + 1 HEAD_COACH)
# ════════════════════════════════════════════════════════════════════════
#
# Crée 5 joueurs + 1 entraîneur principal (HEAD_COACH) dans le roster
# FOOTBALL / SENIOR via l'API interne /api/sports/internal/roster/*.
# Idempotent : ré-exécutable, les fiches existantes ne sont pas écrasées
# (upsert côté repository).
#
# Usage :
#   bash scripts/seed-roster-football-senior.sh
#
# Sortie : 6 lignes "✓ Fiche userId=N créée" + exit 0.

set -e
GATEWAY="${GATEWAY:-http://localhost:8080}"
AUTH="${GATEWAY}/api/auth"
SPORTS="${GATEWAY}/api/sports"

# Charge .env si présent (INTERNAL_SECRET)
if [ -f .env ]; then
    set -a; . ./.env; set +a
fi
SECRET="${INTERNAL_SECRET:?INTERNAL_SECRET manquant (vérifier .env)}"

bold() { printf '\n\033[1m%s\033[0m\n' "$1"; }
ok()   { printf '\033[32m✓\033[0m %s\n' "$1"; }
fail() { printf '\033[31m✗\033[0m %s\n' "$1"; exit 1; }

# ────────────────────────────── 1) Récupération token ADMIN ──────────────────────────────
bold "1) Login ADMIN pour créer les comptes"
ADMIN_TOKEN=$(curl -s -X POST "$AUTH/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"admin@wac.ma\",\"password\":\"${ADMIN_PASS:-Admin2026!}\"}" \
    | python -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))")
[ -n "$ADMIN_TOKEN" ] || fail "Pas de token admin (vérifier ADMIN_PASS)"
ok "Admin token OK"

# ────────────────────────────── 2) Création des 6 comptes ──────────────────────────────
bold "2) Création de 5 joueurs + 1 HEAD_COACH"
declare -a ACCOUNTS=(
    # email|password|prenom|nom|role|sportType|category|rosterRole
    "joueur.test@wac.ma|Joueur2026!|Younes|Belal|ROLE_JOUEUR|FOOTBALL|SENIOR|JOUEUR"
    "joueur2.test@wac.ma|Joueur2026!|Reda|Slaoui|ROLE_JOUEUR|FOOTBALL|SENIOR|JOUEUR"
    "joueur3.test@wac.ma|Joueur2026!|Aymane|Idrissi|ROLE_JOUEUR|FOOTBALL|SENIOR|JOUEUR"
    "joueur4.test@wac.ma|Joueur2026!|Hamza|El-Arabi|ROLE_JOUEUR|FOOTBALL|SENIOR|JOUEUR"
    "joueur5.test@wac.ma|Joueur2026!|Omar|Laraki|ROLE_JOUEUR|FOOTBALL|SENIOR|JOUEUR"
    "coach.test@wac.ma|Coach2026!|Mohamed|Amrani|ROLE_ENTRAINEUR|FOOTBALL|SENIOR|STAFF"
)

declare -A USER_IDS  # email → userId

for line in "${ACCOUNTS[@]}"; do
    IFS='|' read -r email pass first last role sport cat _ <<< "$line"
    # Crée le compte (idempotent côté API : 409 si existe)
    HTTP=$(curl -s -w '\n%{http_code}' -X POST "$AUTH/admin/users/create" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        -H 'Content-Type: application/json' \
        -d "{\"email\":\"$email\",\"password\":\"$pass\",\"firstName\":\"$first\",\"lastName\":\"$last\",\"role\":\"${role#ROLE_}\"}")
    CODE=$(echo "$HTTP" | tail -1)
    [ "$CODE" = "201" ] || [ "$CODE" = "409" ] || fail "Création $email KO (HTTP $CODE : $(echo "$HTTP" | head -1))"

    # Récupère l'userId
    UID=$(curl -s "$AUTH/admin/users?email=$email" \
        -H "Authorization: Bearer $ADMIN_TOKEN" \
        | python -c "import sys,json; d=json.load(sys.stdin); print(next((u['id'] for u in (d if isinstance(d,list) else d.get('items',[])) if u.get('email')=='$email'), ''))")
    [ -n "$UID" ] || fail "userId introuvable pour $email"
    USER_IDS[$email]=$UID
    ok "$email créé (id=$UID)"
done

# ────────────────────────────── 3) Création des fiches roster (interne) ──────────────────────────────
bold "3) Création des fiches players + staff (API interne sports-service)"
for line in "${ACCOUNTS[@]}"; do
    IFS='|' read -r email pass first last role sport cat rosterRole <<< "$line"
    UID=${USER_IDS[$email]}
    FULLNAME="$first $last"
    if [ "$rosterRole" = "JOUEUR" ]; then
        curl -s -X POST "$SPORTS/internal/roster/players" \
            -H "X-Internal-Secret: $SECRET" \
            -H 'Content-Type: application/json' \
            -d "{\"userId\":$UID,\"fullName\":\"$FULLNAME\",\"sportType\":\"$sport\",\"category\":\"$cat\"}" \
            > /dev/null
        ok "Fiche JOUEUR $email (userId=$UID)"
    else
        curl -s -X POST "$SPORTS/internal/roster/staff" \
            -H "X-Internal-Secret: $SECRET" \
            -H 'Content-Type: application/json' \
            -d "{\"userId\":$UID,\"fullName\":\"$FULLNAME\",\"role\":\"HEAD_COACH\",\"sportType\":\"$sport\",\"assignedCategory\":\"$cat\"}" \
            > /dev/null
        ok "Fiche STAFF (HEAD_COACH) $email (userId=$UID)"
    fi
done

# ────────────────────────────── 4) Vérification du groupe ──────────────────────────────
bold "4) Vérification — groupe FOOTBALL/SENIOR doit avoir 6 membres"
# Login président
PRES_TOKEN=$(curl -s -X POST "$AUTH/login" \
    -H 'Content-Type: application/json' \
    -d '{"email":"president@wac.ma","password":"President2026!"}' \
    | python -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))")
[ -n "$PRES_TOKEN" ] || fail "Pas de token président (vérifier que le seeder président a été lancé)"

COUNT=$(curl -s "$SPORTS/team-chat/FOOTBALL/SENIOR/members" \
    -H "Authorization: Bearer $PRES_TOKEN" \
    | python -c "import sys,json; d=json.load(sys.stdin); print(len(d) if isinstance(d,list) else 0)")
[ "$COUNT" -eq 6 ] || fail "Groupe devrait avoir 6 membres, trouvé $COUNT (vérifier sport/cat)"

bold "════════════════════════════════════════════════════════════════════"
printf '\033[32m✓ Roster FOOTBALL/SENIOR : 5 joueurs + 1 HEAD_COACH créés\033[0m\n'
echo "Comptes de test :"
echo "  joueur.test@wac.ma / Joueur2026!"
echo "  coach.test@wac.ma  / Coach2026!"
echo "  president@wac.ma  / President2026!  (rappel)"
echo ""
echo "Prochaine étape :"
echo "  bash scripts/audit-president-discussions.sh"
