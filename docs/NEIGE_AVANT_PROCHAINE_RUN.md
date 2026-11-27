# TEST2 — diagnostic terrain et neige avant le prochain run

> **Statut : correctif préparé, pas validé en jeu.** Le dossier `scan-20261127-195953` n'est pas présent dans ce dépôt ni dans Google Drive connecté. Le run signale explicitement zéro dégradé et une seule couche peinte; il faut encore récupérer les rapports bruts pour caractériser les colonnes réellement enneigées et sèches.

## Cause démontrée pour le sol peint sur une seule couche

Crimson Lake contourne `prepZone` et ne publiait donc jamais `PASTED_OCCUPIED` ni les colonnes de base du NBT. Pourtant, sa finition appelait `dressAndRevegetate(..., postPaste=true)`. Dans `dressAndPlant`, une emprise protégée avec `PASTED_OCCUPIED` vide bascule en `vegetationOnly`; le choix de palette et la peinture de profondeur sont alors sautés. C'est une différence de chemin d'exécution propre au lac, pas un problème de graine ou de variation aléatoire.

Le changement collecte maintenant les positions non-air et le Y de base par colonne au fil des blocs effectivement posés, publie ce contexte avant `fixWater` et l'habillage, puis l'efface à la fin. Les écritures de sol continuent de refuser les positions appartenant à la structure, les minerais, l'eau et la glace. Un contrôle statique ciblé vérifie ce chemin; cela ne prouve pas le résultat visuel.

## Mesures disponibles et limites

Le dernier rapport pré-refonte couvrait l'emprise x=-64..319, z=112..527 et un scan x=-112..367, z=64..575 : 48 blocs (3 chunks) de halo sur chaque bord. À la capture initiale, la section neige avait 176 896 cellules `????` et 68 864 `----`; à la fin, 216 064 `????` et 29 696 `----`, sur 245 760. Elle ne contenait donc **aucune valeur numérique de potentiel**. Les chunks absents ne sont pas des colonnes sèches; ces données ne permettent pas de reconstruire un gradient ni de savoir quelle zone avait réellement une couche de neige.

La version préparée du scanner ajoute à chaque étape deux grilles pleine résolution dans le fichier d'étape; la neige est sondée jusqu'à 48 blocs sous le haut de la surface pour inclure le sol sous les arbres, mais pas les cavernes profondes :

- `9f` — potentiel continu neige par colonne, lu depuis le snapshot du champ utilisé;
- `9g` — vraie couche `SNOW` observée dans les 48 blocs autour de la surface de chaque colonne (canopee comprise) : `1000` si trouvée, `0000` sinon, `????` si non mesurée, `----` si le chunk était absent. Les cavernes profondes sont exclues.

Les étapes avant/après la pose permettront de voir où la neige existait réellement, où elle a disparu et si le champ utilisé suit ces observations. Le domaine de trois chunks est la fenêtre scannée; il ne prouve pas l'état au-delà, notamment quand le domain-warp déplace un échantillon.

## Nouveau test à transmettre

Armer `/dlbscan` avant `/dlbtest crimsonlake`, laisser finir le pipeline et la capture `4_FIN`, puis conserver le dossier complet `dlb_scan/scan-<horodatage>`, le journal MCreator et des captures d'écran avec coordonnées. Transmettre au minimum `00_SOMMAIRE.txt` et tous les fichiers d'étape. Pour produire une vue :

```powershell
python tools\scanview.py "CHEMIN\VERS\dlb_scan\scan-HORODATAGE" -o "CHEMIN\VERS\neige.html"
```

## Installation contrôlée

1. Fermer MCreator, sauvegarder le workspace et copier le monde de test.
2. Utiliser `deliverables/METTRE-A-JOUR.ps1` du commit de livraison; il télécharge le colis immuable et vérifie son empreinte. Ne pas lancer le jeu si la copie échoue ou si l'empreinte diffère.
3. Empreinte attendue pour le colis courant : `0bf4e93a3c86` (226 fichiers Java). Le journal Gradle doit afficher exactement la même empreinte.
4. Rouvrir MCreator, attendre la fin du build, puis refaire le test ci-dessus.

## Critère de conclusion

Le correctif de peinture ne sera considéré validé qu'après constat en jeu de plusieurs couches (surface puis dirt, profondeur variable 1 à 4) tout en laissant intacte la structure. La neige ne sera considérée corrigée qu'après comparaison de 9f à 9g sur toutes les zones et confirmation visuelle d'un dégradé continu, avec eau/glace sans neige. Ni les bancs statiques ni la compilation ne remplacent cette validation.
