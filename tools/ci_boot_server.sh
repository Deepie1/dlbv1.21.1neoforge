#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════
# DEMARRAGE D'UN SERVEUR DEDIE NEOFORGE, AVEC VERDICT
# ═══════════════════════════════════════════════════════════════════════════
#
# Usage :  tools/ci_boot_server.sh <fichier_log> <attendu> [etiquette]
#            attendu = demarre  -> le serveur DOIT atteindre « Done (...s)! »
#            attendu = casse    -> le serveur DOIT refuser de charger le mod
#
# Pourquoi un script separe plutot que du YAML en ligne : le banc doit servir
# DEUX fois dans le meme job -- une fois sur le code livre, une fois sur le
# code qui a crashe le 25/11. Deux copies du meme code divergent toujours ;
# une seule, appelee deux fois, ne peut pas mentir sur l'une des deux.
#
# Le verdict ne depend JAMAIS du code de sortie de Gradle : un serveur tue par
# le banc sort en erreur alors qu'il a parfaitement demarre. Seul le journal
# fait foi.
# ═══════════════════════════════════════════════════════════════════════════
set -uo pipefail

LOG="${1:?chemin du journal attendu}"
ATTENDU="${2:?attendu : demarre ou casse}"
ETIQ="${3:-}"

case "$ATTENDU" in
  demarre|casse) ;;
  *) echo "attendu doit valoir 'demarre' ou 'casse', recu : $ATTENDU" >&2; exit 2 ;;
esac

# Marqueurs. Le succes est la ligne que le serveur dedie imprime quand il est
# pret a accepter des joueurs. L'echec regroupe les quatre signatures d'un mod
# qui ne charge pas, toutes relevees dans le vrai crash du 25/11.
SUCCES='Done \([0-9.]+s\)!'
ECHEC='ModLoadingCrashException|has failed to load correctly|Cowardly refusing to send event|Failed to register class'

note() { echo "::notice::${ETIQ:+[$ETIQ] }$*"; }
err()  { echo "::error::${ETIQ:+[$ETIQ] }$*"; }

mkdir -p "$(dirname "$LOG")"
: > "$LOG"

# setsid : le serveur tourne dans sa propre session, ce qui permet de tuer tout
# le groupe (Gradle et la JVM qu'il forke) une fois le verdict pris.
setsid bash -c './gradlew --no-daemon --stacktrace runServer' > "$LOG" 2>&1 &
PGID=$!

verdict=limite_de_temps
for _ in $(seq 1 3000); do            # 50 minutes au maximum
  if grep -qE "$SUCCES" "$LOG" 2>/dev/null; then verdict=demarre; break; fi
  if grep -qE "$ECHEC"  "$LOG" 2>/dev/null; then verdict=mod_casse; break; fi
  if ! kill -0 "$PGID" 2>/dev/null;          then verdict=sorti_prematurement; break; fi
  sleep 1
done

kill -TERM -"$PGID" 2>/dev/null || true
sleep 10
kill -KILL -"$PGID" 2>/dev/null || true

# ── PREUVES ────────────────────────────────────────────────────────────────
# Les journaux bruts d'Actions ne sont pas lisibles depuis le bac a sable de
# l'agent : les faits qui comptent sont donc republies en annotations.
note "verdict=$verdict (attendu=$ATTENDU)"

# Aucune de ces lectures ne doit pouvoir faire echouer le banc : ce sont des
# temoins, pas des criteres. Un « grep ... && echo ... » renvoie 1 quand le
# motif est absent -- sous le « bash -e » que GitHub impose aux etapes, cela
# suffisait a faire echouer un demarrage parfaitement reussi. Constate le
# 26/11 : le serveur affichait « Done (10.279s)! » et l'etape sortait en 1.
PRET=$(grep -oE "$SUCCES.*" "$LOG" 2>/dev/null | tail -1 | tr -d '\r' || true)
if [ -n "$PRET" ]; then note "serveur pret — $PRET"; fi

# Cette ligne-ci n'est emise qu'en DEBUG ; le serveur tourne en INFO, donc son
# absence ne veut rien dire. Elle est signalee a titre indicatif seulement.
if grep -q 'Subscribing @EventBusSubscriber class Ldeepluckyblock/procedures/CrimsonLakeStructureSpawnProcedure' "$LOG" 2>/dev/null; then
  note "temoin : gestionnaire du lac enregistre sur le bus (visible car journal en DEBUG)"
fi

FAUTE=$(grep -m1 -oE '[A-Za-z0-9_.]+\.[A-Za-z0-9_]+\([^)]*\) annotated with @SubscribeEvent[^.]*' "$LOG" 2>/dev/null | tr -d '\r' || true)
if [ -n "$FAUTE" ]; then note "defaut @SubscribeEvent : $FAUTE"; fi

if [ -n "${GITHUB_STEP_SUMMARY:-}" ]; then
  {
    echo "### Banc de demarrage${ETIQ:+ — $ETIQ}"
    echo
    echo "| element | valeur |"
    echo "|---|---|"
    echo "| attendu | \`$ATTENDU\` |"
    echo "| verdict | **\`$verdict\`** |"
    [ -n "$PRET" ]  && echo "| serveur | $PRET |"
    [ -n "$FAUTE" ] && echo "| faute | \`$FAUTE\` |"
    echo
    echo '<details><summary>40 dernieres lignes</summary>'
    echo
    echo '```'
    tail -40 "$LOG"
    echo '```'
    echo
    echo '</details>'
  } >> "$GITHUB_STEP_SUMMARY"
fi

echo "::group::200 dernieres lignes du serveur${ETIQ:+ ($ETIQ)}"
tail -200 "$LOG"
echo "::endgroup::"

# ── DECISION ───────────────────────────────────────────────────────────────
if [ "$ATTENDU" = casse ]; then
  if [ "$verdict" = demarre ]; then
    err "LE BANC N'A PAS MORDU sur un code repute casse : il ne protege de rien."
    exit 1
  fi
  echo "Controle negatif concluant : le banc a bien refuse ce code ($verdict)."
  exit 0
fi

case "$verdict" in
  demarre)
    echo "Le serveur a atteint « Done ( ... s)! » : le mod charge et le serveur tourne."
    exit 0 ;;
  mod_casse)
    err "Le mod ne charge pas."
    grep -nE 'ModLoadingCrashException|has failed to load correctly|Failed to register class|IllegalArgumentException' "$LOG" | head -20
    exit 1 ;;
  sorti_prematurement)
    err "Le build ou le serveur s'est arrete avant d'etre pret."
    tail -60 "$LOG"
    exit 1 ;;
  *)
    err "Le serveur n'a jamais atteint « Done ( » dans le temps imparti."
    exit 1 ;;
esac
