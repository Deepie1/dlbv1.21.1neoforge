# TEST2 — diagnostic terrain et neige avant le prochain run

> **Statut : correctif préparé, pas validé en jeu.** Les sources locales ne remplacent pas le rapport du nouveau run ni l'observation visuelle. Le dossier `scan-20261127-195953` n'est pas présent dans ce dépôt ni dans Google Drive connecté; il faut encore récupérer le dossier brut du jeu.

## Cause démontrée pour le sol peint sur une seule couche

Crimson Lake contourne `prepZone` et ne publiait donc jamais `PASTED_OCCUPIED`/les colonnes de base du NBT. Pourtant, sa finition appelait `dressAndRevegetate(..., postPaste=true)`. Dans `dressAndPlant`, une emprise protégée avec `PASTED_OCCUPIED` vide bascule en `vegetationOnly`; le choix de palette et la peinture de profondeur sont alors sautés. C'est une différence de chemin d'exécution propre au lac, pas un problème de graine ou de variation aléatoire.

Le changement collecte maintenant les positions non-air et le Y de base par colonne au fil des blocs effectivement posés, publie ce contexte avant `fixWater` et l'habillage, puis l'efface à la fin. Les écritures de sol continuent de refuser les positions appartenant à la structure, les minerais, l'eau et la glace. Un contrôle statique ciblé vérifie ce chemin; cela ne prouve pas le résultat visuel.

## Scanner: observations à récupérer

La version du scanner préparée pour le prochain run ajoute, à chaque étape, deux grilles pleine résolution dans chaque fichier d'étape :

- `9f` — potentiel continu neige par colonne, lu depuis le snapshot du champ utilisé;
- `9g` — neige réellement observée au sommet de chaque colonne : `1000` pour neige/neige compacte/neige poudreuse, `0000` pour autre surface, `????` si la colonne n'a pas de mesure, `----` si le chunk était absent.

Les étapes avant/après la pose permettent de voir quelles colonnes étaient réellement couvertes, où la neige a disparu et si le champ du jeu correspond à l'observation. Les marqueurs d'absence ne doivent jamais être lus comme des colonnes sèches. Le scan qui s'est arrêté au calcul théorique du relief ne suffit pas à établir le profil de neige, et ses fichiers n'ont pas été fournis ici.

Pour le prochain test : armer `/dlbscan` avant `/dlbtest crimsonlake`, laisser finir le pipeline et la capture `4_FIN`, puis conserver le dossier complet `dlb_scan/scan-<horodatage>`, le journal MCreator et des captures d'écran avec coordonnées. Transmettre au minimum `00_SOMMAIRE.txt` et tous les fichiers d'étape. La visionneuse peut être générée ainsi :

```powershell
python tools\scanview.py "CHEMIN\VERS\dlb_scan\scan-HORODATAGE" -o "CHEMIN\VERS\neige.html"
```

## Installation contrôlée

1. Fermer MCreator, sauvegarder le workspace et copier le monde de test.
2. Utiliser `deliverables/METTRE-A-JOUR.ps1` du commit de livraison; il télécharge le colis immuable et vérifie son empreinte. Ne pas lancer le jeu si la copie échoue ou si l'empreinte diffère.
3. Empreinte attendue pour le colis courant : `e78426265918` (226 fichiers Java). Le journal Gradle doit afficher exactement la même empreinte.
4. Rouvrir MCreator, attendre la fin du build, puis refaire le test ci-dessus.

## Critère de conclusion

Le correctif de peinture ne sera considéré validé qu'après constat en jeu de plusieurs couches (surface puis dirt, profondeur variable 1 à 4) tout en laissant intacte la structure. La neige ne sera considérée corrigée qu'après comparaison du 9f au 9g sur toutes les zones et confirmation visuelle d'un dégradé continu, avec eau/glace sans neige. Ni les bancs statiques ni la compilation ne remplacent cette validation.
