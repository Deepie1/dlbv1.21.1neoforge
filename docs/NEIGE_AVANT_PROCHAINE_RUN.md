# Neige — contrôle avant la prochaine vérification en jeu

> **Statut : livré, à vérifier en jeu.** Les bancs locaux ne prouvent pas le rendu visible. La compilation Java locale n'a pas pu tourner ici (aucun Java/JAVA_HOME); attendre la CI du SHA livré, qui compile et démarre le serveur sans valider l'apparence de la neige.

## Mise à jour du workspace

1. Fermer MCreator, sauvegarder le workspace et faire une copie du monde de repro.
2. Télécharger `deliverables/METTRE-A-JOUR.ps1` depuis le lien épinglé au SHA livré.
3. Lancer PowerShell :

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\METTRE-A-JOUR.ps1
   ```

   Ajouter `-Workspace "D:\chemin\du\workspace"` si l'auto-détection ne trouve pas MCreator.
4. Vérifier le message de fin et la valeur d'empreinte indiqués dans la livraison. Ne pas lancer le jeu si la copie échoue ou si l'empreinte diffère.
5. Rouvrir MCreator et vérifier que le journal Gradle affiche exactement la ligne d'empreinte attendue. Utiliser une copie du monde, pas le monde de référence.

## Test en jeu et scan

1. Se placer près de la zone repro et armer le scanner avant la pose : `/dlbscan`.
2. Lancer le même test qu'au repro (`/dlbtest crimsonlake`), laisser toutes les phases finir et attendre le rapport `4_FIN`.
3. Dans le rapport final, comparer :
   - `9f. TAUX BRUT DU SPREAD DE NEIGE PAR COLONNE` : potentiel continu (milli-unités) interpolé à chaque coordonnée de colonne;
   - `9g. COUVERTURE DE NEIGE OBSERVEE PAR COLONNE` : neige réellement visible au sommet de chaque colonne, `1000` ou `0000`, avec absences clairement marquées.
4. Garder le dossier brut du scan, le journal et des captures d'écran des mêmes coordonnées. Ne pas confondre une colonne `----` (chunk non chargé) avec une colonne sèche; un `????` indique un profil non disponible.
5. Pour examiner les rapports :

   ```powershell
   python tools\scanview.py "CHEMIN\VERS\dlb_scan\scan-HORODATAGE" -o "CHEMIN\VERS\neige.html"
   ```

## Diagnostic des rapports précédents (avant cette refonte)

Le dernier rapport couvre l'emprise x=-64..319, z=112..527 et un scan x=-112..367, z=64..575 : le scan va exactement 48 blocs (3 chunks) au-delà de l'emprise sur les quatre côtés. Géométriquement, le halo nominal couvre donc toute la fenêtre observée. En revanche, la section 9f précédente ne contenait **aucune valeur numérique** : au début, 176 896 cellules étaient `????` et 68 864 `----`; à la fin, 216 064 étaient `????` et 29 696 `----` sur 245 760. Elle ne permet pas d'attribuer la neige manquante au spread, et les chunks absents ne sont pas des colonnes sèches. La marge de trois chunks ne prouve pas non plus l'état au-delà de la fenêtre; le domain-warp peut déplacer un échantillon d'environ dix blocs.

## Critère de conclusion

La simulation, le scan et la CI valident des propriétés du code; **ils ne prouvent pas l'aspect en jeu**. La neige ne sera déclarée corrigée en jeu qu'après un run réel montrant un dégradé sans damier, les franges attendues et zéro neige sur l'eau/glace. Si les valeurs manquent ou si l'aspect reste discontinu, transmettre le rapport complet, les captures et les coordonnées du repro.
