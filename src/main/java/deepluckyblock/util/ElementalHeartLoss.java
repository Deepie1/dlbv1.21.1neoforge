package deepluckyblock.util;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

/**
 * ElementalHeartLoss — état "cœur(s) perdu(s)" par élément (demande
 * utilisateur, ask #9 : "chaque type de cœur = UNE seule condition de
 * régénération ; un cœur perdu hors condition NE régénère PAS").
 *
 * <p>L'état est stocké directement en NBT sur le PLASTRON (chestplate)
 * porteur des enchantements de cœur, plutôt que dans les PersistentData du
 * joueur : un champ NBT d'ItemStack d'équipement est automatiquement
 * répliqué au client par la synchronisation d'inventaire vanilla — donc
 * {@code ElementalHeartOverlay} (purement client) peut lire le même état
 * que le serveur SANS packet réseau dédié, exactement comme
 * {@code EnchantAura} lit déjà {@code GLINT_NBT_KEY}/{@code
 * GLINT_FORCE_NBT_KEY} sur l'item pour piloter le rendu du glint.
 *
 * <p>Clés NBT (toutes préfixées {@code dlbHeartLoss_}) :
 * <ul>
 *   <li>{@code dlbHeartLoss_<element>} (int) — nombre de cœurs actuellement
 *       "perdus" pour cet élément (0..niveau d'enchant) ;</li>
 *   <li>{@code dlbHeartDmgAcc} (float) — accumulateur de dégâts partagé,
 *       consommé par tranches de 2 PV pour convertir une perte de vie réelle
 *       en perte d'UN cœur élémentaire (le dernier actif dans l'ordre de la
 *       barre) ;</li>
 *   <li>{@code dlbHeartRegenAt_<element>} (long, game time) — prochain
 *       instant où CET élément a le droit de régénérer un cœur (évite un
 *       remplissage instantané dès que la condition est remplie).</li>
 *   <li>{@code dlbHeartCondSince_<element>} (long) — FIX (ask #4, rapporté
 *       en jeu : "je rentre dans l'eau et je regen instantanément un cœur
 *       complet, c'est abusé/pas satisfaisant") : malgré son nom (conservé
 *       pour compatibilité de lecture), ce champ est un ACCUMULATEUR de
 *       ticks tenus en continu pour la condition de régénération de CET
 *       élément — PAS un timestamp. Il n'avance QUE pendant les tick où la
 *       condition est vraie (+10 à chaque cycle de {@code updateElementalHearts},
 *       appelé tous les 10 ticks) et reste GELÉ (jamais réinitialisé à 0) dès
 *       que la condition redevient fausse : quitter brièvement l'eau/la nuit/
 *       etc. met la progression EN PAUSE au lieu de la faire tout perdre —
 *       plus solide et satisfaisant qu'un reset brutal, tout en respectant
 *       "chaque cœur = une seule condition" (aucune progression n'a lieu tant
 *       que la condition n'est pas de nouveau vraie). Avant ce correctif,
 *       {@code getRegenAt} valait 0 par défaut la toute première fois, et
 *       {@code gameTime >= 0} était TOUJOURS vrai : un joueur qui plongeait
 *       dans l'eau déclenchait donc la regen dès le tick suivant (0,5s),
 *       perçu comme "instantané". Le nouveau système exige que l'accumulateur
 *       atteigne {@code HOLD_TICKS_BEFORE_REGEN} avant même le premier stade
 *       de progression, ET introduit une étape intermédiaire "demi-cœur"
 *       (voir {@code getRegenStage}) pour une régénération visuellement
 *       progressive en 2 temps (0→0.5, pause, 0.5→1.0) au lieu d'un
 *       remplissage brutal en un seul coup.</li>
 *   <li>{@code dlbHeartRegenStage_<element>} (int, 0 ou 1) — étape de
 *       régénération EN COURS pour le PROCHAIN cœur à restaurer : 0 = pas
 *       encore commencé, 1 = première moitié déjà accordée (le cœur affiche
 *       un état "demi" pendant la pause avant la seconde moitié). Permet au
 *       client ({@code ElementalHeartOverlay}) de savoir qu'un cœur est en
 *       train de repousser progressivement plutôt que d'un coup.</li>
 * </ul>
 */
public final class ElementalHeartLoss {

    private ElementalHeartLoss() {}

    private static String lostKey(String elementId) { return "dlbHeartLoss_" + elementId; }
    private static String regenAtKey(String elementId) { return "dlbHeartRegenAt_" + elementId; }
    private static String condSinceKey(String elementId) { return "dlbHeartCondSince_" + elementId; }
    private static String regenStageKey(String elementId) { return "dlbHeartRegenStage_" + elementId; }
    private static final String DAMAGE_ACC_KEY = "dlbHeartDmgAcc";

    public static int getLost(ItemStack chest, String elementId) {
        CompoundTag tag = DeepNbt.tag(chest);
        if (tag == null) return 0;
        return Math.max(0, tag.getInt(lostKey(elementId)));
    }

    /** Fixe le nombre de cœurs perdus, borné à [0, maxLevel]. */
    public static void setLost(ItemStack chest, String elementId, int lost, int maxLevel) {
        int clamped = Math.max(0, Math.min(lost, Math.max(0, maxLevel)));
        if (clamped == 0 && DeepNbt.tag(chest) == null) return; // rien à écrire, évite de créer un tag vide
        CompoundTag tag = DeepNbt.getOrCreateTag(chest);
        tag.putInt(lostKey(elementId), clamped);
        // FIX CRITIQUE (voir Javadoc de DeepNbt#commit) : sans ce recommit,
        // Minecraft ne détecte JAMAIS ce changement comme réel (l'objet
        // CompoundTag muté en place reste `==`-identique à tout snapshot
        // déjà pris avant cet appel) et n'envoie donc JAMAIS le nouvel état
        // "perdu" au client -- c'est la cause exacte de "la perte d'un
        // cœur ne joue aucune animation" (le client ne reçoit simplement
        // jamais l'info).
        DeepNbt.commit(chest, tag);
    }

    public static float getDamageAcc(ItemStack chest) {
        CompoundTag tag = DeepNbt.tag(chest);
        return tag == null ? 0f : tag.getFloat(DAMAGE_ACC_KEY);
    }

    public static void setDamageAcc(ItemStack chest, float value) {
        CompoundTag tag = DeepNbt.getOrCreateTag(chest);
        tag.putFloat(DAMAGE_ACC_KEY, Math.max(0f, value));
        DeepNbt.commit(chest, tag);
    }

    public static long getRegenAt(ItemStack chest, String elementId) {
        CompoundTag tag = DeepNbt.tag(chest);
        return tag == null ? 0L : tag.getLong(regenAtKey(elementId));
    }

    public static void setRegenAt(ItemStack chest, String elementId, long gameTime) {
        CompoundTag tag = DeepNbt.getOrCreateTag(chest);
        tag.putLong(regenAtKey(elementId), gameTime);
        DeepNbt.commit(chest, tag);
    }

    /**
     * Nombre de ticks accumulés (condition vraie) pour la progression de
     * régénération EN COURS de {@code elementId}. Gelé (jamais réinitialisé)
     * quand la condition redevient fausse — voir Javadoc de classe.
     */
    public static long getConditionProgressTicks(ItemStack chest, String elementId) {
        CompoundTag tag = DeepNbt.tag(chest);
        return tag == null ? 0L : tag.getLong(condSinceKey(elementId));
    }

    // FIX CRITIQUE (freeze infini rapporte apres /testhearts avec un plastron
    // a 17 enchantements coeur).
    //
    // CAUSE : updateElementalHearts() tourne tous les 10 ticks et, pour CHACUN
    // des 7 elements, appelait inconditionnellement resetConditionProgress()
    // PUIS setRegenStage() -- meme quand la valeur etait deja celle voulue.
    // Or chaque ecriture passe par DeepNbt.commit(), qui reconstruit le
    // composant DataComponents.CUSTOM_DATA de l'ItemStack : CustomData.of()
    // fait une COPIE DEFENSIVE du CompoundTag entier. Avec 17 enchantements
    // le tag est volumineux, et on le recopiait donc jusqu'a 14 fois toutes
    // les 10 ticks, sur le thread serveur. Chaque modification d'ItemStack
    // marque en plus l'inventaire "dirty" -> resynchronisation reseau du
    // plastron a chaque passage. D'ou le blocage.
    //
    // CORRECTIF : ecriture CONDITIONNELLE. On relit la valeur courante et on
    // ne commit QUE si elle change reellement. En regime stable (rien a
    // regenerer, ce qui est le cas la plupart du temps) le nombre d'ecritures
    // tombe a ZERO, et le comportement fonctionnel est strictement identique.
    public static void setConditionProgressTicks(ItemStack chest, String elementId, long ticks) {
        long want = Math.max(0L, ticks);
        CompoundTag cur = DeepNbt.tag(chest);
        if (cur != null && cur.getLong(condSinceKey(elementId)) == want) return; // deja a jour
        CompoundTag tag = DeepNbt.getOrCreateTag(chest);
        tag.putLong(condSinceKey(elementId), want);
        DeepNbt.commit(chest, tag);
    }

    public static void resetConditionProgress(ItemStack chest, String elementId) {
        CompoundTag tag = DeepNbt.tag(chest);
        if (tag == null) return;
        if (tag.getLong(condSinceKey(elementId)) == 0L) return; // deja a zero : aucune ecriture
        tag.putLong(condSinceKey(elementId), 0L);
        DeepNbt.commit(chest, tag);
    }

    /** Étape de régénération en cours (0 = pas commencée, 1 = demi-cœur déjà accordé). */
    public static int getRegenStage(ItemStack chest, String elementId) {
        CompoundTag tag = DeepNbt.tag(chest);
        return tag == null ? 0 : tag.getInt(regenStageKey(elementId));
    }

    /** Ecriture conditionnelle : voir la note de setConditionProgressTicks. */
    public static void setRegenStage(ItemStack chest, String elementId, int stage) {
        CompoundTag cur = DeepNbt.tag(chest);
        if (cur != null && cur.getInt(regenStageKey(elementId)) == stage) return; // deja a jour
        CompoundTag tag = DeepNbt.getOrCreateTag(chest);
        tag.putInt(regenStageKey(elementId), stage);
        DeepNbt.commit(chest, tag);
    }

    /**
     * Niveau EFFECTIF (réellement disponible pour les POUVOIRS ACTIFS, pas
     * seulement le bonus de PV plat) d'un cœur élémentaire, une fois déduits
     * les cœurs actuellement "perdus" (voir {@link #getLost}).
     *
     * <p>Correctif (audit demande utilisateur #3) : avant ce correctif, les
     * pouvoirs actifs des 7 cœurs élémentaires (dégâts bonus, réduction de
     * dégâts, procs, vol de vie, invisibilité, faim/saturation, minage,
     * répulsion des mobs...) lisaient directement {@code getArmorEnchantLvl}
     * / {@code lvl(chest, ...)}, c'est-à-dire le niveau BRUT de l'enchantement
     * gravé sur le plastron -- SANS jamais consulter {@code ElementalHeartLoss}.
     * Résultat : un joueur ayant perdu tous ses cœurs de Foudre (ou de tout
     * autre élément) via des dégâts réels continuait de bénéficier de 100 %
     * du pouvoir actif de Foudre tant que le plastron restait équipé -- seul
     * le bonus de PV plat (+2 PV/cœur) était réduit. Cette méthode centralise
     * le calcul correct : {@code effectif = max(0, brut - perdus)}, et TOUT
     * pouvoir actif d'un cœur doit désormais passer par elle plutôt que par
     * le niveau brut, pour que "perdre un cœur" désactive proportionnellement
     * le pouvoir jusqu'à régénération (voir ElementalHeartConditions).
     */
    public static int effectiveLevel(ItemStack chest, String elementId, int rawLevel) {
        if (rawLevel <= 0 || chest == null || chest.isEmpty()) return 0;
        int lost = getLost(chest, elementId);
        return Math.max(0, rawLevel - lost);
    }
}
