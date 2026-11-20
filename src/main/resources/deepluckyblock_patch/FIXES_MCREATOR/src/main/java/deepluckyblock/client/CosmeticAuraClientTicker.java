package deepluckyblock.client;

import deepluckyblock.DeepLuckyBlockMod;
import deepluckyblock.enchantments.EnchantmentComboRegistry;
import deepluckyblock.enchantments.EnchantmentComboRegistry.ComboMask;
import deepluckyblock.enchantments.UniversalEnchantmentHandler;
import deepluckyblock.util.DeepEnchant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * T321-CONTINUOUS-COSMETIC-FX : Moteur client temps réel d'auras, halos, couronnes et rubans d'ailes.
 *
 * Pourquoi côté Client (ClientTickEvent.Post) et non via des salves serveur espacées (% 20..200) ?
 *  1) Continuité infinie : les particules sont émises à chaque tick directement sur la position +
 *     vélocité courante de l'entité -> le halo (ex. Combo #110, Roi, Esprit Mystique, Égide Radieuse,
 *     ailes d'Archange...) suit le joueur en continu sans jamais clignoter ni rester figé derrière lui.
 *  2) Zéro paquet réseau & zéro charge serveur (0.00 ms TPS).
 *  3) Ultra-optimisé anti-lag : profil cosmétique mis en cache sur un simple int bitmask toutes les
 *     10 ticks (0,5 s), culling à 24 blocs, pause automatique et plafond strict de particules/tick.
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID, value = Dist.CLIENT)
public final class CosmeticAuraClientTicker {

    private CosmeticAuraClientTicker() {}

    public static final int F_COMBO_110_AVATAR      = 1;
    public static final int F_COMBO_097_ETERNAL     = 1 << 1;
    public static final int F_COMBO_098_SWORD_GOD   = 1 << 2;
    public static final int F_COMBO_101_ARCHANGEL   = 1 << 3;
    public static final int F_COMBO_106_WARLORD     = 1 << 4;
    public static final int F_COMBO_062_PLAGUE      = 1 << 5;
    public static final int F_ENCH_KING             = 1 << 6;
    public static final int F_ENCH_MYSTIC_MIND      = 1 << 7;
    public static final int F_ENCH_RADIANT_AEGIS    = 1 << 8;
    public static final int F_ENCH_WINGED_ENDURANCE = 1 << 9;
    public static final int F_ENCH_DREAD_AURA       = 1 << 10;
    /** Halo générique, limité aux armes dont le choix Lucky Block persisté l'autorise. */
    public static final int F_HALO_CONTINUOUS       = 1 << 11;

    /**
     * T436 : deux cosmétiques de plus quittent le monde des particules vanilla pour celui des
     * pixels. Ils existaient déjà côté serveur ({@code EnchantmentParticleFX.cloudCushion} et
     * {@code shockwaveRing}) mais AUCUN bit ne les portait jusqu'au rendu : le joueur ne voyait
     * qu'un nuage de particules génériques, jamais la galette ni les anneaux de l'aperçu.
     */
    public static final int F_ENCH_CLOUD_CUSHION   = 1 << 12;
    public static final int F_ENCH_LANDING_WAVE    = 1 << 13;

    /**
     * T436 : les deux derniers ornements de JOUEUR qui n'existaient qu'en particules.
     * Les trois restants ({@code doubleHelix}, {@code tetherBeam}, {@code burstOnEntity})
     * ne sont pas des ornements portés : ce sont des effets de combat tirés à une position
     * arbitraire ou entre deux entités. Ils ne se déduisent pas de l'équipement et
     * demanderaient un paquet serveur dédié — ils restent donc en particules.
     */
    public static final int F_ENCH_CROWN_HALO      = 1 << 14;
    public static final int F_ENCH_WING_RIBBONS    = 1 << 15;

    /** Les ornements posés au sol : ce sont eux, et eux seuls, qui laissent une traînée. */
    public static final int F_SOL = F_COMBO_106_WARLORD | F_COMBO_062_PLAGUE | F_ENCH_DREAD_AURA;

    /** T349 : rayon de collecte des profils cosmétiques (rendu fait par CosmeticPixelAura). */
    private static final double CULL_DIST_SQR = 40.0 * 40.0;

    private static final class CachedProfile {
        int flags;
        long expiresAt;
    }

    private static final Map<Integer, CachedProfile> PROFILE_CACHE = new HashMap<>();
    private static ClientLevel cachedLevel = null;
    private static long lastGameTime = -1L;

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        try {
            Minecraft mc = Minecraft.getInstance();
            LocalPlayer viewer = mc.player;
            ClientLevel level = mc.level;
            if (viewer == null || level == null) return;

            if (cachedLevel != level) {
                PROFILE_CACHE.clear();
                CosmeticTrail.vide();          // les pas d'un autre monde n'ont plus de sens
                cachedLevel = level;
                lastGameTime = -1L;
            }

            long now = level.getGameTime();
            // En pause solo, gameTime n'avance pas -> 0 particule émise pendant l'écran pause !
            if (now == lastGameTime) return;
            lastGameTime = now;

            DeepEnchant.rememberClientAccess(level.registryAccess());

            if ((now & 127L) == 0L && PROFILE_CACHE.size() > 64) {
                Iterator<Map.Entry<Integer, CachedProfile>> it = PROFILE_CACHE.entrySet().iterator();
                while (it.hasNext()) {
                    if (now > it.next().getValue().expiresAt + 100L) it.remove();
                }
            }

            // T349-COSMETIC-PIXEL-AURA : PLUS AUCUNE particule émise ici.
            // Le tick ne sert plus qu'à rafraîchir le bitmask (cache 10 ticks, gratuit) ; le dessin des
            // ailes / couronnes / auras est fait une seule fois par frame par CosmeticPixelAura.
            getCosmeticFlags(viewer, now);

            // T436 : la traînée au sol est une mémoire de pas en coordonnées monde. On la
            // nourrit ici, une fois par tick : c'est ce qui la fait se rétracter à l'arrêt
            // et se redéployer au départ, au lieu de défiler bêtement avec le temps.
            int fv = getCosmeticFlags(viewer, now);
            if ((fv & F_SOL) != 0) CosmeticTrail.suivre(viewer, now);
            if ((fv & (F_ENCH_CLOUD_CUSHION | F_ENCH_LANDING_WAVE)) != 0) {
                if ((fv & F_ENCH_CLOUD_CUSHION) != 0) CosmeticTrail.suivre(viewer, now);
                CosmeticTrail.noteChute(viewer, now);
            }

            List<? extends Player> nearbyPlayers = level.players();
            for (int i = 0, n = nearbyPlayers.size(); i < n; i++) {
                Player other = nearbyPlayers.get(i);
                if (other == viewer || !other.isAlive() || other.isSpectator()) continue;
                if (other.distanceToSqr(viewer) > CULL_DIST_SQR) continue;
                int fo = getCosmeticFlags(other, now);
                if ((fo & (F_SOL | F_ENCH_CLOUD_CUSHION)) != 0) CosmeticTrail.suivre(other, now);
                if ((fo & F_ENCH_LANDING_WAVE) != 0) CosmeticTrail.noteChute(other, now);
            }
            if ((now & 63L) == 0L) CosmeticTrail.nettoie(now);
        } catch (Throwable ignored) {}
    }

    public static int getCosmeticFlags(LivingEntity entity, long now) {
        CachedProfile cp = PROFILE_CACHE.get(entity.getId());
        if (cp != null && now < cp.expiresAt) {
            return cp.flags;
        }
        if (cp == null) {
            cp = new CachedProfile();
            PROFILE_CACHE.put(entity.getId(), cp);
        }
        cp.expiresAt = now + 10L;

        // T350-COSMETIC-RELIABLE : on lit d'abord le masque de combos (les 6 pièces d'un combo
        // suffisent, même si l'équipement n'est pas "classiquement" enchanté). L'ancien garde-fou
        // "aucun équipement enchanté -> 0" pouvait couper le halo du Combo #110 alors que le combo
        // était actif : c'est exactement le symptôme "le halo ne fonctionne plus".
        int f = 0;
        ComboMask mask = EnchantmentComboRegistry.getActiveMask(entity);
        if (!mask.isEmpty()) {
            if (mask.has(EnchantmentComboRegistry.IDX_AVATAR_SUPREME_DEEP_LUCKY)) f |= F_COMBO_110_AVATAR;
            if (mask.has(EnchantmentComboRegistry.IDX_ARMURE_ETERNELLE_TRANSCENDANTE)) f |= F_COMBO_097_ETERNAL;
            if (mask.has(EnchantmentComboRegistry.IDX_DIEU_LAME_SUPREME)) f |= F_COMBO_098_SWORD_GOD;
            if (mask.has(EnchantmentComboRegistry.IDX_ARCHANGE_JUGEMENT_SOLAIRE)) f |= F_COMBO_101_ARCHANGEL;
            if (mask.has(EnchantmentComboRegistry.IDX_SEIGNEUR_GUERRE_APOCALYPSE)) f |= F_COMBO_106_WARLORD;
            if (mask.has(EnchantmentComboRegistry.IDX_PESTE_AMBULANTE)) f |= F_COMBO_062_PLAGUE;
        }

        boolean equipped = UniversalEnchantmentHandler.hasAnyEnchantedEquipment(entity);
        if (f == 0 && !equipped) {
            cp.flags = 0;
            return 0;
        }

        // T436-LOOT-ONLY : c'est la PIÈCE qui porte l'enchantement qui doit venir du loot
        // du mod. Une armure montée à l'enclume avec un livre du mod garde donc son rendu
        // vanilla. On lit pièce par pièce, et non plus le maximum sur toute l'armure.
        if (surLoot(entity, "deep_lucky_block:king")) f |= F_ENCH_KING;
        if (surLoot(entity, "deep_lucky_block:mystic_mind")) f |= F_ENCH_MYSTIC_MIND;
        if (surLoot(entity, "deep_lucky_block:heart_resonance")) f |= F_ENCH_RADIANT_AEGIS;
        if (surLoot(entity, "deep_lucky_block:wing_toughness_armor")) f |= F_ENCH_WINGED_ENDURANCE;
        if (surLoot(entity, "deep_lucky_block:dread_aura")) f |= F_ENCH_DREAD_AURA;

        // Mêmes enchantements que les appels serveur de cloudCushion/shockwaveRing : le pixel
        // et la particule parlent enfin du même équipement.
        if (surLoot(entity, "deep_lucky_block:cloud_walker")
                || surLoot(entity, "deep_lucky_block:air_walk")
                || surLoot(entity, "deep_lucky_block:cloud_stride")) {
            f |= F_ENCH_CLOUD_CUSHION;
        }
        if (surLoot(entity, "deep_lucky_block:crash_landing")
                || surLoot(entity, "deep_lucky_block:meteor_stomp")) {
            f |= F_ENCH_LANDING_WAVE;
        }
        // Mêmes portes que les appels serveur : general pour le diadème,
        // winging (sur l'élytre) pour les banderoles.
        if (surLoot(entity, "deep_lucky_block:general")) f |= F_ENCH_CROWN_HALO;
        // Porte recopiée sur FishingTridentElytraHandler.elytraTickImpl : les banderoles
        // ne sortent QUE d'une élytre au torse, EN VOL PLANÉ, sous aerodynamic ou
        // wind_rider. Scanner toute l'armure, ou les afficher au sol, montrerait une
        // parure que le jeu ne produit jamais.
        // (Le serveur ajoute « vitesse < 3,5 » sur la seule branche aerodynamic ; on ne
        //  le reprend pas, sinon les rubans clignoteraient au milieu d'un piqué.)
        ItemStack torse = entity.getItemBySlot(EquipmentSlot.CHEST);
        if (torse.is(Items.ELYTRA) && DeepNbt.isModLoot(torse) && entity.isFallFlying()
                && (UniversalEnchantmentHandler.getEnchantLvl(torse, "deep_lucky_block:aerodynamic") > 0
                 || UniversalEnchantmentHandler.getEnchantLvl(torse, "deep_lucky_block:wind_rider") > 0)) {
            f |= F_ENCH_WING_RIBBONS;
        }

        // Plus d'aura constante pour toute arme non à distance : lecture du choix persisté au loot.
        // Les tridents enchantés conservent explicitement leur comportement antérieur, sans tirage.
        if (f == 0 && hasContinuousWeaponHalo(entity)) f |= F_HALO_CONTINUOUS;

        cp.flags = f;
        return f;
    }

    /**
     * L'enchantement est-il porté par une pièce d'armure VENUE DU LOOT DU MOD ?
     * Le loot du mod est souvent une pièce vanilla : c'est la provenance qui tranche,
     * jamais le type de l'item ni le namespace de l'enchantement.
     */
    private static boolean surLoot(LivingEntity entity, String id) {
        for (ItemStack piece : entity.getArmorSlots()) {
            if (!deepluckyblock.util.DeepNbt.isModLoot(piece)) continue;
            if (UniversalEnchantmentHandler.getEnchantLvl(piece, id) > 0) return true;
        }
        return false;
    }

    /** Aucun aléa ici : seulement deux lectures d'items et le marqueur NBT déjà synchronisé. */
    private static boolean hasContinuousWeaponHalo(LivingEntity entity) {
        ItemStack main = entity.getMainHandItem();
        ItemStack off = entity.getOffhandItem();
        // T436 : même un trident doit venir du loot du mod. Avant, N'IMPORTE quel trident
        // enchanté — y compris à la table — déclenchait le halo continu.
        if (deepluckyblock.util.DeepNbt.isModLoot(main)
                && main.getItem() instanceof net.minecraft.world.item.TridentItem && main.isEnchanted()) return true;
        if (deepluckyblock.util.DeepNbt.isModLoot(off)
                && off.getItem() instanceof net.minecraft.world.item.TridentItem && off.isEnchanted()) return true;
        return deepluckyblock.util.DeepNbt.hasLuckyWeaponAura(main)
                || deepluckyblock.util.DeepNbt.hasLuckyWeaponAura(off);
    }

    /**
     * T349 : plus aucune particule. Conservé vide volontairement pour ne rien casser côté appelants
     * éventuels : le cosmétique est désormais intégralement rendu en mini-cubes 3D par
     * {@link CosmeticPixelAura} (formes d'ailes réelles, couronnes en anneau, auras au sol).
     */
    private static int emitForEntity(ClientLevel level, LivingEntity entity, long now, int budget) {
        return budget;
    }
}
