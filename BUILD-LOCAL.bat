@echo off
REM ============================================================
REM  BUILD DU MOD APRES COLLAGE DES FICHIERS LATEST (Windows)
REM
REM  1. Colle ce fichier .bat A LA RACINE de ton projet MCreator
REM     (la ou se trouve gradlew.bat) — par exemple dans le dossier
REM     extrait de deep_lucky_block_mcreator.zip.
REM  2. Avant de double-cliquer : colle les 8 fichiers LATEST_*.java
REM     par-dessus les fichiers du meme nom dans src/.
REM  3. Double-clique. Le JAR final sort dans build\libs\.
REM ============================================================
title DeepLuckyBlock build (serie F)
echo Verification des 8 fichiers LATEST colles...
findstr /c:"worldEditFixWater" src\main\java\deepluckyblock\procedures\StructureTerrainPrep.java >nul || (echo [ABSENT] T107 dans StructureTerrainPrep.java -- recolle ! & pause & exit /b 1)
findstr /c:"ClearFootprintJob" src\main\java\deepluckyblock\procedures\StructureTerrainPrep.java >nul || (echo [ABSENT] T108 dans StructureTerrainPrep.java -- recolle ! & pause & exit /b 1)
findstr /c:"SMOOTH_KERNEL_SCHEDULE_T109" src\main\java\deepluckyblock\procedures\StructureTerrainPrep.java >nul || (echo [ABSENT] T109 -- recolle ! & pause & exit /b 1)
findstr /c:"T111" src\main\java\deepluckyblock\procedures\CrimsonLakeStructureSpawnProcedure.java >nul || (echo [ABSENT] T111 crimson -- recolle ! & pause & exit /b 1)
if not exist src\main\java\deepluckyblock\util\CrimsonButterflyRetaliation.java (echo [ABSENT] nouveau fichier CrimsonButterflyRetaliation.java -- cree-le ! & pause & exit /b 1)
echo OK, les fichiers serie F sont en place. Lancement du build...
call gradlew.bat build
if errorlevel 1 (echo ECHEC BUILD -- copie les erreurs au-dessus & pause & exit /b 1)
echo.
echo ==== BUILD OK ====
echo Le JAR du mod est dans : build\libs\
dir /b build\libs\*.jar
echo Copie ce .jar dans le dossier mods de ton client/serveur Minecraft.
pause