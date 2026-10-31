@echo off
setlocal enabledelayedexpansion
chcp 65001 >nul 2>&1
cd /d "%~dp0"

echo.
echo ===========================================================
echo   DEEP LUCKY BLOCK - CONSTRUCTION DU MOD
echo ===========================================================
echo.
echo   Ce fichier doit etre place A LA RACINE du workspace,
echo   a cote de build.gradle. Dossier actuel :
echo   %CD%
echo.

set "KIT=src\main\resources\deepluckyblock_patch\FIXES_MCREATOR"

if not exist "build.gradle" (
  echo [ERREUR] build.gradle introuvable ici.
  echo          Ce .bat n'est pas au bon endroit. Il doit etre
  echo          dans le dossier du workspace MCreator.
  echo.
  pause
  exit /b 1
)

if not exist "%KIT%" (
  echo [ERREUR] Kit de reparation introuvable :
  echo          %KIT%
  echo.
  pause
  exit /b 1
)

echo -----------------------------------------------------------
echo  ETAPE 1/3  Restauration des fichiers cadre MCreator
echo -----------------------------------------------------------

rem --- les 8 fichiers Java generes (bugues) par MCreator ---
if not exist "src\main\java\deepluckyblock\init"    mkdir "src\main\java\deepluckyblock\init"
if not exist "src\main\java\deepluckyblock\network" mkdir "src\main\java\deepluckyblock\network"
if not exist "src\main\java\deepluckyblock\mixin"   mkdir "src\main\java\deepluckyblock\mixin"

set ERR=0
call :CP "%KIT%\src\main\java\deepluckyblock\DeepLuckyBlockMod.java"                      "src\main\java\deepluckyblock\"
call :CP "%KIT%\src\main\java\deepluckyblock\init\DeepLuckyBlockModEntities.java"         "src\main\java\deepluckyblock\init\"
call :CP "%KIT%\src\main\java\deepluckyblock\init\DeepLuckyBlockModEntityRenderers.java"  "src\main\java\deepluckyblock\init\"
call :CP "%KIT%\src\main\java\deepluckyblock\init\DeepLuckyBlockModItems.java"            "src\main\java\deepluckyblock\init\"
call :CP "%KIT%\src\main\java\deepluckyblock\init\DeepLuckyBlockModModels.java"           "src\main\java\deepluckyblock\init\"
call :CP "%KIT%\src\main\java\deepluckyblock\init\DeepLuckyBlockModTabs.java"             "src\main\java\deepluckyblock\init\"
call :CP "%KIT%\src\main\java\deepluckyblock\network\DeepLuckyBlockModVariables.java"     "src\main\java\deepluckyblock\network\"
call :CP "%KIT%\src\main\java\deepluckyblock\mixin\MultiBufferSourceEnchantMixin.java.disabled" "src\main\java\deepluckyblock\mixin\"

rem --- le mixin NON desactive casse la compilation ---
if exist "src\main\java\deepluckyblock\mixin\MultiBufferSourceEnchantMixin.java" (
  del /F /Q "src\main\java\deepluckyblock\mixin\MultiBufferSourceEnchantMixin.java"
  echo   [-] MultiBufferSourceEnchantMixin.java supprime
)

rem --- les 2 ressources ---
if not exist "src\main\resources\META-INF" mkdir "src\main\resources\META-INF"
call :CP "%KIT%\src\main\resources\META-INF\neoforge.mods.toml" "src\main\resources\META-INF\"
call :CP "%KIT%\src\main\resources\deepluckyblock.mixins.json"  "src\main\resources\"
if exist "src\main\resources\META-INF\neoforged.mods.toml" del /F /Q "src\main\resources\META-INF\neoforged.mods.toml"

rem --- les 51 advancements ---
if not exist "src\main\resources\data\deep_lucky_block\advancement" mkdir "src\main\resources\data\deep_lucky_block\advancement"
xcopy /Y /Q "%KIT%\data\deep_lucky_block\advancement\*.json" "src\main\resources\data\deep_lucky_block\advancement\" >nul 2>&1
if errorlevel 1 (
  echo   [!] advancements : echec de la copie
  set ERR=1
) else (
  echo   [OK] 51 advancements
)

if "%ERR%"=="1" (
  echo.
  echo [ERREUR] Au moins un fichier n'a pas pu etre copie. On s'arrete.
  echo.
  pause
  exit /b 1
)

echo.
echo -----------------------------------------------------------
echo  ETAPE 2/3  Compilation
echo -----------------------------------------------------------
echo  Cela peut prendre plusieurs minutes. Ne pas fermer.
echo.

if not exist "gradlew.bat" (
  echo [ERREUR] gradlew.bat introuvable dans ce dossier.
  echo          Lancer une fois le jeu depuis MCreator pour qu'il
  echo          le genere, puis relancer ce .bat.
  echo.
  pause
  exit /b 1
)

call gradlew.bat build
set BUILDRC=%ERRORLEVEL%

echo.
echo -----------------------------------------------------------
echo  ETAPE 3/3  Resultat
echo -----------------------------------------------------------

if not "%BUILDRC%"=="0" (
  echo.
  echo  [ECHEC] La compilation a echoue. Remonter le texte
  echo          ci-dessus en entier.
  echo.
  pause
  exit /b 1
)

echo.
echo  [SUCCES] Le mod se trouve ici :
echo.
dir /b "build\libs\*.jar" 2>nul
echo.
echo  Dossier complet :
echo  %CD%\build\libs
echo.
echo  Prendre le .jar SANS suffixe (pas -sources, -dev, -slim, -all)
echo  et le renommer en DeepLuckyBlock-1.0.jar pour le distribuer.
echo.
echo  Ouverture du dossier...
start "" "%CD%\build\libs"
echo.
pause
exit /b 0

rem ===========================================================
:CP
if not exist %1 (
  echo   [!] MANQUANT : %~nx1
  set ERR=1
  goto :eof
)
copy /Y %1 %2 >nul
if errorlevel 1 (
  echo   [!] ECHEC   : %~nx1
  set ERR=1
) else (
  echo   [OK] %~nx1
)
goto :eof
