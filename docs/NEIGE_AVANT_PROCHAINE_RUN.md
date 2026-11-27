# Neige — checklist avant le prochain run

> **État de preuve :** les contrôles Python du visualisateur et du scan passent. Le Java n'a pas été compilé ici et Minecraft n'a pas été lancé. Attendre la CI verte sur le commit livré avant de mettre à jour le workspace et de lancer le test. Cette CI compile et démarre le serveur, mais ne prouve pas le rendu neige en jeu.

## 1. Préparer le poste, sans toucher au monde de référence

1. Fermer MCreator et faire une copie de sauvegarde du workspace et du monde de repro.
2. Télécharger `METTRE-A-JOUR.ps1` depuis le lien de livraison. Le script conserve une sauvegarde des fichiers Java remplacés et télécharge le paquet complet `DLB_sync.zip` depuis cette branche.
3. Ouvrir PowerShell dans le dossier de téléchargement et lancer :

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\METTRE-A-JOUR.ps1
   ```

   Si le workspace n'est pas détecté automatiquement :

   ```powershell
   powershell -NoProfile -ExecutionPolicy Bypass -File .\METTRE-A-JOUR.ps1 -Workspace "D:\chemin\du\workspace"
   ```

4. Laisser le script finir. Il doit annoncer la copie réussie et afficher l'empreinte attendue `467e34de1b29` pour **226 fichiers Java**. En cas d'erreur, ne pas lancer le jeu : conserver `DLB_mise_a_jour.txt` et le transmettre.
5. Vérifier que le script de réparation Gradle a bien été installé dans `%USERPROFILE%\.mcreator\gradle\init.d\deep-lucky-block-patch.gradle`. Rouvrir MCreator et vérifier dans la console que `[DLB-PATCH] empreinte des sources Java` apparaît avec la même empreinte. Si la ligne manque ou diffère, arrêter ici.

## 2. Lancer le test contrôlé

1. Utiliser une copie du monde de repro, pas le monde de référence. Se placer près de la zone où le défaut de neige a été observé.
2. Ouvrir le monde avec les permissions opérateur nécessaires. Armer le scan **avant** le test :

   ```text
   /dlbscan
   ```

3. Lancer le même test de structure que pour le repro :

   ```text
   /dlbtest crimsonlake
   ```

4. Laisser le pipeline terminer complètement et attendre le message de fin/rédaction du scan. Le scanner écrit un dossier `dlb_scan/scan-<horodatage>` dans le dossier du monde, avec un rapport composite par étape. Ne pas conclure à partir d'une étape intermédiaire seulement.
5. Vérifier que le rapport final contient `9f. TAUX BRUT DU SPREAD DE NEIGE PAR COLONNE`. Les valeurs sont des milli-unités du potentiel directionnel brut, répétées sur les colonnes du chunk : `105` = `0.105`, `210` = `0.210`, etc. `NEUT` = cellule neutre (eau/glace/poids faible), `????` = profil absent, `----` = chunk non chargé. Ce n'est **pas** un pourcentage de blocs enneigés ni la décision finale après écotone et tirage.
6. Pour le rendu, depuis la racine du dépôt ou avec une copie de `scanview.py` et Python 3 :

   ```powershell
   python tools\scanview.py "CHEMIN\VERS\dlb_scan\scan-HORODATAGE" -o "CHEMIN\VERS\neige.html"
   ```

   Ouvrir `neige.html`, choisir le calque `snow`, puis inspecter les étapes. Conserver le dossier brut complet, le HTML, les captures d'écran et le journal du jeu. Envoyer ces éléments avec les coordonnées du repro et le résultat attendu/observé.

## 3. Critère de conclusion

- Si le profil 9f est cohérent mais que la neige visible ne l'est pas, le défaut est après ce potentiel brut : le rapport seul ne suffit pas à déclarer la correction validée.
- Si le profil manque, est `????` dans une zone censée être connue, ou ne suit pas les sources voisines attendues, conserver le rapport et arrêter : ce sont des indices à diagnostiquer, pas une preuve de correction.
- La neige ne sera déclarée **corrigée en jeu** qu'après confirmation du développeur sur le run réel et examen du rapport correspondant.
