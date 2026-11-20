package deepluckyblock.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import deepluckyblock.DeepLuckyBlockMod;

import java.util.ArrayList;
import java.util.List;

/**
 * T349-COSMETIC-PIXEL-AURA : moteur cosmétique maison, 100 % pixels 3D, sans AUCUNE particule vanilla.
 *
 * <p><b>Problème résolu.</b> Les ailes, couronnes et auras étaient rendues par 2 à 6 particules
 * ponctuelles par tick : de loin ça ne ressemblait qu'à « quelques points dans le dos », et chaque
 * particule vanilla coûte un tick + un billboard à rendre (donc du lag dès qu'il y a plusieurs joueurs
 * ou que le budget grimpe).</p>
 *
 * <p><b>Solution.</b> On ne stocke plus RIEN par tick : à chaque frame on dessine directement les
 * formes complètes (membrane d'aile, couronne en anneau, aura au sol) en mini-cubes 3D, dans un seul
 * batch dédié à ce système. Coût : une centaine de cubes par joueur, zéro particule, zéro paquet
 * réseau, zéro allocation par frame (aucune liste temporaire).</p>
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID, value = Dist.CLIENT)
public final class CosmeticPixelAura {

    private CosmeticPixelAura() {}

    /**
     * Budget total de cubes par frame (tous joueurs confondus).
     *
     * <p>T435 : 1400 → 3400, les formes de l'aperçu étant bien plus détaillées
     * que les ornements d'origine (une aile valait cinq quads).</p>
     *
     * <p>T436 : 3400 → 4200. Deux ornements de plus sont rendus en pixels —
     * le coussin de nuage et l'onde de choc. Le pire cas n'est plus une somme
     * de maxima indépendants mais une mesure INSTANT PAR INSTANT de ce qui
     * coexiste vraiment ({@code /tmp/t436_budget.js}) : 3747 cubes en l'air
     * (couronne + aile + cumulables + coussin) et 3718 au sol (les mêmes, plus
     * l'onde). Le coussin et l'onde ne peuvent pas coexister : l'un se dessine
     * en l'air, l'autre à l'atterrissage. 4200 laisse 10,8 % de marge — un
     * plafond collé au pire cas n'est plus un garde-fou.</p>
     */
    private static final int MAX_CUBES_PER_FRAME = 4200;
    /** Distance maximale de rendu d'une aura cosmétique. */
    private static final double MAX_DIST_SQR = 40.0 * 40.0;

    private static RenderType auraAdditiveType;
    private static RenderType auraTranslucentType;

    private static int budget;

    private static final List<Player> TARGETS = new ArrayList<>(8);

    // =====================================================================
    // RENDER TYPES DÉDIÉS (aucune interférence avec les VFX d'armes)
    // =====================================================================

    public static RenderType getAdditiveType() {
        if (auraAdditiveType == null) {
            auraAdditiveType = RenderType.create(
                    "dlb_cosmetic_aura_add",
                    DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS,
                    16384,
                    false,
                    false,
                    RenderType.CompositeState.builder()
                            .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                            .setTransparencyState(RenderStateShard.LIGHTNING_TRANSPARENCY)
                            .setCullState(RenderStateShard.NO_CULL)
                            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                            .setOutputState(RenderStateShard.PARTICLES_TARGET)
                            .createCompositeState(false));
        }
        return auraAdditiveType;
    }

    public static RenderType getTranslucentType() {
        if (auraTranslucentType == null) {
            auraTranslucentType = RenderType.create(
                    "dlb_cosmetic_aura_alpha",
                    DefaultVertexFormat.POSITION_COLOR,
                    VertexFormat.Mode.QUADS,
                    16384,
                    false,
                    true,
                    RenderType.CompositeState.builder()
                            .setShaderState(RenderStateShard.RENDERTYPE_LIGHTNING_SHADER)
                            .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                            .setCullState(RenderStateShard.NO_CULL)
                            .setDepthTestState(RenderStateShard.LEQUAL_DEPTH_TEST)
                            .setWriteMaskState(RenderStateShard.COLOR_WRITE)
                            .setOutputState(RenderStateShard.PARTICLES_TARGET)
                            .createCompositeState(false));
        }
        return auraTranslucentType;
    }

    // =====================================================================
    // COLLECTE DES CIBLES (une fois par tick)
    // =====================================================================

    @SubscribeEvent
    public static void onClientTickPost(ClientTickEvent.Post event) {
        try {
            Minecraft mc = Minecraft.getInstance();
            refillTargets(mc, mc.level);
        } catch (Throwable ignored) {
            TARGETS.clear();
        }
    }

    private static void refillTargets(Minecraft mc, ClientLevel level) {
        Player viewer = mc.player;
        TARGETS.clear();
        if (level == null || viewer == null) return;
        TARGETS.add(viewer);
        List<? extends Player> others = level.players();
        for (int i = 0, n = others.size(); i < n; i++) {
            Player other = others.get(i);
            if (other == viewer || !other.isAlive() || other.isSpectator()) continue;
            if (other.distanceToSqr(viewer) > MAX_DIST_SQR) continue;
            TARGETS.add(other);
        }
    }

    // =====================================================================
    // RENDU
    // =====================================================================

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;

        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        if (level == null) return;

        // T350-COSMETIC-RELIABLE : repli si la liste n'a pas été remplie (tick sauté, monde rechargé...)
        if (TARGETS.isEmpty()) {
            refillTargets(mc, level);
            if (TARGETS.isEmpty()) return;
        }

        Vec3 cam = event.getCamera().getPosition();
        Matrix4f mat = event.getPoseStack().last().pose();
        Vector3f tmp = new Vector3f();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();

        float time = WeaponWorldVfxManager.smoothTimeSeconds();
        WeaponWorldVfxManager.beginFrame(time);
        // T378-NO-JITTER : l'aura est accrochee a une entite et ses anneaux tournent, donc
        // AUCUN de ses pixels n'occupe la meme position deux images de suite. Les variations
        // qu'emitVoxelCube tire de la position changeaient donc a chaque image. Le defaut
        // etait deja a moitie connu ici : T351-SMOOTH-GLINT a supprime l'alternance de
        // couleur « quand l'anneau tourne », sans voir que la taille et le grain
        // tremblaient pour exactement la meme raison.
        boolean previousFrozen = WeaponWorldVfxManager.beginFrozenDetail();
        try {

            WeaponWorldVfxManager.resetCubeBudget();
            budget = MAX_CUBES_PER_FRAME;
            VertexConsumer add = null;
            VertexConsumer alpha = null;

            // T360-HALO-CONTINU : le style partagé du moteur (seuil d'alpha du reflet d'arme, densité de
            // pixels) pouvait rester « fermé » et culling l'intégralité de l'aura (alpha 150-250). On
            // isole donc complètement la passe cosmétique : halo TOUJOURS dessiné, quoi qu'il arrive.
            int previousAlphaFloor = WeaponWorldVfxManager.getAlphaFloor();
            WeaponWorldVfxManager.beginCosmeticPass();

            try {
                for (int i = 0, n = TARGETS.size(); i < n && budget > 0; i++) {
                    Player p = TARGETS.get(i);
                    if (!p.isAlive() || p.isInvisible()) continue;
                    if (p.distanceToSqr(cam.x, cam.y, cam.z) > MAX_DIST_SQR) continue;

                    int flags = CosmeticAuraClientTicker.getCosmeticFlags(p, level.getGameTime());
                    if (flags == 0) continue;

                    if (add == null) add = buffers.getBuffer(getAdditiveType());
                    if (alpha == null) alpha = buffers.getBuffer(getTranslucentType());

                    // T360-HALO-CONTINU : une erreur sur un joueur ne doit plus vider l'écran de TOUS
                    // les autres halos (l'ancien catch englobant rendait le halo « invisible à vie »).
                    try {
                        renderPlayerAura(p, flags, cam, mat, tmp, add, alpha, time);
                    } catch (RuntimeException ignored) {
                    }
                }
            } catch (RuntimeException ignored) {
                // Jamais de crash pour du cosmétique
            } finally {
                WeaponWorldVfxManager.setAlphaFloor(previousAlphaFloor);
                try {
                    buffers.endBatch(getTranslucentType());
                } catch (RuntimeException ignored) {
                }
                try {
                    buffers.endBatch(getAdditiveType());
                } catch (RuntimeException ignored) {
                }
            }
        } finally {
            WeaponWorldVfxManager.endFrozenDetail(previousFrozen);
        }
    }

    /**
     * T436 : le rendu passe désormais par {@link CosmeticShapes}, c'est-à-dire par les
     * géométries validées sur l'aperçu — une forme propre PAR ornement. Avant, sept
     * cosmétiques différents sortaient du même {@code haloRing} avec un rayon et une
     * couleur changés : c'était un seul objet repeint, et troué.
     */
    private static void renderPlayerAura(Player p, int flags, Vec3 cam, Matrix4f mat, Vector3f tmp,
                                         VertexConsumer add, VertexConsumer alpha, float time) {
        float height = p.getBbHeight();
        float echelle = height / 1.8f;

        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        CosmeticShapes.Brush br = BRUSH;
        br.add = add;
        br.alpha = alpha;
        br.mat = mat;
        br.tmp = tmp;
        br.px = p.getX() - cam.x;
        br.py = p.getY() - cam.y;
        br.pz = p.getZ() - cam.z;
        br.fwdX = -Mth.sin(yaw);
        br.fwdZ = Mth.cos(yaw);
        br.rightX = -br.fwdZ;
        br.rightZ = br.fwdX;
        br.time = time;
        br.budget = budget;

        // --- 1. ORNEMENTS DE TÊTE -------------------------------------------
        // Exclusivité inchangée : #110 prime sur (#097|#098), qui priment sur Roi.
        if ((flags & CosmeticAuraClientTicker.F_HALO_CONTINUOUS) != 0) {
            CosmeticShapes.haloContinu(br, height);
        }
        if ((flags & CosmeticAuraClientTicker.F_COMBO_110_AVATAR) != 0) {
            CosmeticShapes.soleilAvatar(br, height);
        } else if ((flags & CosmeticAuraClientTicker.F_COMBO_098_SWORD_GOD) != 0) {
            CosmeticShapes.epeesDieuLame(br, height);
        } else if ((flags & CosmeticAuraClientTicker.F_COMBO_097_ETERNAL) != 0) {
            CosmeticShapes.chaineEternelle(br, height);
        } else if ((flags & CosmeticAuraClientTicker.F_ENCH_KING) != 0) {
            CosmeticShapes.couronneRoi(br, height);
        }
        if ((flags & CosmeticAuraClientTicker.F_ENCH_MYSTIC_MIND) != 0) {
            CosmeticShapes.espritMystique(br, height);
        }
        if ((flags & CosmeticAuraClientTicker.F_ENCH_RADIANT_AEGIS) != 0) {
            CosmeticShapes.egideRadieuse(br, height);
        }

        // --- 2. ORNEMENTS DE SOL, ET LEUR TRAÎNÉE ----------------------------
        // Les empreintes sont en coordonnées MONDE : elles restent là où le joueur
        // est passé, se rétractent quand il s'arrête, se redéploient quand il repart.
        int solFlags = CosmeticAuraClientTicker.F_COMBO_106_WARLORD
                | CosmeticAuraClientTicker.F_COMBO_062_PLAGUE
                | CosmeticAuraClientTicker.F_ENCH_DREAD_AURA;
        if ((flags & solFlags) != 0) {
            long tick = p.level().getGameTime();
            CosmeticTrail.Pas[] pas = CosmeticTrail.collecte(p, tick, 0f);
            CosmeticTrail.Pas[] relatifs = relatifs(pas, cam);
            double solY = p.getY() - cam.y;
            if ((flags & CosmeticAuraClientTicker.F_COMBO_106_WARLORD) != 0) {
                CosmeticShapes.sceauGuerre(br, relatifs, solY);
            }
            if ((flags & CosmeticAuraClientTicker.F_COMBO_062_PLAGUE) != 0) {
                CosmeticShapes.pesteAmbulante(br, relatifs, solY);
            }
            if ((flags & CosmeticAuraClientTicker.F_ENCH_DREAD_AURA) != 0) {
                CosmeticShapes.auraTerreur(br, relatifs, solY);
            }
        }

        // --- 3. LE NUAGE ET L'ONDE : deux cosmétiques qui n'existaient qu'en particules ---
        // Le coussin est un amorti de saut : il n'existe QUE quand le joueur est en l'air,
        // exactement comme le déclencheur serveur. C'est aussi ce qui garantit qu'il ne
        // coexiste jamais avec l'onde de choc, et donc que le budget mesuré tient.
        if ((flags & CosmeticAuraClientTicker.F_ENCH_CLOUD_CUSHION) != 0 && !p.onGround()) {
            long tick = p.level().getGameTime();
            CosmeticTrail.Pas[] pas = relatifs(CosmeticTrail.collecte(p, tick, 0f), cam);
            CosmeticShapes.coussinNuage(br, pas, p.getY() - cam.y);
        }
        if ((flags & CosmeticAuraClientTicker.F_ENCH_LANDING_WAVE) != 0) {
            long tick = p.level().getGameTime();
            CosmeticTrail.Impact im = CosmeticTrail.impact(p, tick);
            if (im != null) {
                // L'onde est ancrée au point d'impact : le joueur peut s'en éloigner,
                // elle reste là où il a touché le sol. Aucune traînée, par construction.
                float avance = (tick - im.tick) / (float) CosmeticTrail.ONDE_TICKS;
                CosmeticShapes.ondeChoc(br, avance, im.chute,
                        im.x - cam.x, im.y - cam.y, im.z - cam.z);
            }
        }

        // --- 4. AILES (EN DERNIER) -------------------------------------------
        // Volontairement tracées à la fin : ce sont de loin les plus grosses pièces.
        // Si le plafond par frame était atteint, il vaut mieux perdre quelques plumes
        // de bout d'aile qu'un ornement entier.
        // Deux fonctions DISTINCTES : une aile emplumée et une voilure cristalline.
        // Elles n'ont plus une seule ligne de calcul en commun.
        boolean archangel = (flags & CosmeticAuraClientTicker.F_COMBO_101_ARCHANGEL) != 0;
        boolean winged = (flags & CosmeticAuraClientTicker.F_ENCH_WINGED_ENDURANCE) != 0
                && (!p.onGround() || p.isFallFlying());
        if (archangel) {
            CosmeticShapes.aileSeraphin(br, -1, echelle);
            CosmeticShapes.aileSeraphin(br, 1, echelle);
        } else if (winged) {
            CosmeticShapes.voilureGivre(br, -1, echelle);
            CosmeticShapes.voilureGivre(br, 1, echelle);
        }

        budget = br.budget;
    }

    /** Le pinceau est unique et réutilisé : zéro allocation par frame, comme le reste du moteur. */
    private static final CosmeticShapes.Brush BRUSH = new CosmeticShapes.Brush();
    private static CosmeticTrail.Pas[] RELATIFS = new CosmeticTrail.Pas[0];

    /** Les empreintes sont en coordonnées monde ; le rendu travaille en repère caméra. */
    private static CosmeticTrail.Pas[] relatifs(CosmeticTrail.Pas[] pas, Vec3 cam) {
        if (RELATIFS.length < pas.length) {
            RELATIFS = new CosmeticTrail.Pas[pas.length];
            for (int i = 0; i < pas.length; i++) RELATIFS[i] = new CosmeticTrail.Pas();
        }
        CosmeticTrail.Pas[] out = new CosmeticTrail.Pas[pas.length];
        for (int i = 0; i < pas.length; i++) {
            CosmeticTrail.Pas d = RELATIFS[i];
            if (d == null) {
                d = new CosmeticTrail.Pas();
                RELATIFS[i] = d;
            }
            d.x = pas[i].x - cam.x;
            d.y = pas[i].y - cam.y;
            d.z = pas[i].z - cam.z;
            d.age = pas[i].age;
            d.tick = pas[i].tick;
            out[i] = d;
        }
        return out;
    }

    /**
     * T436 : {@code haloRing} et {@code wing} ont été SUPPRIMÉES. C'étaient les deux seules
     * formes du moteur : un anneau de dix points rappelé pour sept cosmétiques différents, et
     * une aile unique partagée par l'Archange et l'Endurance Ailée. Les géométries vivent
     * maintenant dans {@link CosmeticShapes}, une par ornement.
     */
}
