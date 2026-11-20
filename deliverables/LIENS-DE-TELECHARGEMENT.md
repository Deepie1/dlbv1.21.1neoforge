# Telechargement

## ⬇️ L'archive — c'est le seul lien

https://github.com/Deepie1/dlbv1.21.1neoforge/raw/5ea96837f3d292a00a8fcd6f8b5a6ac50199c77c/deliverables/DLB_src.zip

`DLB_src.zip` · 118 fichiers · 2 161 240 octets
<br>SHA-256 `93e4f8394c0abcf96105c0b7f76926aeb157d6127f9de6af6bfdbe53a61132f6`

Le fichier `deep_lucky_block-neoforge-1.21.1.mcreator` est dedans, à la racine.

---

## Votre question : non, ce n'était pas le cas

Les cubes **existaient bien** — `renderVoidPortalCubes` (T392) et `renderChaosPixelOrbit`
(T401) — mais leur condition d'affichage n'avait **aucun rapport** avec une compétence :

1. l'arme devait avoir l'**aura**, tirée à 15 % au loot ;
2. une **couche visuelle** devait être d'élément Vide ou Chaos.

Et surtout : **aucun code client ne lisait l'état de recharge.** Vérifié au `grep` —
`isCharged`, `readyAt` et la clé de halo n'apparaissaient **nulle part** sous `client/`.
Conséquence concrète : les cubes tournaient autour d'armes qui n'avaient aucune compétence,
et ils restaient affichés pendant toute la recharge.

### Le comportement, avant et après

| L'arme | Avant | Après |
|---|---|---|
| Aura + enchantement Vide, **aucune compétence** | cubes | rien |
| Compétence Vide **prête**, arme à aura | cubes | cubes |
| Compétence Vide **prête**, arme **sans** aura | rien | **cubes** |
| Compétence Vide **en recharge** | cubes | rien |
| Arme ordinaire | rien | rien |

### Ce qui pilote les cubes maintenant

Une seule méthode répond à la question — `hasChargedSkillOfElement(arme, élément, temps)` —
pour que **les cubes et le halo s'allument et s'éteignent ensemble**. Elle part de `allOn()`,
exactement la même source que l'encart COMPÉTENCES : les trois affichages ne peuvent plus se
contredire.

Les **pixels de lame**, eux, gardent leur règle de minorité posée au loot : une arme sans
aura reste à lame nue, mais sort quand même ses cubes si sa compétence est prête.

**10 compétences déclenchent les cubes** — Vide : `warp`, `dragons_breath_arrow`,
`null_rift`, `horizon_gate`, `chain_collapse` · Chaos : `chaos_entropy`, `chaos_nexus`,
`chaos_rupture`, `feigenbaum_cascade`, `sandpile_critical`.

Un point qui compte : cette lecture n'est fiable **que grâce au correctif précédent**. Avant
lui, l'état de recharge n'arrivait jamais à jour côté client — les cubes auraient été aussi
muets que le halo.

## Toujours inclus

Les règles de la livraison précédente : skill d'élément aléatoire à 5 % (puis 5 %), troc
0-3, slash visuel 7 % au drop / 6 % au coup / +10 % de dégâts.

## Ce que je n'ai pas pu vérifier

`javac` n'existe pas ici : **la compilation n'est pas prouvée**. Je ne peux pas lancer le
jeu, donc l'apparition réelle des cubes à l'écran reste à confirmer par vous — c'est
précisément le genre de chose qu'aucun banc ne sait regarder.
