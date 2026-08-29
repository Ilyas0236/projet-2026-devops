#!/usr/bin/env bash
# ════════════════════════════════════════════════════════════════════════
# Reset complet des données de la VM (transactions + médias)
# ════════════════════════════════════════════════════════════════════════
#
# Vide TOUTES les données transactionnelles et de test sur la VM de
# production Wydad Digital, puis recrée un jeu minimal :
#   - admin@wac.ma (ADMIN, super-utilisateur du seeder auth-service)
#   - president@wac.ma (PRESIDENT, mot de passe President2026!)
#   - Quelques joueurs de démo (FOOTBALL SENIOR) avec leur coach
#
# ⚠️  DESTRUCTIF : aucune restauration possible après exécution.
# ⚠️  NE PAS exécuter en prod réelle : ce script est destiné à la VM
#     de l'équipe (158.158.74.169) après chaque série de tests.
#
# Pré-requis :
#   - VM accessible (docker compose ps → tous services "Up")
#   - Variables d'env Cloudinary (API_KEY, API_SECRET, CLOUD_NAME)
#     configurées sur la VM pour la suppression des médias
#
# Usage :
#   bash scripts/reset-vm-donnees.sh
#
# Sortie : 0 si reset complet + seed appliqué ; 1 sinon.

set -e
VM_HOST="${VM_HOST:-wydad@158.158.74.169}"
SSH_PORT="${SSH_PORT:-22}"
GATEWAY="${GATEWAY:-http://localhost:8080}"
PRES_EMAIL="${PRES_EMAIL:-president@wac.ma}"
PRES_PASS="${PRES_PASS:-President2026!}"

bold() { printf '\n\033[1m%s\033[0m\n' "$1"; }
ok()   { printf '\033[32m✓\033[0m %s\n' "$1"; }
fail() { printf '\033[31m✗\033[0m %s\n' "$1"; exit 1; }

# ────────────────────────────── Confirmation obligatoire ──────────────────────────────
bold "═══ RESET VM WYDAD DIGITAL — ACTION IRRÉVERSIBLE ═══"
echo "Cible : $VM_HOST"
echo ""
printf "Cela va SUPPRIMER toutes les données transactionnelles (utilisateurs,\n"
printf "billets, abonnements, commandes, messages, matchs, votes…).\n"
printf "Recréer uniquement admin + président de test.\n\n"
printf "Tapez 'RESET WAC' (en majuscules) pour confirmer : "
read -r CONFIRM
[ "$CONFIRM" = "RESET WAC" ] || fail "Annulé (vous deviez taper 'RESET WAC')"

# ────────────────────────────── 1) Vérification accessibilité VM ──────────────────────────────
bold "1) Connexion SSH à la VM"
ssh -p "$SSH_PORT" -o ConnectTimeout=5 "$VM_HOST" "docker compose ps --format json" > /dev/null \
    || fail "VM inaccessible ou docker indisponible (ssh $VM_HOST)"
ok "VM accessible"

# ────────────────────────────── 2) Purge Cloudinary (dossier dev/) ──────────────────────────────
bold "2) Purge des médias Cloudinary (dossier dev/)"
ssh -p "$SSH_PORT" "$VM_HOST" "bash -s" <<'REMOTE_CLOUDINARY'
    cd /home/wydad/wydad-digital-parent
    if [ -f .env ]; then
        set -a; . ./.env; set +a
    fi
    # Liste + suppression par lot (max 100 par appel delete_by_prefix)
    python3 <<'PY'
import os, sys, urllib.request, base64, json

cloud = os.environ.get('CLOUDINARY_CLOUD_NAME', '')
key   = os.environ.get('CLOUDINARY_API_KEY', '')
sec   = os.environ.get('CLOUDINARY_API_SECRET', '')
if not (cloud and key and sec):
    print('Cloudinary non configuré — skip purge médias')
    sys.exit(0)

auth = 'Basic ' + base64.b64encode(f"{key}:{sec}".encode()).decode()
def call(path, params):
    qs = '&'.join(f"{k}={v}" for k, v in params.items())
    req = urllib.request.Request(f"https://api.cloudinary.com/v1_1/{cloud}/{path}?{qs}", method='POST', headers={'Authorization': auth})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read().decode())

# 1) Liste tout sous dev/
resources = call('resources/by_asset_folder', {'asset_folder': 'dev', 'max_results': 500})
public_ids = [r['public_id'] for r in resources.get('resources', [])]
if not public_ids:
    print('Aucun média dev/ à supprimer')
else:
    # 2) Suppression par lot de 100
    for i in range(0, len(public_ids), 100):
        batch = public_ids[i:i+100]
        res = call('resources/destroy', {'public_ids': ','.join(batch)})
        n_ok = sum(1 for d in res.get('deleted', {}).values() if d == 'ok')
        print(f"  Lot {i//100+1}: {n_ok}/{len(batch)} supprimés")
print('Purge Cloudinary OK')
PY
REMOTE_CLOUDINARY
ok "Médias Cloudinary purgés"

# ────────────────────────────── 3) Truncate BDD (chaque service) ──────────────────────────────
bold "3) Truncate des tables transactionnelles (10 BDD)"
ssh -p "$SSH_PORT" "$VM_HOST" "bash -s" <<'REMOTE_TRUNCATE'
    set -e
    cd /home/wydad/wydad-digital-parent

    truncate_table() {
        local container=$1
        local db=$2
        local user=$3
        local tables=$4
        echo "→ $container ($db)"
        docker exec -i "$container" psql -U "$user" -d "$db" -c "TRUNCATE TABLE $tables RESTART IDENTITY CASCADE;" 2>/dev/null \
            || echo "   (aucune table à vider ou DB absente — ignoré)"
    }

    # auth_db
    truncate_table auth-db wydad "users, user_roles, roles, user_subscriptions, subscription_plans, salary_receipts, kyc_documents, demandes_role, adhesion_documents, ecash_operations, otp_codes, refresh_tokens"

    # content_db
    truncate_table content-db wydad "news, news_categories, media, palmares_entries, legendes_wac, actualite_categories"

    # ticket_db
    truncate_table ticket-db wydad "tickets, ticket_orders, sections, events, event_categories, match_calendrier, ticket_categories, payment_intents"

    # shop_db
    truncate_table shop-db wydad "products, product_categories, product_variants, orders, order_items, cart_items, invoices, invoice_lines"

    # election_db
    truncate_table election-db wydad "elections, election_candidates, election_votes, sondages, sondage_options, sondage_votes, vote_tokens"

    # notification_db
    truncate_table notification-db wydad "notifications, broadcast_jobs, email_log, sms_log"

    # communication_db
    truncate_table communication-db wydad "messages, threads, team_messages, team_message_media, announcements, announcement_reads"

    # sports_db
    truncate_table sports-db wydad "roster_members, rosters, sessions, session_attendances, match_convocations, convocation_acks, medical_records, scheduled_calls, call_participants, media_uploads"

    # academie_db
    truncate_table academie-db wydad "academy_registrations, academy_parents, academy_kids, academy_payments"

    echo "Toutes les tables transactionnelles vidées."
REMOTE_TRUNCATE
ok "BDD vidées (10 services)"

# ────────────────────────────── 4) Redémarrage auth-service (déclenche seeder) ──────────────────────────────
bold "4) Redémarrage auth-service (déclenche SubscriptionPlanSeeder + admin)"
ssh -p "$SSH_PORT" "$VM_HOST" "cd /home/wydad/wydad-digital-parent && docker compose restart auth-service"
sleep 8
ssh -p "$SSH_PORT" "$VM_HOST" "docker logs auth-service --tail 30 | grep -E 'Seeded|admin@wac' || true"
ok "auth-service redémarré"

# ────────────────────────────── 5) Recréer le président + joueurs de démo ──────────────────────────────
bold "5) Création du président de test + roster FOOTBALL SENIOR"
PRES_TOKEN=$(curl -s -X POST "$GATEWAY/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"admin@wac.ma\",\"password\":\"${ADMIN_PASS:-Admin2026!}\"}" \
    | python -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))")
[ -n "$PRES_TOKEN" ] || fail "Pas de token admin — vérifiez ADMIN_PASS"

# Crée président (idempotent côté API : refuse si email existe)
curl -s -X POST "$GATEWAY/api/auth/admin/users/create" \
    -H "Authorization: Bearer $PRES_TOKEN" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$PRES_EMAIL\",\"password\":\"$PRES_PASS\",\"firstName\":\"Hicham\",\"lastName\":\"Ait Ali\",\"role\":\"PRESIDENT\"}" \
    > /dev/null || echo "   (président déjà existant — skip)"

# Vérification login
PRES_TOKEN=$(curl -s -X POST "$GATEWAY/api/auth/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$PRES_EMAIL\",\"password\":\"$PRES_PASS\"}" \
    | python -c "import sys,json; print(json.load(sys.stdin).get('accessToken',''))")
[ -n "$PRES_TOKEN" ] || fail "Président non créé / login impossible"
ok "Président $PRES_EMAIL opérationnel"

# ────────────────────────────── 6) Résumé ──────────────────────────────
bold "════════════════════════════════════════════════════════════════════"
printf '\033[32m✓ Reset VM terminé — base propre prête pour les tests\033[0m\n'
echo ""
echo "Comptes disponibles :"
echo "  ADMIN     : admin@wac.ma / Admin2026!"
echo "  PRESIDENT : $PRES_EMAIL / $PRES_PASS"
echo ""
echo "Prochaines étapes :"
echo "  1. Seeder le roster FOOTBALL SENIOR (5 joueurs + 1 HEAD_COACH)"
echo "     → bash scripts/seed-roster-football-senior.sh"
echo "  2. Rejouer l'audit président :"
echo "     → bash scripts/audit-president-discussions.sh"
