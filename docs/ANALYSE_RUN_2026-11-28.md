# Analyse du run MCreator du 28 novembre 2026

## Confirmé par les fichiers fournis

- L'empreinte exécutée est `10c9a014bb7c` (226 fichiers Java) et le journal de démarrage fourni confirme `:compileJava`, `:classes` et `:runClient` réussis.
- Le scan Crimson Lake couvre `480 x 496` colonnes. À `4_FIN`, il a lu `207 104` colonnes et en a laissé `30 976` non mesurables parce que leurs chunks n'étaient pas chargés. L'ancienne exception d'index ne réapparaît pas.
- Le scan annonce explicitement que le relief théorique est arrêté avant rédaction; les sections théoriques sont donc vides. Les `18 634 ms` cités ailleurs concernent un autre scan `112 x 112`, et ne prouvent pas que la grille Crimson Lake ait été calculée.
- Comparaison directe de la carte 9d `EAU` entre `05_3_APRES_PASTE` et `06_4_FIN` : les `89 041` colonnes de fluide présentes dans les deux mesures gardent toutes exactement le même Y de surface. Le rapport final observe `8 155` colonnes de fluide supplémentaires, parce que davantage de chunks sont mesurables à la fin. Ces mesures ne montrent donc pas de remontée générale de la surface de l'eau causée pendant la finition. Elles ne permettent pas d'identifier une cellule isolée, une source ajoutée dans un chunk précédemment absent, ni la hauteur de l'eau visible sur la capture.
- La carte observée 9g (`snow` dans les 48 blocs autour de la surface, canopée comprise) contient `61 758` colonnes enneigées sur `207 104` mesurées à `4_FIN` (29,8 %). À `3_APRES_PASTE`, elle en contient `50 386` sur `193 536` mesurées (26,0 %). Parmi les `193 536` colonnes comparables, `42 333` passent de sans couche détectée à couche détectée et `35 615` font l'inverse. Le solde positif n'identifie pas la passe responsable : le scan voit toute neige dans sa fenêtre, y compris celle de la canopée, et ne distingue pas neige vanilla, spread, couches replacées ou chargement tardif.
- Les rapports TXT sont des observations passives. Ils ne contiennent pas les compteurs d'exécution de `dressAndPlant`, de peinture ou de replantation; ils ne valident donc pas à eux seuls la paint, la végétation ou les arbres. La capture montre bien un relief enneigé et une bande sombre à la surface de l'eau, mais elle ne donne ni coordonnées ni Y permettant de confirmer les « 7 blocs ».

## Correction de sûreté du fixwater

Le chemin Crimson Lake contourne `prepZone`; il n'avait donc pas la photographie `NATURAL_WATER` normalement utilisée par `StructureTerrainPrep`. Dans `fillSlice`, une référence absente signifiait historiquement que toute poche d'air atteignable était admissible. C'était un chemin réel de création d'eau non bornée par une carte de nappe, même si les cartes de surface disponibles ne montrent pas une remontée générale.

Le correcteur est maintenant appelé après la pose et avant fixwater pour photographier les colonnes où l'eau existe déjà. Les blocs d'air ne sont remplis que si cette référence existe et que la colonne est autorisée; sans référence, le correcteur peut encore stabiliser l'eau courante, mais ne crée pas de nouvelles sources. Le journal de fin précise maintenant combien de blocs d'air ont été convertis, combien d'eaux courantes promues et l'intervalle Y réellement édité. Cette barrière réduit le risque d'inondation artificielle; elle ne constitue pas encore une validation en jeu de l'écart de 7 blocs signalé.

## Neige, paint et arbres : statut

- Le champ de spread et le peintre restent dans `StructureTerrainPrep`; le TXT reste découplé et ne consomme pas le champ théorique.
- Pour le run joint, le pipeline Crimson Lake personnalisé n'a pas démarré le contexte `TerrainMother` de `prepZone`; il retombait donc au fallback biome dans `snowCoverHere`. La couverture observée ne prouve pas le spread continu piloté par les sources de `TerrainMother` pour ce run.
- Le correctif courant initialise désormais un contexte autonome de `TerrainMother` avant le relevé pré-clear du talus. Les chunks relevés alimentent les sources neige/poids terre dans `StructureTerrainPrep`, puis le contexte est fermé après la finition. Au prochain run, vérifier la ligne `[DLB-SNOW] contexte autonome...` et les diagnostics `[DLB-SNOW] champ TerrainPrep...`; ceci améliore le chemin, mais ne transforme pas le rapport du 28 novembre en validation rétroactive.
- La peinture est bien atteignable par `dressAndPlant`, mais aucun compteur paint de son journal d'exécution n'est inclus dans les fichiers joints. Son succès visuel reste à confirmer par les logs dédiés (`dressAndPlant ... appels paint / ... blocs de sous-sol peints`).
- Le rapport de scan ne contient pas de preuve permettant d'évaluer la replantation des arbres; le compteur `[DLB-LAKE] ... arbres releves avant creusement` et les résultats du callback de replantation sont nécessaires.

## À relever au prochain run

1. Garder le journal MCreator complet, notamment la ligne finale `fixWaterNearStructure` avec les Y édités, le compteur d'air converti et les eaux promues.
2. Pour quantifier le décalage de niveau, relever les coordonnées X/Z et le Y de la source incriminée avant/après fixwater, plus le Y de surface dans la même colonne avant le chantier. Une capture seule ne donne pas ce repère.
3. Conserver les compteurs `dressAndPlant` (appels paint/blocs peints/plantes/colonnes sautées), les lignes `[DLB-SNOW]` et le nombre d'arbres capturés/replantés.
4. Laisser le calcul théorique terminer ou préserver son délai indépendant de la rédaction TXT; ne pas inclure le spread théorique dans le scanner passif.
