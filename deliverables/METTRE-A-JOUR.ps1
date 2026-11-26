# =====================================================================
#  Deep Lucky Block — MISE A JOUR DU WORKSPACE MCREATOR
# =====================================================================
#
#  A QUOI CA SERT
#     Votre workspace MCreator n'est pas un depot Git. Quand je publie un
#     correctif, rien ne le fait descendre chez vous : il faut recopier les
#     fichiers a la main. Les trois derniers crashs venaient de la — pas du
#     code. Gradle disait « compileJava UP-TO-DATE », c'est-a-dire « aucune
#     source n'a change », et rejouait un .class de la veille.
#
#     Ce script fait la copie, puis il la PROUVE : il affiche l'empreinte des
#     sources avant et apres. Si elle ne change pas alors qu'elle aurait du,
#     il vous le dit au lieu de vous laisser croire que c'est fait.
#
#  UTILISATION — clic droit sur le fichier > « Executer avec PowerShell ».
#  Ou, dans une fenetre PowerShell :
#     powershell -ExecutionPolicy Bypass -File METTRE-A-JOUR.ps1
#
#  Si votre workspace n'est pas a l'endroit habituel :
#     powershell -ExecutionPolicy Bypass -File METTRE-A-JOUR.ps1 -Workspace "D:\chemin\vers\le\workspace"
#
#  CE QUE LE SCRIPT NE FAIT PAS : il ne touche ni a vos mondes, ni a vos
#  reglages MCreator, ni a quoi que ce soit hors de src\. Et il sauvegarde
#  l'ancien src\ avant de le remplacer.
# =====================================================================

param(
    [string] $Workspace = "",
    # Par defaut : la derniere version de la branche de travail. L'identite
    # de ce qui est installe n'est pas le numero de commit mais l'empreinte
    # affichee en fin de script — c'est elle qu'on compare.
    # Pour figer une version precise : -Commit "4c0d89a4987..."
    [string] $Commit    = "refs/heads/arena/605e67fb-dlbv1-21-1neoforge",
    [switch] $PasDeSauvegarde
)

$ErrorActionPreference = "Stop"
chcp 65001 > $null 2>&1
$OutputEncoding = [System.Text.Encoding]::UTF8

function Titre($t) {
    Write-Host ""
    Write-Host ("=" * 70) -ForegroundColor DarkCyan
    Write-Host "  $t" -ForegroundColor Cyan
    Write-Host ("=" * 70) -ForegroundColor DarkCyan
}
function Ok($t)    { Write-Host "  [ok]    $t" -ForegroundColor Green }
function Info($t)  { Write-Host "  [info]  $t" -ForegroundColor Gray }
function Alerte($t){ Write-Host "  [!]     $t" -ForegroundColor Yellow }
function Erreur($t){ Write-Host "  [ECHEC] $t" -ForegroundColor Red }

# ---------------------------------------------------------------------
#  Empreinte des sources Java.
#  Definition identique a tools\empreinte_sources.py et au script Gradle :
#  chemins relatifs tries en ordre d'octets ; pour chacun, octets du chemin,
#  saut de ligne, octets du fichier, saut de ligne ; SHA-1 ; 12 hex.
#  Les fichiers sont lus en OCTETS : ni encodage ni fin de ligne n'entrent
#  en jeu, donc la valeur est la meme sous Windows et sous Linux.
# ---------------------------------------------------------------------
function Get-Empreinte([string] $racineJava) {
    if (-not (Test-Path $racineJava)) { return @{ hash = "(absent)"; n = 0 } }
    $base = (Resolve-Path $racineJava).Path.TrimEnd('\')
    $fichiers = Get-ChildItem -Path $base -Filter *.java -Recurse -File
    $liste = @()
    foreach ($f in $fichiers) {
        $rel = $f.FullName.Substring($base.Length + 1).Replace('\', '/')
        $liste += [pscustomobject]@{
            rel  = $rel
            full = $f.FullName
            cle  = [System.Text.Encoding]::UTF8.GetBytes($rel)
        }
    }
    # Tri en ordre d'octets non signes (et non selon la culture Windows).
    $liste = $liste | Sort-Object -Property @{ Expression = {
        ($_.cle | ForEach-Object { $_.ToString("000") }) -join ''
    }}
    $sha = [System.Security.Cryptography.SHA1]::Create()
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
    $h = $sha.ComputeHash($flux)
    $flux.Dispose(); $sha.Dispose()
    $hex = ($h | ForEach-Object { $_.ToString("x2") }) -join ''
    return @{ hash = $hex.Substring(0, 12); n = $liste.Count }
}

Titre "Deep Lucky Block - mise a jour du workspace MCreator"
Info "commit a installer : $Commit"

# ---------------------------------------------------------------------
#  1) Trouver le workspace
# ---------------------------------------------------------------------
Titre "1/6  Localisation du workspace"

if ($Workspace -eq "") {
    $candidats = @(
        (Join-Path $env:USERPROFILE "MCreatorWorkspaces\deep_lucky_block-neoforge-1.21.1"),
        (Join-Path $env:USERPROFILE "MCreatorWorkspaces\deep_lucky_block")
    )
    foreach ($c in $candidats) {
        if (Test-Path (Join-Path $c "src\main\java")) { $Workspace = $c; break }
    }
    if ($Workspace -eq "") {
        $racine = Join-Path $env:USERPROFILE "MCreatorWorkspaces"
        if (Test-Path $racine) {
            $trouve = Get-ChildItem -Path $racine -Directory -ErrorAction SilentlyContinue |
                      Where-Object { Test-Path (Join-Path $_.FullName "src\main\java\deepluckyblock") } |
                      Select-Object -First 1
            if ($trouve) { $Workspace = $trouve.FullName }
        }
    }
}

if ($Workspace -eq "" -or -not (Test-Path (Join-Path $Workspace "src\main\java"))) {
    Erreur "workspace MCreator introuvable."
    Info "Relancez en donnant le chemin, par exemple :"
    Info '  powershell -ExecutionPolicy Bypass -File METTRE-A-JOUR.ps1 -Workspace "C:\Users\...\MCreatorWorkspaces\deep_lucky_block-neoforge-1.21.1"'
    exit 1
}
Ok "workspace : $Workspace"

$avant = Get-Empreinte (Join-Path $Workspace "src\main\java")
Info "empreinte AVANT : $($avant.hash)  ($($avant.n) fichiers)"

# ---------------------------------------------------------------------
#  2) Telecharger l'archive du commit
# ---------------------------------------------------------------------
Titre "2/6  Telechargement depuis GitHub"

$travail = Join-Path $env:TEMP ("dlb_maj_" + (Get-Date -Format "yyyyMMdd_HHmmss"))
New-Item -ItemType Directory -Path $travail -Force | Out-Null
$zip = Join-Path $travail "source.zip"
$url = "https://codeload.github.com/Deepie1/dlbv1.21.1neoforge/zip/$Commit"

Info "source : $url"
try {
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $ProgressPreference = 'SilentlyContinue'
    Invoke-WebRequest -Uri $url -OutFile $zip -UseBasicParsing
} catch {
    Erreur "telechargement impossible : $_"
    Info "Verifiez votre connexion, ou telechargez l'archive a la main depuis"
    Info "  $url"
    Info "puis relancez avec -Workspace et une archive deja extraite."
    exit 1
}
$ko = [math]::Round((Get-Item $zip).Length / 1KB)
Ok "archive recue ($ko Ko)"

Expand-Archive -Path $zip -DestinationPath $travail -Force
$extrait = Get-ChildItem -Path $travail -Directory |
           Where-Object { Test-Path (Join-Path $_.FullName "src\main\java") } |
           Select-Object -First 1
if (-not $extrait) { Erreur "archive inattendue : src\main\java absent."; exit 1 }
Ok "archive extraite"

$refJava = Join-Path $extrait.FullName "src\main\java"
$ref = Get-Empreinte $refJava
Info "empreinte de reference (ce qui doit etre installe) : $($ref.hash)  ($($ref.n) fichiers)"

if ($ref.hash -eq $avant.hash) {
    Titre "DEJA A JOUR"
    Ok "votre workspace contient deja exactement ces sources ($($ref.hash))."
    Alerte "Si le jeu crashe encore, le probleme n'est PAS la version des sources."
    Alerte "Envoyez-moi le journal complet : on cherchera ailleurs."
    Info "menage : $travail"
    Remove-Item -Path $travail -Recurse -Force -ErrorAction SilentlyContinue
    exit 0
}

# ---------------------------------------------------------------------
#  3) Sauvegarde
# ---------------------------------------------------------------------
Titre "3/6  Sauvegarde de l'existant"

if ($PasDeSauvegarde) {
    Alerte "sauvegarde desactivee (-PasDeSauvegarde)"
} else {
    $sauve = Join-Path $Workspace ("src_sauvegarde_" + (Get-Date -Format "yyyyMMdd_HHmmss"))
    Copy-Item -Path (Join-Path $Workspace "src") -Destination $sauve -Recurse -Force
    Ok "ancien src\ copie dans : $sauve"
    Info "(vous pouvez le supprimer quand tout fonctionne)"
}

# ---------------------------------------------------------------------
#  4) Copie
# ---------------------------------------------------------------------
Titre "4/6  Installation des sources"

foreach ($sous in @("src\main\java", "src\main\resources")) {
    $src = Join-Path $extrait.FullName $sous
    $dst = Join-Path $Workspace $sous
    if (-not (Test-Path $src)) { Alerte "$sous absent de l'archive, ignore"; continue }
    if (Test-Path $dst) { Remove-Item -Path $dst -Recurse -Force }
    New-Item -ItemType Directory -Path $dst -Force | Out-Null
    Copy-Item -Path (Join-Path $src "*") -Destination $dst -Recurse -Force
    $n = (Get-ChildItem -Path $dst -Recurse -File).Count
    Ok "$sous installe ($n fichiers)"
}

# ---------------------------------------------------------------------
#  5) Casser l'« UP-TO-DATE »
#     Gradle compare les horodatages. Une copie peut lui sembler plus
#     ancienne que le .class deja produit : il ne recompile alors rien et
#     relance l'ancien binaire. C'est exactement ce qui s'est passe trois
#     fois. On supprime donc les classes compilees du mod.
# ---------------------------------------------------------------------
Titre "5/6  Invalidation des classes compilees"

$cibles = @(
    (Join-Path $Workspace "build\classes\java\main\deepluckyblock"),
    (Join-Path $Workspace "build\resources\main"),
    (Join-Path $Workspace "build\tmp\compileJava")
)
$efface = 0
foreach ($c in $cibles) {
    if (Test-Path $c) { Remove-Item -Path $c -Recurse -Force -ErrorAction SilentlyContinue; $efface++; Ok "supprime : $c" }
}
if ($efface -eq 0) { Info "rien a supprimer (pas encore compile)" }

# Horodatage des sources force a maintenant, ceinture et bretelles.
Get-ChildItem -Path (Join-Path $Workspace "src\main\java") -Filter *.java -Recurse -File |
    ForEach-Object { $_.LastWriteTime = Get-Date }
Ok "horodatage des .java rafraichi"

# ---------------------------------------------------------------------
#  6) Preuve
# ---------------------------------------------------------------------
Titre "6/6  Verification"

$apres = Get-Empreinte (Join-Path $Workspace "src\main\java")
Write-Host ""
Write-Host "    attendu : $($ref.hash)   ($($ref.n) fichiers)" -ForegroundColor White
Write-Host "    obtenu  : $($apres.hash)   ($($apres.n) fichiers)" -ForegroundColor White
Write-Host "    (avant la mise a jour : $($avant.hash))" -ForegroundColor DarkGray
Write-Host ""

Remove-Item -Path $travail -Recurse -Force -ErrorAction SilentlyContinue

if ($apres.hash -ne $ref.hash) {
    Erreur "les sources installees ne correspondent PAS a l'archive."
    Alerte "Causes possibles : un fichier etait verrouille par MCreator ou par"
    Alerte "l'explorateur Windows. Fermez MCreator et relancez ce script."
    exit 1
}

Ok "sources installees et verifiees : $($apres.hash)"
Write-Host ""
Write-Host "  ETAPE SUIVANTE" -ForegroundColor Cyan
Write-Host "    Relancez le jeu depuis MCreator." -ForegroundColor White
Write-Host "    Dans la console, vous devez voir cette ligne passer :" -ForegroundColor White
Write-Host ""
Write-Host "      [DLB-PATCH] empreinte des sources Java : $($apres.hash) ($($apres.n) fichiers)" -ForegroundColor Green
Write-Host ""
Write-Host "    Si l'empreinte affichee par Gradle est differente de celle-la," -ForegroundColor White
Write-Host "    c'est que Gradle lit un AUTRE dossier que celui mis a jour :" -ForegroundColor White
Write-Host "    envoyez-moi la ligne, elle me dira lequel." -ForegroundColor White
Write-Host ""
exit 0
