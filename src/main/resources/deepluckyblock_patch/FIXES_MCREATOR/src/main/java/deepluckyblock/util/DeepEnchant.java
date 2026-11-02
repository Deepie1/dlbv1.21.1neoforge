package deepluckyblock.util;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
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
import java.util.Optional;
import java.util.function.BiConsumer;

/**
 * DeepEnchant — pont d'API pour 1.21.1 (version 3 — T315-SIDE-SAFE-REGISTRY).
 *
 * ⚠️ Rupture 1.20.5+ : les enchantements ne sont PLUS des champs d'ItemStack.
 * Ils vivent dans le composant de données DataComponents.ENCHANTMENTS dont le
 * type est {@link ItemEnchantments} (pas un Map).
 *
 * T315-SIDE-SAFE-REGISTRY :
 * En solo (IntegratedServer + Render thread dans la même JVM), le client et le
 * serveur possèdent CHACUN leur propre instance de MappedRegistry<Enchantment>
 * et leurs propres Holder.Reference<Enchantment> (comparés par identité dans
 * Registry.asHolderIdMap() lors de l'encodage réseau Netty).
 * Séparer strictement cachedServerAccess et cachedClientAccess empêche tout
 * appel client (ex. CloudWalkerClientInput / overlays / tooltips) d'écraser le
 * RegistryAccess serveur et garantit que tout ItemStack serveur reçoit toujours
 * un Holder.Reference appartenant au MappedRegistry du ServerLevel actif.
 */
public final class DeepEnchant {

    private DeepEnchant() {}

    // Caches séparés Serveur vs Client (aucune pollution croisée en solo IntegratedServer)
    private static volatile RegistryAccess cachedServerAccess;
    private static volatile RegistryAccess cachedClientAccess;

    private static volatile Registry<Enchantment> lastGoodServerRegistry;
    private static volatile Registry<Enchantment> lastGoodClientRegistry;

    // Lookup fiable de la clé d'un enchantement par IDENTITÉ d'objet Java,
    // peuplé dès l'indexation des registres serveur/client ET lors des lectures d'ItemStack.
    private static final Map<Enchantment, ResourceLocation> KEY_BY_IDENTITY =
            Collections.synchronizedMap(new IdentityHashMap<>());

    /** Détecte sans aucun import client si le thread courant est un thread client/rendu. */
    private static boolean isClientThread() {
        if (deepluckyblock.DeepLuckyBlockMod.isServerThread()) {
            return false;
        }
        String name = Thread.currentThread().getName();
        if (name != null && (name.equals("Render thread") || name.startsWith("Client"))) {
            return true;
        }
        return cachedServerAccess == null && cachedClientAccess != null;
    }

    private static void indexRegistry(Registry<Enchantment> reg) {
        if (reg == null) return;
        try {
            for (Enchantment e : reg) {
                if (e == null) continue;
                ResourceLocation rl = reg.getKey(e);
                if (rl != null) {
                    KEY_BY_IDENTITY.put(e, rl);
                }
            }
        } catch (Exception ignored) {
        }
    }

    /** Mémorise explicitement le RegistryAccess du SERVEUR actif. */
    public static void rememberServerAccess(RegistryAccess access) {
        if (access == null || access == RegistryAccess.EMPTY) return;
        cachedServerAccess = access;
        try {
            Registry<Enchantment> r = access.registryOrThrow(Registries.ENCHANTMENT);
            if (r != lastGoodServerRegistry) {
                lastGoodServerRegistry = r;
                indexRegistry(r);
            }
        } catch (Exception ignored) {
        }
    }

    /** Mémorise explicitement le RegistryAccess du CLIENT actif (sans jamais toucher au cache serveur). */
    public static void rememberClientAccess(RegistryAccess access) {
        if (access == null || access == RegistryAccess.EMPTY) return;
        cachedClientAccess = access;
        try {
            Registry<Enchantment> r = access.registryOrThrow(Registries.ENCHANTMENT);
            if (r != lastGoodClientRegistry) {
                lastGoodClientRegistry = r;
                indexRegistry(r);
            }
        } catch (Exception ignored) {
        }
    }

    /** Purgé à l'arrêt du serveur intégré/dédié pour éviter tout registre périmé entre deux mondes. */
    public static void clearServerAccess() {
        cachedServerAccess = null;
        lastGoodServerRegistry = null;
    }

    /** Mémorise un RegistryAccess en le routant automatiquement vers le bon côté (Serveur vs Client). */
    public static void rememberAccess(RegistryAccess access) {
        if (access == null || access == RegistryAccess.EMPTY) return;
        if (isClientThread()) {
            rememberClientAccess(access);
        } else {
            rememberServerAccess(access);
        }
    }

    /**
     * Retourne le RegistryAccess approprié au thread courant :
     * - Sur le Server thread (ou thread serveur) : UNIQUEMENT cachedServerAccess (jamais le registre client !).
     * - Sur le Render thread / client : cachedClientAccess en priorité, sinon cachedServerAccess.
     */
    public static RegistryAccess access() {
        if (deepluckyblock.DeepLuckyBlockMod.isServerThread()) {
            return cachedServerAccess != null ? cachedServerAccess : RegistryAccess.EMPTY;
        }
        if (isClientThread()) {
            if (cachedClientAccess != null) return cachedClientAccess;
            if (cachedServerAccess != null) return cachedServerAccess;
            return RegistryAccess.EMPTY;
        }
        if (cachedServerAccess != null) return cachedServerAccess;
        if (cachedClientAccess != null) return cachedClientAccess;
        return RegistryAccess.EMPTY;
    }

    /** Mémorise la clé d'un enchantement dès qu'on la connaît avec certitude (via un Holder). */
    private static void rememberKey(Holder<Enchantment> holder) {
        if (holder == null) return;
        holder.unwrapKey().ifPresent(key -> KEY_BY_IDENTITY.put(holder.value(), key.location()));
    }

    public static Registry<Enchantment> reg(RegistryAccess access) {
        boolean client = isClientThread();
        if (access != null && access != RegistryAccess.EMPTY) {
            rememberAccess(access);
            try {
                Registry<Enchantment> reg = access.registryOrThrow(Registries.ENCHANTMENT);
                if (client) {
                    if (reg != lastGoodClientRegistry) {
                        lastGoodClientRegistry = reg;
                        indexRegistry(reg);
                    }
                } else {
                    if (reg != lastGoodServerRegistry) {
                        lastGoodServerRegistry = reg;
                        indexRegistry(reg);
                    }
                }
                return reg;
            } catch (IllegalStateException ignored) {
            }
        }
        Registry<Enchantment> fallback = client
                ? (lastGoodClientRegistry != null ? lastGoodClientRegistry : lastGoodServerRegistry)
                : lastGoodServerRegistry;
        if (fallback != null) {
            return fallback;
        }
        if (access != null) {
            return access.registryOrThrow(Registries.ENCHANTMENT);
        }
        return RegistryAccess.EMPTY.registryOrThrow(Registries.ENCHANTMENT);
    }

    /** Enchantment de registre par path, ou null si inconnu. */
    public static Enchantment byId(RegistryAccess access, String id) {
        if (id == null) return null;
        Registry<Enchantment> r = reg(access);
        ResourceLocation parsed = ResourceLocation.tryParse(id);
        Enchantment found = parsed != null ? r.get(parsed) : null;
        if (found != null && parsed != null) {
            KEY_BY_IDENTITY.put(found, parsed);
            return found;
        }
        if (found == null && id.indexOf(':') < 0) {
            ResourceLocation modLoc = ResourceLocation.fromNamespaceAndPath("deep_lucky_block", id);
            found = r.get(modLoc);
            if (found != null) {
                KEY_BY_IDENTITY.put(found, modLoc);
            }
        }
        return found;
    }

    /**
     * Résout un Holder.Reference<Enchantment> garanti appartenir au registre cible {@code access}.
     * Même si {@code en} provient d'un autre monde ou de l'autre côté (client vs serveur),
     * la résolution par ResourceKey renvoie le Holder.Reference canonique du registre actif
     * (indispensable pour que Registry.asHolderIdMap().getIdOrThrow réussisse à l'encodage Netty).
     */
    public static Holder<Enchantment> holder(RegistryAccess access, Enchantment en) {
        if (en == null) return null;
        RegistryAccess targetAccess = (access != null && access != RegistryAccess.EMPTY) ? access : access();
        try {
            Registry<Enchantment> r = reg(targetAccess);
            ResourceLocation rl = r.getKey(en);
            if (rl == null) {
                rl = KEY_BY_IDENTITY.get(en);
            }
            if (rl != null) {
                Optional<Holder.Reference<Enchantment>> ref =
                        r.getHolder(ResourceKey.create(Registries.ENCHANTMENT, rl));
                if (ref.isPresent()) {
                    rememberKey(ref.get());
                    return ref.get();
                }
            }
            Holder<Enchantment> wrapped = r.wrapAsHolder(en);
            if (wrapped instanceof Holder.Reference<Enchantment>) {
                rememberKey(wrapped);
                return wrapped;
            }
        } catch (Exception ignored) {
        }
        try {
            return RegistryAccess.EMPTY.registryOrThrow(Registries.ENCHANTMENT).wrapAsHolder(en);
        } catch (Exception ignored) {
        }
        return Holder.direct(en);
    }

    /** Niveau d'un enchantement identifié par ResourceKey (Enchantments.FALL_PROTECTION etc.). */
    public static int keyLevel(RegistryAccess access, ItemStack stack, ResourceKey<Enchantment> key) {
        if (stack == null || stack.isEmpty() || key == null) return 0;
        try {
            return getLevel(stack, keyHolder(access, key));
        } catch (Exception ignored) {
            return getLevelByLocation(stack, key.location());
        }
    }

    /** Holder d'un effet connu par ResourceKey (MobEffects.*). */
    public static Holder<net.minecraft.world.effect.MobEffect> mobEffectHolder(RegistryAccess access, ResourceKey<net.minecraft.world.effect.MobEffect> key) {
        rememberAccess(access);
        return access.registryOrThrow(Registries.MOB_EFFECT).getHolder(key)
                .orElseThrow(() -> new IllegalStateException("Effet inconnu: " + key));
    }

    /** Holder d'un enchantement connu par ResourceKey (clé vanilla type Enchantments.SILK_TOUCH). */
    public static Holder<Enchantment> keyHolder(RegistryAccess access, ResourceKey<Enchantment> key) {
        RegistryAccess target = (access != null && access != RegistryAccess.EMPTY) ? access : access();
        return reg(target).getHolder(key)
                .orElseThrow(() -> new IllegalStateException("Enchantement inconnu: " + key));
    }

    /** ResourceLocation (namespace:path) d'un enchantement. */
    public static ResourceLocation path(RegistryAccess access, Enchantment en) {
        if (en == null) return null;
        ResourceLocation known = KEY_BY_IDENTITY.get(en);
        if (known != null) return known;

        try {
            Registry<Enchantment> r = reg(access);
            ResourceLocation fromRegistry = r.getKey(en);
            if (fromRegistry != null) {
                KEY_BY_IDENTITY.put(en, fromRegistry);
                return fromRegistry;
            }
        } catch (Exception ignored) {
        }

        if (lastGoodServerRegistry != null) {
            ResourceLocation srv = lastGoodServerRegistry.getKey(en);
            if (srv != null) {
                KEY_BY_IDENTITY.put(en, srv);
                return srv;
            }
        }
        if (lastGoodClientRegistry != null) {
            ResourceLocation cli = lastGoodClientRegistry.getKey(en);
            if (cli != null) {
                KEY_BY_IDENTITY.put(en, cli);
                return cli;
            }
        }

        try {
            ResourceLocation fallback = RegistryAccess.EMPTY.registryOrThrow(Registries.ENCHANTMENT).getKey(en);
            if (fallback != null) {
                KEY_BY_IDENTITY.put(en, fallback);
                return fallback;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    public static ResourceLocation path(Enchantment en) {
        return path(access(), en);
    }

    public static Holder<Enchantment> holder(Enchantment en) {
        return holder(access(), en);
    }

    public static Enchantment byId(String id) {
        return byId(access(), id);
    }

    /** Alias path() — ResourceLocation d'un enchantement (null si inconnu). */
    public static ResourceLocation getKeySafe(Enchantment en) {
        return path(en);
    }

    private static int getLevelByLocation(ItemStack stack, ResourceLocation wanted) {
        if (stack == null || stack.isEmpty() || wanted == null) return 0;
        ItemEnchantments ench = stack.getEnchantments();
        if (ench.isEmpty()) return 0;
        for (Holder<Enchantment> entry : ench.keySet()) {
            Optional<ResourceKey<Enchantment>> rk = entry.unwrapKey();
            if (rk.isPresent() && wanted.equals(rk.get().location())) {
                return ench.getLevel(entry);
            }
        }
        return 0;
    }

    /** Remplace stack.getEnchantmentLevel(en) — niveau "jeu" (composants), tolérant inter-registres. */
    public static int getLevel(ItemStack stack, Enchantment en) {
        if (stack == null || stack.isEmpty() || en == null) return 0;
        Holder<Enchantment> h = holder(en);
        if (h != null) {
            int lvl = stack.getEnchantmentLevel(h);
            if (lvl > 0) return lvl;
        }
        ResourceLocation wanted = path(en);
        return wanted != null ? getLevelByLocation(stack, wanted) : 0;
    }

    /**
     * FILET DE SECURITE "SCALING A NIVEAU FORCE" (commande/lucky block/NBT) :
     * - Jusqu'au niveau max officiel de l'enchantement (getMaxLevel), le
     *   niveau est retourné tel quel : AUCUN changement pour le jeu normal.
     * - Au-delà (niveau forcé par commande, ex. 1000), la puissance continue
     *   d'augmenter mais à rendement décroissant (+1 niveau effectif par
     *   palier de racine carrée du dépassement).
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
        int lvl = stack.getEnchantmentLevel(h);
        if (lvl > 0) return lvl;
        Optional<ResourceKey<Enchantment>> rk = h.unwrapKey();
        return rk.isPresent() ? getLevelByLocation(stack, rk.get().location()) : 0;
    }

    /** Remplace stack.enchant(en, level) — pose le niveau (supprime si <= 0). */
    public static void setLevel(ItemStack stack, Enchantment en, int level) {
        setLevel(stack, holder(en), level);
    }

    public static void setLevel(ItemStack stack, Holder<Enchantment> h, int level) {
        if (stack == null || stack.isEmpty() || h == null) return;
        final ResourceLocation targetKey = h.unwrapKey().map(ResourceKey::location).orElse(null);
        EnchantmentHelper.updateEnchantments(stack, m -> {
            if (level <= 0) {
                m.removeIf(x -> x.equals(h) || (targetKey != null
                        && x.unwrapKey().map(rk -> targetKey.equals(rk.location())).orElse(false)));
            } else {
                if (targetKey != null) {
                    m.removeIf(x -> !x.equals(h)
                            && x.unwrapKey().map(rk -> targetKey.equals(rk.location())).orElse(false));
                }
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
        return deepluckyblock.enchantments.CustomEnchantment.checkCompatibilityMod(a, b);
    }
}
