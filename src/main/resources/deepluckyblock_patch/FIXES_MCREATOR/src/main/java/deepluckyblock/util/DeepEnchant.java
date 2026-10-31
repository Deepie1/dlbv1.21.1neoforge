package deepluckyblock.util;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * DeepEnchant — pont d'API pour 1.21.1 (version 2, API vérifiée sur la
 * javadoc NeoForge 21.1.x).
 *
 * ⚠️ Rupture 1.20.5+ : les enchantements ne sont PLUS des champs d'ItemStack.
 * Ils vivent dans le composant de données DataComponents.ENCHANTMENTS dont le
 * type est {@link ItemEnchantments} (pas un Map). Les méthodes supprimées :
 *   - stack.enchant(en, lvl)            -> DeepEnchant.setLevel(stack, en, lvl)
 *   - stack.getEnchantmentLevel(en)     -> DeepEnchant.getLevel(stack, en)
 *   - stack.removeEnchantment(en)       -> DeepEnchant.remove(stack, en)
 *   - stack.getEnchantmentLevels()      -> DeepEnchant.map(stack) / forEach
 *   - EnchantmentHelper.getEnchantments(stack)  -> DeepEnchant.map(stack)
 *   - EnchantmentHelper.setEnchantments(map, stack) -> DeepEnchant.setLevel x N
 *
 * Points d'appui 1.21.1 (javadoc vérifiée) :
 *   - IItemStackExtension.getEnchantmentLevel(Holder)            [lecture jeu]
 *   - IItemStackExtension.getAllEnchantments(RegistryLookup)     [ItemEnchantments]
 *   - EnchantmentHelper.updateEnchantments(stack, Consumer&lt;Mutable&gt;)
 *   - ItemEnchantments.Mutable.set/removeIf
 * L'auto-port (scripts/autoport.py) remplace les anciens appels automatiquement.
 */
public final class DeepEnchant {

    private DeepEnchant() {}

    /**
     * PORT 1.21.1 : Enchantment est un REGISTRE DATAPACK (Registries.ENCHANTMENT),
     * plus dans BuiltInRegistries. Toutes les resolutions passent par un
     * RegistryAccess (level.registryAccess(), stack.getRegistry(), ...).
     */
    // Cache du dernier RegistryAccess connu (le registre datapack enchantment
    // n'est pas accessible statiquement ; on memorise l'access d'un level/stack).
    private static volatile RegistryAccess cachedAccess;

    public static void rememberAccess(RegistryAccess access) {
        if (access != null) cachedAccess = access;
    }

    /** Access en cache (initialise EMPTY si aucun encore — a utiliser en secours). */
    public static RegistryAccess access() {
        return cachedAccess != null ? cachedAccess : net.minecraft.core.RegistryAccess.EMPTY;
    }

    // FIX v6 : dernier registre valide connu (defense en profondeur). Si un
    // access sans registre datapack arrive (RegistryAccess.EMPTY = aucun level
    // charge encore), on retourne le dernier registre valide au lieu de lancer
    // "Missing registry: minecraft:enchantment" qui plantait toute la
    // generation d'items (enchantements, glow, infusions, coffres, mobs).
    private static volatile Registry<Enchantment> lastGoodRegistry;

    // FIX v38 : lookup fiable de la cle d'un enchantement, INDEPENDANT du
    // registre mis en cache (cachedAccess / lastGoodRegistry ne sont mis a
    // jour que cote SERVEUR, via onServerStarted/tick). Cote CLIENT, les
    // instances Enchantment recues par synchronisation reseau ne sont pas
    // les MEMES objets Java que celles du registre serveur en cache : une
    // recherche inverse "registry.getKey(en)" (par IDENTITE d'objet, cf.
    // MappedRegistry) echoue alors SILENCIEUSEMENT (retourne null, aucune
    // exception) pour tout enchantement, cote client. C'est la cause du bug
    // "les enchantements s'appliquent mais aucun nom/description ne
    // s'affiche dans l'infobulle" : getKeySafe() renvoyait null pour les 6
    // enchantements d'un item alors que forEach()/map() venaient JUSTEMENT
    // d'obtenir un Holder<Enchantment> connaissant sa propre cle (via
    // ItemEnchantments.keySet()), avant de la jeter en ne gardant que
    // h.value(). On memorise desormais cette cle des qu'on la voit, par
    // IDENTITE de l'objet Enchantment (IdentityHashMap : deux enchantements
    // "egaux" en structure mais d'instances differentes ne doivent PAS se
    // confondre), afin que getKeySafe() la retrouve sans dependre d'aucun
    // registre mis en cache.
    private static final Map<Enchantment, ResourceLocation> KEY_BY_IDENTITY =
            Collections.synchronizedMap(new IdentityHashMap<>());

    /** Memorise la cle d'un enchantement des qu'on la connait avec certitude (via un Holder). */
    private static void rememberKey(Holder<Enchantment> holder) {
        if (holder == null) return;
        holder.unwrapKey().ifPresent(key -> KEY_BY_IDENTITY.put(holder.value(), key.location()));
    }

    public static Registry<Enchantment> reg(RegistryAccess access) {
        rememberAccess(access);
        try {
            Registry<Enchantment> reg = access.registryOrThrow(Registries.ENCHANTMENT);
            lastGoodRegistry = reg;
            return reg;
        } catch (IllegalStateException e) {
            if (lastGoodRegistry != null) {
                return lastGoodRegistry;
            }
            throw e;
        }
    }

    /** Enchantment de registre par path, ou null si inconnu. */
    public static Enchantment byId(RegistryAccess access, String id) {
        if (id == null) return null;
        rememberAccess(access);
        Enchantment found = reg(access).get(ResourceLocation.tryParse(id));
        // FIX (rapporté en jeu : "/testhearts donnait un shulker sans aucun
        // enchantement visible") : ResourceLocation.tryParse("light_heart")
        // (SANS ':') retombe SILENCIEUSEMENT sur le namespace "minecraft"
        // (ResourceLocation.tryParse -> défaut "minecraft" si aucun ':'
        // n'est présent), donc byId("light_heart") cherchait en réalité
        // "minecraft:light_heart" (inexistant) et renvoyait null sans
        // aucune erreur -- un appelant oubliant de préfixer explicitement
        // "deep_lucky_block:" perdait alors silencieusement TOUS ses
        // enchantements. Filet de secours en profondeur : si l'id fourni ne
        // contient aucun ':' et que la recherche a échoué, on retente avec
        // le namespace du mod avant d'abandonner.
        if (found == null && id.indexOf(':') < 0) {
            found = reg(access).get(ResourceLocation.fromNamespaceAndPath("deep_lucky_block", id));
        }
        return found;
    }

    public static Holder<Enchantment> holder(RegistryAccess access, Enchantment en) {
        if (en == null) return null;
        if (access != null) {
            try {
                return reg(access).wrapAsHolder(en);
            } catch (Exception ignored) {
            }
        }
        return Holder.direct(en);
    }

    /** Niveau d'un enchantement identifie par ResourceKey (Enchantments.FALL_PROTECTION etc.). */
    public static int keyLevel(RegistryAccess access, ItemStack stack, ResourceKey<Enchantment> key) {
        rememberAccess(access);
        return getLevel(stack, keyHolder(access, key));
    }

    /** Holder d'un effet connu par ResourceKey (MobEffects.*). */
    public static Holder<net.minecraft.world.effect.MobEffect> mobEffectHolder(RegistryAccess access, ResourceKey<net.minecraft.world.effect.MobEffect> key) {
        rememberAccess(access);
        return access.registryOrThrow(Registries.MOB_EFFECT).getHolder(key)
                .orElseThrow(() -> new IllegalStateException("Effet inconnu: " + key));
    }

    /** Holder d'un enchantement connu par ResourceKey (cle vanilla type Enchantments.SILK_TOUCH). */
    public static Holder<Enchantment> keyHolder(RegistryAccess access, ResourceKey<Enchantment> key) {
        rememberAccess(access);
        return access.registryOrThrow(Registries.ENCHANTMENT).getHolder(key)
                .orElseThrow(() -> new IllegalStateException("Enchantement inconnu: " + key));
    }

    /** ResourceLocation (path) d'un enchantement. */
    public static ResourceLocation path(RegistryAccess access, Enchantment en) {
        if (en == null) return null;
        // FIX v38 : priorite absolue au cache par identite (fiable cote
        // client, voir KEY_BY_IDENTITY) avant toute recherche inverse dans
        // un registre qui peut appartenir a un autre cote (serveur vs
        // client) et donc contenir des instances differentes de "en".
        ResourceLocation known = KEY_BY_IDENTITY.get(en);
        if (known != null) return known;

        rememberAccess(access);
        ResourceLocation fromRegistry = reg(access).getKey(en);
        if (fromRegistry != null) return fromRegistry;

        // FIX v38 : dernier recours si l'enchantement n'a jamais transite par
        // forEach()/map() (donc jamais memorise) ET que la recherche par
        // identite dans le registre courant echoue : on tente aussi
        // RegistryAccess.EMPTY (registres datapack vanilla bases uniquement),
        // au cas ou "en" appartienne a un registre different de celui mis en
        // cache (ex. registre client vs registre serveur).
        try {
            ResourceLocation fallback = RegistryAccess.EMPTY.registryOrThrow(Registries.ENCHANTMENT).getKey(en);
            if (fallback != null) return fallback;
        } catch (Exception ignored) {
        }
        return null;
    }

    public static ResourceLocation path(Enchantment en) {
        return path(access(), en);
    }

    public static Holder<Enchantment> holder(Enchantment en) {
        if (en == null) return null;
        // FIX v5 : Holder.direct() (Holder.Direct) ne se SERIALISE PAS — a la
        // sauvegarde : "Elements from registry minecraft:enchantment can't be
        // serialized to a value" (holders Direct{}) et l'item/l'entite ne
        // persiste pas. Un holder serialisable est un holder KEYED
        // (Reference sur un ResourceKey), obtenu via Registry.wrapAsHolder().
        // On resout donc via le RegistryAccess du dernier level connu (cache,
        // complet : vanilla + enchantements du mod), sinon via
        // RegistryAccess.EMPTY (registres datapack vanilla). Le direct ne
        // reste que dernier recours (lecture seule, jamais persiste).
        if (cachedAccess != null) {
            try {
                return cachedAccess.registryOrThrow(Registries.ENCHANTMENT).wrapAsHolder(en);
            } catch (Exception ignored) {
            }
        }
        try {
            return RegistryAccess.EMPTY.registryOrThrow(Registries.ENCHANTMENT).wrapAsHolder(en);
        } catch (Exception ignored) {
        }
        return Holder.direct(en);
    }

    public static Enchantment byId(String id) {
        return byId(access(), id);
    }

    /** Alias path() — ResourceLocation d'un enchantement (null si inconnu). */
    public static ResourceLocation getKeySafe(Enchantment en) {
        return path(en);
    }

    /** Remplace stack.getEnchantmentLevel(en) — niveau "jeu" (composants). */
    public static int getLevel(ItemStack stack, Enchantment en) {
        return getLevel(stack, holder(en));
    }

    /**
     * FILET DE SECURITE "SCALING A NIVEAU FORCE" (commande/lucky block/NBT) :
     * - Jusqu'au niveau max officiel de l'enchantement (getMaxLevel), le
     *   niveau est retourne tel quel : AUCUN changement pour le jeu normal.
     * - Au-dela (niveau force par commande, ex. 1000), la puissance continue
     *   d'augmenter mais a rendement decroissant (+1 niveau effectif par
     *   palier de racine carree du depassement), pour eviter qu'une formule
     *   lineaire non plafonnee ne produise des valeurs absurdes/instables a
     *   tres haut niveau tout en restant clairement croissante.
     * Reprend exactement la meme regle que UniversalEnchantmentHandler
     * (softCappedLevel), centralisee ici pour que TOUS les handlers
     * d'enchantements (equipement, peche/trident/elytre, etc.) beneficient
     * du meme filet, meme ceux qui ne passent pas par ce handler.
     */
    public static int softCappedLevel(Enchantment en, int storedLevel) {
        if (en == null) return Math.max(0, storedLevel);
        int cap = Math.max(1, en.getMaxLevel());
        if (storedLevel <= cap) return Math.max(0, storedLevel);
        int extra = storedLevel - cap;
        return cap + Math.max(1, (int) Math.sqrt(extra));
    }

    public static int getLevel(ItemStack stack, Holder<Enchantment> h) {
        if (stack == null || stack.isEmpty() || h == null) return 0;
        return stack.getEnchantmentLevel(h);
    }

    /** Remplace stack.enchant(en, level) — pose le niveau (supprime si <= 0). */
    public static void setLevel(ItemStack stack, Enchantment en, int level) {
        setLevel(stack, holder(en), level);
    }

    public static void setLevel(ItemStack stack, Holder<Enchantment> h, int level) {
        if (stack == null || stack.isEmpty() || h == null) return;
        EnchantmentHelper.updateEnchantments(stack, m -> {
            if (level <= 0) {
                m.removeIf(x -> x.equals(h));
            } else {
                m.set(h, level);
            }
        });
    }

    /** Remplace stack.removeEnchantment(en). */
    public static void remove(ItemStack stack, Enchantment en) {
        setLevel(stack, en, 0);
    }

    public static void remove(ItemStack stack, Holder<Enchantment> h) {
        setLevel(stack, h, 0);
    }

    /** stack.hasEnchantment(en) / getLevel(stack, en) > 0 */
    public static boolean has(ItemStack stack, Enchantment en) {
        return getLevel(stack, en) > 0;
    }

    /**
     * Remplace l'itération de stack.getEnchantmentLevels() /
     * EnchantmentHelper.getEnchantments(stack).
     */
    public static void forEach(ItemStack stack, BiConsumer<Enchantment, Integer> consumer) {
        if (stack == null || stack.isEmpty()) return;
        ItemEnchantments ench = stack.getEnchantments();
        for (Holder<Enchantment> h : ench.keySet()) {
            int lvl = ench.getLevel(h);
            if (lvl > 0) {
                // FIX v38 : on memorise la cle AVANT de perdre le Holder (voir
                // KEY_BY_IDENTITY plus haut) afin que getKeySafe() la retrouve
                // meme si le registre en cache est celui du serveur et que
                // cet Enchantment est une instance cote client.
                rememberKey(h);
                consumer.accept(h.value(), lvl);
            }
        }
    }

    /** Snapshot Map&lt;Enchantment, Integer&gt; (équivalent 1.20.1 getEnchantments). */
    public static Map<Enchantment, Integer> map(ItemStack stack) {
        Map<Enchantment, Integer> out = new HashMap<>();
        forEach(stack, out::put);
        return out;
    }

    /**
     * Conflits d'enchantements — remplace Enchantment.checkCompatibility(other)
     * (méthode supprimée en 1.21). Délégué à la logique métier du mod.
     */
    public static boolean areCompatible(Enchantment a, Enchantment b) {
        if (a == null || b == null) return true;
        // PORT 1.21.1 : CustomEnchantment n'est plus une sous-classe d'Enchantment
        // (Enchantment est un record) -> la table de conflits est statique et
        // fonctionne sur les paths du registre (gère aussi vanilla vs vanilla).
        return deepluckyblock.enchantments.CustomEnchantment.checkCompatibilityMod(a, b);
    }
}
