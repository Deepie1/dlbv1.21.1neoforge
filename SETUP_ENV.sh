#!/usr/bin/env bash
# ==========================================================================
# SETUP_ENV.sh — reconstruit l'environnement de build dans un sandbox neuf
# ==========================================================================
#
# POURQUOI CE SCRIPT
# ------------------
# Le sandbox d'Arena est remis a zero entre les sessions : /opt (JDK 21,
# cache Gradle) et /tmp disparaissent. Seul /home/user est conserve (c'est le
# workspace que tu telecharges). Or le JDK 21 + le cache Gradle NeoForge pesent
# plus de 1 Go : les installer dans /home/user ferait exploser la taille du
# workspace (et donc du zip a telecharger).
#
# SOLUTION : on installe l'outillage HORS du workspace, dans /var/tmp
# (inscriptible, non persiste, sans impact sur le zip). Ce script le refait
# en une commande.
#
# USAGE
#   bash SETUP_ENV.sh          # installe le JDK 21 + lance un build de chauffe
#   bash SETUP_ENV.sh build    # installe si besoin, puis build seulement
#
# Ensuite, pour builder :
#   JAVA_HOME=/var/tmp/jdk-21 GRADLE_USER_HOME=/var/tmp/gradle-home \
#     JAVA_TOOL_OPTIONS="-Xmx700m -XX:+UseSerialGC -XX:ActiveProcessorCount=1 -XX:MaxMetaspaceSize=256m" \
#     sh ./gradlew build --console=plain --max-workers=1
# ==========================================================================
set -u

# Repertoire du script, capture AVANT tout cd (sinon cd . reste dans /var/tmp)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

JDK_DIR=/var/tmp/jdk-21
GRADLE_HOME=/var/tmp/gradle-home
JDK_URL="https://github.com/adoptium/temurin21-binaries/releases/download/jdk-21.0.12.1%2B1/OpenJDK21U-jdk_x64_linux_hotspot_21.0.12.1_1.tar.gz"

if [ ! -x "$JDK_DIR/bin/javac" ]; then
  echo "[SETUP] Telechargement du JDK 21 (Temurin 21.0.12.1) ..."
  mkdir -p /var/tmp && cd /var/tmp || exit 1
  curl -L --fail -o jdk21.tar.gz "$JDK_URL" || { echo "[SETUP] Echec du telechargement"; exit 1; }
  echo "[SETUP] Extraction ..."
  rm -rf /var/tmp/jdk-21-extract && mkdir -p /var/tmp/jdk-21-extract
  tar -xzf jdk21.tar.gz -C /var/tmp/jdk-21-extract --strip-components=1
  rm -rf "$JDK_DIR" && mv /var/tmp/jdk-21-extract "$JDK_DIR"
  rm -f /var/tmp/jdk21.tar.gz
else
  echo "[SETUP] JDK deja present : $JDK_DIR"
fi

"$JDK_DIR/bin/java" -version 2>&1 | head -1
mkdir -p "$GRADLE_HOME"

cd "$SCRIPT_DIR" || exit 1
chmod +x gradlew 2>/dev/null

echo "[SETUP] Build de chauffe (telecharge Gradle 9.4.1 + NeoForge 21.1.77 au 1er lancement) ..."
JAVA_HOME="$JDK_DIR" GRADLE_USER_HOME="$GRADLE_HOME" \
  JAVA_TOOL_OPTIONS="-Xmx700m -XX:+UseSerialGC -XX:ActiveProcessorCount=1 -XX:MaxMetaspaceSize=256m" \
  sh ./gradlew build --console=plain --max-workers=1 2>&1 | tail -12
