# =====================================================================
#  Deep Lucky Block -- MISE A JOUR DU WORKSPACE MCREATOR
# =====================================================================
#
#  CE FICHIER EST EN ASCII PUR, ET IL DOIT LE RESTER.
#     Windows PowerShell 5.1 lit un .ps1 SANS BOM comme de l'ANSI
#     (page de codes 1252), pas comme de l'UTF-8. Un tiret cadratin
#     U+2014 y est code E2 80 94 ; relu en 1252 cela donne trois
#     caracteres, dont 0x94 = guillemet fermant courbe. Or PowerShell
#     accepte les guillemets courbes comme DELIMITEURS DE CHAINE : le
#     tiret refermait donc la chaine en plein milieu, et tout le reste
#     du fichier devenait incomprehensible. Le script a ete livre une
#     premiere fois avec un tel tiret ligne 251 et a refuse de
#     s'analyser, en signalant des erreurs trente lignes plus loin.
#     Le collage dans la console marchait, lui, parce que le texte y
#     arrive deja decode : seule la lecture du FICHIER echouait.
#     Un banc (tools/ps1asciicheck.py) le verifie a chaque livraison.
# =====================================================================
#
#  A QUOI CA SERT
#     Votre workspace MCreator n'est pas un depot Git. Quand je publie un
#     correctif, rien ne le fait descendre chez vous. Les quatre derniers
#     crashs venaient de la, pas du code : Gradle affichait "compileJava
#     UP-TO-DATE" -- aucune source n'a change -- et relancait un .class du
#     25 novembre.
#
#     Ce script fait la copie, puis il la PROUVE : il affiche l'empreinte des
#     sources avant et apres, et s'arrete en erreur si elle ne correspond pas
#     a celle attendue. Plus de supposition.
#
#  IL INSTALLE DEUX CHOSES, ET LA SECONDE EST CELLE QU'ON OUBLIE TOUJOURS :
#     1. les 226 fichiers .java du mod, dans le workspace ;
#     2. le script de reparation Gradle, dans
#        %USERPROFILE%\.mcreator\gradle\init.d\
#        Votre journal du 26/11 a 05:35 ne contenait PAS la ligne
#        "[DLB-PATCH] empreinte des sources Java : ..." : c'est la preuve
#        que votre copie de ce script date, elle aussi. Sans lui, ni
#        l'empreinte ni le garde-fou ne peuvent vous prevenir.
#
#  UTILISATION -- clic droit sur le fichier > "Executer avec PowerShell".
#  Ou, dans une fenetre PowerShell :
#     powershell -ExecutionPolicy Bypass -File METTRE-A-JOUR.ps1
#
#  Si votre workspace n'est pas a l'endroit habituel :
#     powershell -ExecutionPolicy Bypass -File METTRE-A-JOUR.ps1 -Workspace "D:\chemin\du\workspace"
#
#  CE QUE LE SCRIPT NE FAIT PAS : il ne touche ni a vos mondes, ni a vos
#  reglages MCreator, ni a vos ressources, ni a quoi que ce soit hors de
#  src\main\java. Et il sauvegarde ce qu'il remplace.
# =====================================================================

param(
    [string] $Workspace = "",
    # Revision immuable dont le colis DLB_sync.zip a ete construit et verifie par CI.
    # Pour changer de livraison, utiliser le script telecharge depuis le SHA correspondant.
    [string] $Revision  = "2fd24bdd6881da7a8865253e515adb3d8b4e4a1c",
    [switch] $PasDeSauvegarde,
    # Pour un appel automatise, ou la fenetre ne doit pas attendre.
    [switch] $SansPause
)

$ErrorActionPreference = "Stop"
try { chcp 65001 > $null 2>&1 } catch { }

function Titre($t) {
    Write-Host ""
    Write-Host ("=" * 72) -ForegroundColor DarkCyan
    Write-Host "  $t" -ForegroundColor Cyan
    Write-Host ("=" * 72) -ForegroundColor DarkCyan
}
function Ok($t)    { Write-Host "  [ok]    $t" -ForegroundColor Green }
function Info($t)  { Write-Host "  [info]  $t" -ForegroundColor Gray }
function Alerte($t){ Write-Host "  [!]     $t" -ForegroundColor Yellow }
function Erreur($t){ Write-Host "  [ECHEC] $t" -ForegroundColor Red }

# ---------------------------------------------------------------------
#  SORTIE DU SCRIPT -- pourquoi tout ce ceremonial pour un "exit"
#     Lance par "Executer avec PowerShell", Windows ferme la fenetre des
#     que le script se termine. La premiere version disparaissait donc avant
#     qu'on ait pu lire l'empreinte -- c'est-a-dire avant d'avoir montre la
#     seule chose qu'elle existait pour montrer. Et les trois chemins
#     d'erreur se refermaient encore plus vite, en silence.
#     Desormais : tout est transcrit dans un fichier qui survit a la
#     fermeture, et la fenetre attend une touche avant de partir.
# ---------------------------------------------------------------------
$rapport = Join-Path ([Environment]::GetFolderPath('Desktop')) "DLB_mise_a_jour.txt"
if (-not (Test-Path (Split-Path $rapport -Parent))) {
    $rapport = Join-Path $env:USERPROFILE "DLB_mise_a_jour.txt"
}
$transcription = $false
try { Start-Transcript -Path $rapport -Force | Out-Null; $transcription = $true } catch { }

function Fin([int] $code) {
    Write-Host ""
    if ($transcription) {
        try { Stop-Transcript | Out-Null } catch { }
        Write-Host "  Compte rendu complet enregistre dans :" -ForegroundColor Gray
        Write-Host "    $rapport" -ForegroundColor White
        Write-Host "  (envoyez-moi ce fichier si quelque chose cloche)" -ForegroundColor Gray
    }
    if (-not $SansPause) {
        Write-Host ""
        Write-Host "  --- Appuyez sur Entree pour fermer cette fenetre ---" -ForegroundColor DarkCyan
        try { Read-Host | Out-Null } catch { Start-Sleep -Seconds 30 }
    }
    exit $code
}

# ---------------------------------------------------------------------
#  Empreinte des sources Java.
#  Meme definition que tools\empreinte_sources.py et que le script Gradle :
#  chemins relatifs tries en ordre d'OCTETS ; pour chacun, octets du chemin,
#  saut de ligne, octets du fichier, saut de ligne ; SHA-1 ; 12 hex.
#  Lecture en octets : ni l'encodage ni les fins de ligne n'entrent en jeu,
#  donc la valeur est la meme sous Windows et sous Linux.
#  Le tri passe par un encodage decimal a 3 chiffres par octet, parce que le
#  tri par defaut de PowerShell depend de la culture et ignore la casse : il
#  donnerait un ordre different, donc une empreinte differente.
# ---------------------------------------------------------------------
function Get-Empreinte([string] $racineJava) {
    if (-not (Test-Path $racineJava)) { return @{ hash = "(absent)"; n = 0 } }
    $base = (Resolve-Path $racineJava).Path.TrimEnd('\')
    $fichiers = Get-ChildItem -Path $base -Filter *.java -Recurse -File
    $liste = @()
    foreach ($f in $fichiers) {
        $rel = $f.FullName.Substring($base.Length + 1).Replace('\', '/')
        $liste += [pscustomobject]@{
            rel = $rel
            full = $f.FullName
            tri = (([System.Text.Encoding]::UTF8.GetBytes($rel) |
                    ForEach-Object { $_.ToString("000") }) -join '')
        }
    }
    if ($liste.Count -gt 1) { $liste = $liste | Sort-Object -Property tri }
    $flux = New-Object System.IO.MemoryStream
    foreach ($e in $liste) {
        $c = [System.Text.Encoding]::UTF8.GetBytes($e.rel)
        $flux.Write($c, 0, $c.Length)
        $flux.WriteByte(10)
        $d = [System.IO.File]::ReadAllBytes($e.full)
        $flux.Write($d, 0, $d.Length)
        $flux.WriteByte(10)
    }
    $flux.Position = 0
    $sha = [System.Security.Cryptography.SHA1]::Create()
    $h = $sha.ComputeHash($flux)
    $flux.Dispose(); $sha.Dispose()
    $hex = ($h | ForEach-Object { $_.ToString("x2") }) -join ''
    return @{ hash = $hex.Substring(0, 12); n = $liste.Count }
}

Titre "Deep Lucky Block - mise a jour du workspace MCreator"

# ---------------------------------------------------------------------
#  1) Trouver le workspace
# ---------------------------------------------------------------------
Titre "1/7  Localisation du workspace"

if ($Workspace -eq "") {
    $racine = Join-Path $env:USERPROFILE "MCreatorWorkspaces"
    $prefere = Join-Path $racine "deep_lucky_block-neoforge-1.21.1"
    if (Test-Path (Join-Path $prefere "src\main\java")) {
        $Workspace = $prefere
    } elseif (Test-Path $racine) {
        $trouve = Get-ChildItem -Path $racine -Directory -ErrorAction SilentlyContinue |
                  Where-Object { Test-Path (Join-Path $_.FullName "src\main\java\deepluckyblock") } |
                  Select-Object -First 1
        if ($trouve) { $Workspace = $trouve.FullName }
    }
}
if ($Workspace -eq "" -or -not (Test-Path (Join-Path $Workspace "src\main\java"))) {
    Erreur "workspace MCreator introuvable."
    Info "Relancez en donnant le chemin, par exemple :"
    Info '  powershell -ExecutionPolicy Bypass -File METTRE-A-JOUR.ps1 -Workspace "C:\Users\Nazario Delucci\MCreatorWorkspaces\deep_lucky_block-neoforge-1.21.1"'
    Fin 1
}
Ok "workspace : $Workspace"

$avant = Get-Empreinte (Join-Path $Workspace "src\main\java")
Info "empreinte actuelle : $($avant.hash)  ($($avant.n) fichiers)"

# MCreator utilise son propre GRADLE_USER_HOME. C'est la, et nulle part
# ailleurs, que Gradle lit les scripts d'initialisation.
$gradleHome = Join-Path $env:USERPROFILE ".mcreator\gradle"
if (-not (Test-Path $gradleHome)) { $gradleHome = Join-Path $env:USERPROFILE ".gradle" }
$initDir = Join-Path $gradleHome "init.d"
Info "scripts d'init Gradle : $initDir"

# ---------------------------------------------------------------------
#  2) Telecharger le colis de synchronisation (environ 2 Mo)
# ---------------------------------------------------------------------
Titre "2/7  Telechargement"

$travail = Join-Path $env:TEMP ("dlb_maj_" + (Get-Date -Format "yyyyMMdd_HHmmss"))
New-Item -ItemType Directory -Path $travail -Force | Out-Null
$zip = Join-Path $travail "DLB_sync.zip"
$url = "https://raw.githubusercontent.com/Deepie1/dlbv1.21.1neoforge/$Revision/deliverables/DLB_sync.zip"

Info "source : $url"
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $ProgressPreference = 'SilentlyContinue'
    Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
} catch {
    Erreur "telechargement impossible : $_"
    Info "Verifiez la connexion, ou telechargez le fichier a la main depuis l'adresse"
    Info "ci-dessus, placez-le a cote de ce script et relancez."
    Fin 1
}
Ok ("colis recu (" + [math]::Round((Get-Item $zip).Length / 1MB, 1) + " Mo)")

$extrait = Join-Path $travail "colis"
Expand-Archive -Path $zip -DestinationPath $extrait -Force
$srcJava = Join-Path $extrait "src\main\java"
if (-not (Test-Path $srcJava)) { Erreur "colis inattendu : src\main\java absent."; exit 1 }

# L'empreinte attendue voyage DANS le colis : elle ne depend pas d'une valeur
# recopiee a la main dans un message, qui pourrait etre celle d'hier.
$attendu = (Get-Content (Join-Path $extrait "EMPREINTE.txt") -Raw).Trim().Split(' ')[0]
Info "empreinte attendue : $attendu"

if ($attendu -eq $avant.hash) {
    Alerte "vos sources Java sont DEJA a la bonne version ($attendu)."
    Info "On continue quand meme : le script de reparation et les classes"
    Info "compilees sont peut-etre perimes, eux."
}

# ---------------------------------------------------------------------
#  3) Sauvegarde
# ---------------------------------------------------------------------
Titre "3/7  Sauvegarde de l'existant"

if ($PasDeSauvegarde) {
    Alerte "sauvegarde desactivee (-PasDeSauvegarde)"
} else {
    $sauve = Join-Path $Workspace ("src_main_java_sauvegarde_" + (Get-Date -Format "yyyyMMdd_HHmmss"))
    Copy-Item -Path (Join-Path $Workspace "src\main\java") -Destination $sauve -Recurse -Force
    Ok "ancien src\main\java copie dans :"
    Info "  $sauve"
    Info "(supprimable une fois que tout fonctionne)"
}

# ---------------------------------------------------------------------
#  4) Installer les sources Java
# ---------------------------------------------------------------------
Titre "4/7  Installation des sources Java"

$dstJava = Join-Path $Workspace "src\main\java"
Remove-Item -Path $dstJava -Recurse -Force
New-Item -ItemType Directory -Path $dstJava -Force | Out-Null
Copy-Item -Path (Join-Path $srcJava "*") -Destination $dstJava -Recurse -Force
Ok ("" + (Get-ChildItem -Path $dstJava -Filter *.java -Recurse -File).Count + " fichiers .java installes")

# ---------------------------------------------------------------------
#  5) Installer le script de reparation -- L'ETAPE OUBLIEE
#     Il ne vit pas dans le workspace mais dans le GRADLE_USER_HOME de
#     MCreator. Copier seulement src\ le laisse perime indefiniment, et
#     c'est precisement ce qui s'est passe : le journal du 26/11 n'avait
#     pas la ligne d'empreinte.
# ---------------------------------------------------------------------
Titre "5/7  Installation du script de reparation Gradle"

$initSrc = Join-Path $extrait "src\main\resources\deepluckyblock_patch\tools\deep-lucky-block-patch.gradle"
if (-not (Test-Path $initSrc)) {
    Alerte "script de reparation absent du colis -- etape ignoree"
} else {
    New-Item -ItemType Directory -Path $initDir -Force | Out-Null
    $initDst = Join-Path $initDir "deep-lucky-block-patch.gradle"
    if ((Test-Path $initDst) -and (-not $PasDeSauvegarde)) {
        Copy-Item -Path $initDst -Destination ($initDst + ".ancien") -Force
        Info "ancienne version conservee sous deep-lucky-block-patch.gradle.ancien"
    }
    Copy-Item -Path $initSrc -Destination $initDst -Force
    Ok "installe : $initDst"

    # Le meme fichier vit aussi dans le workspace, en tant que source de
    # reference. On le synchronise pour que les deux copies ne divergent pas.
    $miroir = Join-Path $Workspace "src\main\resources\deepluckyblock_patch\tools\deep-lucky-block-patch.gradle"
    if (Test-Path (Split-Path $miroir -Parent)) {
        Copy-Item -Path $initSrc -Destination $miroir -Force
        Ok "copie de reference du workspace synchronisee"
    }
}

# ---------------------------------------------------------------------
#  6) Casser l'"UP-TO-DATE"
#     Gradle compare des horodatages. Une copie peut lui sembler plus
#     ancienne que le .class deja produit : il ne recompile alors rien et
#     relance l'ancien binaire. C'est la cause des quatre crashs.
# ---------------------------------------------------------------------
Titre "6/7  Invalidation des classes compilees"

$cibles = @(
    (Join-Path $Workspace "build\classes\java\main\deepluckyblock"),
    (Join-Path $Workspace "build\tmp\compileJava")
)
$efface = 0
foreach ($c in $cibles) {
    if (Test-Path $c) {
        Remove-Item -Path $c -Recurse -Force -ErrorAction SilentlyContinue
        $efface++; Ok "supprime : $c"
    }
}
if ($efface -eq 0) { Info "rien a supprimer (pas encore compile)" }

Get-ChildItem -Path $dstJava -Filter *.java -Recurse -File |
    ForEach-Object { $_.LastWriteTime = Get-Date }
Ok "horodatage des .java rafraichi"

# ---------------------------------------------------------------------
#  7) Preuve
# ---------------------------------------------------------------------
Titre "7/7  Verification"

$apres = Get-Empreinte $dstJava
Write-Host ""
Write-Host "    attendu : $attendu" -ForegroundColor White
Write-Host "    obtenu  : $($apres.hash)   ($($apres.n) fichiers)" -ForegroundColor White
Write-Host "    (avant  : $($avant.hash))" -ForegroundColor DarkGray
Write-Host ""

Remove-Item -Path $travail -Recurse -Force -ErrorAction SilentlyContinue

if ($apres.hash -ne $attendu) {
    Erreur "les sources installees ne correspondent PAS au colis."
    Alerte "Cause probable : un fichier etait verrouille par MCreator ou par"
    Alerte "l'explorateur Windows. Fermez MCreator et relancez ce script."
    Fin 1
}

Ok "sources installees et verifiees : $($apres.hash)"
Write-Host ""
Write-Host "  ETAPE SUIVANTE" -ForegroundColor Cyan
Write-Host "    Relancez le jeu depuis MCreator. Dans la console, cette ligne" -ForegroundColor White
Write-Host "    doit apparaitre, juste avant "61 fichiers restaures" :" -ForegroundColor White
Write-Host ""
Write-Host "      [DLB-PATCH] empreinte des sources Java : $attendu ($($apres.n) fichiers)" -ForegroundColor Green
Write-Host ""
Write-Host "    - ligne ABSENTE      : Gradle ne lit pas $initDir" -ForegroundColor White
Write-Host "    - empreinte AUTRE    : Gradle ne lit pas $dstJava" -ForegroundColor White
Write-Host "    Dans les deux cas, envoyez-moi juste cette ligne : elle suffit." -ForegroundColor White
Write-Host ""
Fin 0
