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
import net.minecraft.world.entity.player.Player;
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

            List<? extends Player> nearbyPlayers = level.players();
            for (int i = 0, n = nearbyPlayers.size(); i < n; i++) {
                Player other = nearbyPlayers.get(i);
                if (other == viewer || !other.isAlive() || other.isSpectator()) continue;
                if (other.distanceToSqr(viewer) > CULL_DIST_SQR) continue;
                getCosmeticFlags(other, now);
            }
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

        if (f == 0 && !UniversalEnchantmentHandler.hasAnyEnchantedEquipment(entity)) {
            cp.flags = 0;
            return 0;
        }

        if (UniversalEnchantmentHandler.getArmorEnchantLvl(entity, "deep_lucky_block:king") > 0) f |= F_ENCH_KING;
        if (UniversalEnchantmentHandler.getArmorEnchantLvl(entity, "deep_lucky_block:mystic_mind") > 0) f |= F_ENCH_MYSTIC_MIND;
        if (UniversalEnchantmentHandler.getArmorEnchantLvl(entity, "deep_lucky_block:heart_resonance") > 0) f |= F_ENCH_RADIANT_AEGIS;
        if (UniversalEnchantmentHandler.getArmorEnchantLvl(entity, "deep_lucky_block:wing_toughness_armor") > 0) f |= F_ENCH_WINGED_ENDURANCE;
        if (UniversalEnchantmentHandler.getArmorEnchantLvl(entity, "deep_lucky_block:dread_aura") > 0) f |= F_ENCH_DREAD_AURA;

        cp.flags = f;
        return f;
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
