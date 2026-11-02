package deepluckyblock.util;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

import javax.annotation.Nullable;

/**
 * DeepNbt — équivalents 1.21.1 des méthodes NBT legacy d'ItemStack supprimées.
 *
 * En 1.20.5+ la "tag" d'un ItemStack est le composant de données
 * DataComponents.CUSTOM_DATA ({@link CustomData}). Les méthodes supprimées :
 *   - stack.getTag()                    -> DeepNbt.tag(stack)
 *   - stack.hasTag()                    -> DeepNbt.hasTag(stack)
 *   - stack.setTag(tag)                 -> DeepNbt.setTag(stack, tag)   (null = suppression)
 *   - stack.getOrCreateTag()            -> DeepNbt.getOrCreateTag(stack)
 *   - stack.getOrCreateTagElement(n)    -> DeepNbt.getOrCreateTagElement(stack, n)
 *
 * Les valeurs renvoyées sont les instances VIVANTES du composant : les
 * modifications s'appliquent directement à l'item (comme en 1.20.1).
 */
public final class DeepNbt {

    private DeepNbt() {}

    /** Équivalent 1.20.1 de stack.getTag() — la compound vivante, ou null. */
    @Nullable
    public static CompoundTag tag(@Nullable ItemStack stack) {
        if (stack == null) return null;
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd == null || cd.isEmpty()) return null;
        return cd.getUnsafe();
    }

    /** Équivalent 1.20.1 de stack.hasTag(). */
    public static boolean hasTag(@Nullable ItemStack stack) {
        return tag(stack) != null;
    }

    /** Équivalent 1.20.1 de stack.setTag(tag) — null supprime le composant. */
    public static void setTag(ItemStack stack, @Nullable CompoundTag tag) {
        if (tag == null || tag.isEmpty()) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            stack.set(DataComponents.CUSTOM_DATA, CustomData.of(tag));
        }
    }

    /**
     * Équivalent 1.20.1 de stack.getOrCreateTag() — crée le composant si absent.
     *
     * FIX CRITIQUE : CustomData.of(tag) fait une COPIE défensive du compound
     * (tag.copy()) au moment ou il est stocke sur l'item. Si on renvoyait ici
     * l'objet local "tag" fraichement cree, toute mutation faite par l'appelant
     * APRES ce retour (ex: .putInt(...) sans rappeler setTag() ensuite) ne
     * touchait que cet objet local jetable, jamais la copie reellement stockee
     * dans le composant de l'item -> perte SILENCIEUSE de donnees (luck +100,
     * glint custom, drapeaux divers...) sur tout item fraichement cree.
     * On committe donc un tag vide, PUIS on relit la reference VIVANTE stockee
     * dans le composant (CustomData.getUnsafe() sur l'instance deja en place) :
     * toute mutation ulterieure sur l'objet retourne est alors bien visible.
     */
    public static CompoundTag getOrCreateTag(ItemStack stack) {
        CompoundTag tag = tag(stack);
        if (tag != null) return tag;
        // Le composant est absent OU present mais vide (cd.isEmpty()).
        // On (re)commite explicitement un CompoundTag vide sur le stack,
        // PUIS on relit directement le composant (sans le filtre isEmpty()
        // de tag(stack)) pour recuperer la reference VIVANTE fraichement
        // stockee sur l'item : toute mutation ulterieure sur l'objet
        // retourne est alors bien visible depuis le stack.
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag()));
        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        return cd.getUnsafe();
    }

    /** Équivalent 1.20.1 de stack.getOrCreateTagElement(name). */
    public static CompoundTag getOrCreateTagElement(ItemStack stack, String name) {
        CompoundTag tag = getOrCreateTag(stack);
        // PORT 1.21.1 : plus de getOrCreateCompound.
        if (!tag.contains(name, Tag.TAG_COMPOUND)) tag.put(name, new CompoundTag());
        return tag.getCompound(name);
    }

    /**
     * FIX CRITIQUE (rapporté en jeu : "perdre un cœur élémentaire ne joue
     * JAMAIS d'animation, la régénération semble fonctionner mais avec un
     * bug, et les derniers cœurs vanilla de la barre de vie prennent
     * brièvement l'apparence d'un cœur custom") : décompilation du
     * bytecode vanilla ({@code ItemStack}, {@code PatchedDataComponentMap},
     * {@code LivingEntity}) a révélé la cause exacte.
     *
     * <p>{@link #getOrCreateTag} renvoie la référence VIVANTE du
     * {@code CompoundTag} interne au composant {@code CUSTOM_DATA}. Tout
     * code qui mute cette référence via {@code tag.putXxx(...)} SANS
     * jamais rappeler {@code stack.set(DataComponents.CUSTOM_DATA, ...)}
     * modifie l'objet EN PLACE. Or {@code ItemStack.copy()} (utilisé par
     * Minecraft pour ses "snapshots avant/après" lors de la détection de
     * changement d'équipement -- {@code LivingEntity.collectEquipmentChanges()}
     * -- et par {@code AbstractContainerMenu.broadcastChanges()} pour la
     * sync d'inventaire) ne clone PAS en profondeur ce composant : le
     * {@code PatchedDataComponentMap} copié partage la MÊME instance
     * {@code Optional<CustomData>} (copy-on-write ne clone que la
     * structure de la map de composants, jamais leurs valeurs). Résultat :
     * le "snapshot d'avant" et "l'état d'après" que Minecraft compare pour
     * décider s'il doit envoyer un paquet réseau pointent vers le MÊME
     * objet muté -- {@code ItemStack.matches(old, new)} reste TOUJOURS
     * vrai, Minecraft croit que rien n'a changé et n'envoie donc JAMAIS le
     * nouveau NBT au client.
     *
     * <p>Cette méthode DOIT être appelée après CHAQUE mutation d'un
     * {@code CompoundTag} obtenu via {@link #getOrCreateTag}/{@link
     * #getOrCreateTagElement} dont le changement doit être visible côté
     * client (typiquement tout état lu par du code purement client, comme
     * {@code ElementalHeartOverlay} lisant {@code ElementalHeartLoss}) :
     * elle force un VRAI remplacement du composant via
     * {@link #setTag}, qui appelle {@code CustomData.of(tag)} --
     * celui-ci fait un {@code tag.copy()} DÉFENSIF, créant un tout nouvel
     * objet {@code CompoundTag} totalement indépendant de tout snapshot
     * déjà pris. Le prochain {@code ItemStack.copy()} capturera alors
     * cette nouvelle instance, garantissant que la comparaison structurelle
     * suivante détecte correctement tout changement réel de contenu.
     */
    public static void commit(ItemStack stack, CompoundTag tag) {
        setTag(stack, tag);
    }

    /**
     * T331-LUCKY-ITEM-ORIGIN : Clé NBT (dans {@code DataComponents.CUSTOM_DATA}) marquant
     * tout item looté via le Lucky Block (blocs, coffres de structures, boss, événements)
     * ou généré/enchanté via une commande du mod.
     */
    public static final String LUCKY_ITEM_NBT_KEY = "dlbLuckyItem";

    /**
     * T331-LUCKY-ITEM-ORIGIN : Estampille l'item comme issu du Lucky Block ou d'une commande
     * du mod, puis force la synchronisation réseau via {@link #commit}.
     */
    public static void markLuckyBlockOrigin(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return;
        try {
            CompoundTag tag = getOrCreateTag(stack);
            boolean changed = false;
            if (!tag.getBoolean(LUCKY_ITEM_NBT_KEY)) {
                tag.putBoolean(LUCKY_ITEM_NBT_KEY, true);
                changed = true;
            }
            // T337-ARMOR-GLINT-PLUS2 : +2 paliers de glint par défaut sur les armures issues du bloc (Palier 6/20)
            if (deepluckyblock.client.EnchantAura.isArmorPiece(stack)
                    && !tag.contains(deepluckyblock.client.EnchantAura.GLINT_TIER_NBT_KEY, 99)) {
                tag.putInt(deepluckyblock.client.EnchantAura.GLINT_TIER_NBT_KEY,
                        deepluckyblock.client.EnchantAura.LUCKY_ARMOR_DEFAULT_GLINT_TIER);
                changed = true;
            }
            if (changed) {
                commit(stack, tag);
            }
        } catch (Throwable ignored) {
        }
    }

    /**
     * T333-GRADED-ORIGIN-ONLY : Retourne {@code true} UNIQUEMENT si l'item est une pièce
     * d'équipement gradée issue du Deep Lucky Block (ou d'un kit gradé {@code /dlbcombo give}).
     *
     * <p>Un item enchanté à la main (enclume, livre enchanté, table d'enchantement, {@code /enchant},
     * essence d'ombre sur un item non gradé ou autre mod) retourne strictement {@code false} :
     * l'interface spéciale du mod n'est jamais affichée hors des items gradés du Lucky Block.
     */
    public static boolean isLuckyBlockOrigin(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        if (stack.is(net.minecraft.world.item.Items.ENCHANTED_BOOK)) return false;
        CompoundTag tag = tag(stack);
        if (tag == null || !tag.getBoolean(LUCKY_ITEM_NBT_KEY)) {
            return false;
        }
        return stack.has(DataComponents.CUSTOM_NAME) || tag.contains("dlbGlint");
    }
}
