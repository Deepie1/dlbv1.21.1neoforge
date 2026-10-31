package deepluckyblock.procedures;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.FireworkExplosion;
import net.minecraft.world.item.component.Fireworks;

import java.util.ArrayList;
import java.util.List;

/**
 * 🎆 T229 — FEUX D'ARTIFICE DE DEBLOCAGE DE STRUCTURE
 *
 * Consigne : « ajoute des feux d'artifices differents et colores, avec des
 * melanges etc [...] si on debloque une structure avec le lucky block, des
 * fireworks 1-4 sont envoyes depuis les coordonnees du bloc casse. la structure
 * spawn toujours la ou elle devait spawn. attention a bien selectionner les
 * firework dans le theme de la structure. »
 *
 * ⚠️ Ces fusees partent du BLOC CASSE. Elles ne touchent a AUCUN moment la
 *    position de la structure : Structures5Procedure continue de la poser
 *    exactement la ou elle devait aller.
 *
 * FORMAT 1.21.1 (composants, depuis 1.20.5 — l'ancien NBT « Fireworks/Explosions »
 * n'existe plus) :
 *   ItemStack(firework_rocket)
 *     .set(DataComponents.FIREWORKS, new Fireworks(flight, List<FireworkExplosion>))
 *   FireworkExplosion(Shape, IntList colors, IntList fadeColors, hasTrail, hasTwinkle)
 *   Shape ∈ { SMALL_BALL, LARGE_BALL, STAR, CREEPER, BURST }
 *   Les couleurs sont des entiers RGB packes (0xRRGGBB).
 *
 * « Melanges » : chaque fusee empile PLUSIEURS FireworkExplosion dans la meme
 * liste. Elles detonent ensemble et leurs couches se superposent, exactement
 * comme un rocket craft avec plusieurs firework stars.
 */
public class StructureFireworksProcedure {

    /** Nombre de fusees par deblocage : 1 a 4 (consigne). */
    private static final int MIN_ROCKETS = 1;
    private static final int MAX_ROCKETS = 4;

    /** Decalage en ticks entre deux fusees, pour une gerbe et non un paquet. */
    private static final int STAGGER_MIN_TICKS = 3;
    private static final int STAGGER_MAX_TICKS = 11;

    /** Palette + formes d'un theme de structure. */
    private record Theme(String name,
                         int[] primary,     // couleurs de la detonation
                         int[] fade,        // couleurs de fondu
                         FireworkExplosion.Shape[] shapes,
                         boolean trail,
                         boolean twinkle,
                         int flightMin, int flightMax) {}

    // ───────────────────────── THEMES PAR STRUCTURE ─────────────────────────
    // Les IDs suivent GenerateLuckyItemProcedure :
    //   0 Citadelle Sylvestre (§2) · 1 Observatoire Feerique (§b)
    //   2 Guilde du Dragon (§4)    · 3 Cirque Maudit (§d)
    //   4 Ruine d'Epave Fantome (§3) · 5 Everest (§b)
    private static final Theme[] THEMES = {
        // 0 — CITADELLE SYLVESTRE : canopee, seve doree, feuilles.
        new Theme("Citadelle Sylvestre",
            new int[]{0x2E8B22, 0x4CE04C, 0x9ADF5B, 0x1F6B14},
            new int[]{0xF2E96B, 0xFFFFFF, 0xC8F06A},
            new FireworkExplosion.Shape[]{
                FireworkExplosion.Shape.LARGE_BALL,
                FireworkExplosion.Shape.STAR,
                FireworkExplosion.Shape.BURST},
            true, false, 1, 2),

        // 1 — OBSERVATOIRE FEERIQUE : etoiles, poussiere de fee, nebuleuse.
        new Theme("Observatoire Feerique",
            new int[]{0x66FFF2, 0x7FD4FF, 0xFFFFFF, 0xFFB7E8},
            new int[]{0xE0B3FF, 0xFFFFFF, 0x9A7BFF},
            new FireworkExplosion.Shape[]{
                FireworkExplosion.Shape.STAR,
                FireworkExplosion.Shape.SMALL_BALL,
                FireworkExplosion.Shape.STAR},
            true, true, 2, 3),

        // 2 — GUILDE DU DRAGON : souffle de feu, braise, or en fusion.
        new Theme("Guilde du Dragon",
            new int[]{0xB02020, 0xFF6A00, 0xFFC400, 0x7A0A0A},
            new int[]{0x3A0000, 0xFF2D00, 0xFFE08A},
            new FireworkExplosion.Shape[]{
                FireworkExplosion.Shape.BURST,
                FireworkExplosion.Shape.LARGE_BALL,
                FireworkExplosion.Shape.BURST},
            true, true, 1, 2),

        // 3 — CIRQUE MAUDIT : chapiteau, neons, et la tete de creeper pour le
        //     cote « maudit » (seule structure a l'utiliser).
        new Theme("Cirque Maudit",
            new int[]{0xD64BD6, 0x8B2BE2, 0xFFE34D, 0xFF4FA3},
            new int[]{0x1A0022, 0xFF4FA3, 0x33FF8A},
            new FireworkExplosion.Shape[]{
                FireworkExplosion.Shape.CREEPER,
                FireworkExplosion.Shape.BURST,
                FireworkExplosion.Shape.LARGE_BALL},
            false, true, 2, 3),

        // 4 — RUINE D'EPAVE FANTOME : brume, ecume palie, lueur noyee.
        new Theme("Ruine d'Epave Fantome",
            new int[]{0x2F9E9E, 0xBFD9D9, 0xE8F6F6, 0x1C6E6E},
            new int[]{0x12343B, 0x7FA8A8, 0xFFFFFF},
            new FireworkExplosion.Shape[]{
                FireworkExplosion.Shape.SMALL_BALL,
                FireworkExplosion.Shape.BURST,
                FireworkExplosion.Shape.SMALL_BALL},
            true, false, 1, 2),

        // 5 — EVEREST : glace, neige, ciel d'altitude. La plus haute (flight 3).
        new Theme("Everest",
            new int[]{0xFFFFFF, 0xA8E0FF, 0x5FC8E8, 0xDCF2FF},
            new int[]{0xDCF2FF, 0x2E6E8E, 0xFFFFFF},
            new FireworkExplosion.Shape[]{
                FireworkExplosion.Shape.LARGE_BALL,
                FireworkExplosion.Shape.STAR,
                FireworkExplosion.Shape.LARGE_BALL},
            true, true, 2, 3)
    };

    /** Theme de secours : Crimson Lake (systeme separe) ou ID inconnu. */
    private static final Theme CRIMSON = new Theme("Crimson Lake",
        new int[]{0x8C0F1F, 0xD92B3A, 0x4A0008, 0xFF5566},
        new int[]{0x1A0004, 0xFF5566, 0x6E0010},
        new FireworkExplosion.Shape[]{
            FireworkExplosion.Shape.LARGE_BALL,
            FireworkExplosion.Shape.BURST,
            FireworkExplosion.Shape.SMALL_BALL},
        true, true, 1, 2);

    public static Theme themeFor(int structureId) {
        return (structureId >= 0 && structureId < THEMES.length) ? THEMES[structureId] : CRIMSON;
    }

    /**
     * Lance 1 a 4 fusees thematiques depuis les coordonnees du bloc casse.
     *
     * @param level  monde serveur
     * @param x,y,z  position du BLOC CASSE (pas celle de la structure)
     * @param id     identifiant de structure (0..5), -1 pour Crimson Lake
     */
    public static void launch(ServerLevel level, double x, double y, double z, int id) {
        if (level == null) return;
        RandomSource rng = level.random;
        Theme theme = themeFor(id);
        int count = MIN_ROCKETS + rng.nextInt(MAX_ROCKETS - MIN_ROCKETS + 1); // 1..4

        int delay = 0;
        for (int i = 0; i < count; i++) {
            final int shot = i;
            final int when = TestProcedure.currentTick(level) + delay;
            TestProcedure.schedule(level, when, () -> fireOne(level, x, y, z, theme, shot, rng));
            delay += STAGGER_MIN_TICKS + rng.nextInt(STAGGER_MAX_TICKS - STAGGER_MIN_TICKS + 1);
        }
    }

    private static void fireOne(ServerLevel level, double x, double y, double z,
                                Theme theme, int shot, RandomSource rng) {
        try {
            // Leger eparpillement horizontal : une gerbe, pas une colonne.
            double ox = (rng.nextDouble() - 0.5) * 1.8;
            double oz = (rng.nextDouble() - 0.5) * 1.8;

            ItemStack rocket = new ItemStack(Items.FIREWORK_ROCKET);
            List<FireworkExplosion> layers = new ArrayList<>();

            // « Melanges » : 2 ou 3 couches empilees dans la meme fusee.
            int layerCount = 2 + rng.nextInt(2);
            for (int l = 0; l < layerCount; l++) {
                FireworkExplosion.Shape shape = theme.shapes()[rng.nextInt(theme.shapes().length)];
                layers.add(new FireworkExplosion(
                        shape,
                        pickColors(theme.primary(), 2 + rng.nextInt(2), rng), // 2-3 couleurs
                        pickColors(theme.fade(), 1 + rng.nextInt(2), rng),    // 1-2 fondus
                        theme.trail() || rng.nextInt(4) == 0,
                        theme.twinkle() || rng.nextInt(4) == 0));
            }

            int flight = theme.flightMin()
                       + rng.nextInt(Math.max(1, theme.flightMax() - theme.flightMin() + 1));
            rocket.set(DataComponents.FIREWORKS, new Fireworks(flight, layers));

            FireworkRocketEntity entity =
                    new FireworkRocketEntity(level, x + 0.5 + ox, y + 1.0, z + 0.5 + oz, rocket);
            level.addFreshEntity(entity);

            // Un seul coup de sifflet, sur la premiere fusee seulement.
            if (shot == 0) {
                level.playSound(null, x, y, z, SoundEvents.FIREWORK_ROCKET_LAUNCH,
                        SoundSource.AMBIENT, 1.0F, 1.0F);
            }
        } catch (Exception ignored) {
            // Un feu d'artifice ne doit JAMAIS empecher la structure de se poser.
        }
    }

    /** Choisit {@code n} couleurs distinctes si possible, sinon avec repetition. */
    private static IntList pickColors(int[] palette, int n, RandomSource rng) {
        IntArrayList out = new IntArrayList();
        if (palette == null || palette.length == 0) {
            out.add(0xFFFFFF);
            return out;
        }
        n = Math.max(1, Math.min(n, palette.length));
        // Melange partiel de Fisher-Yates sur une copie.
        int[] copy = palette.clone();
        for (int i = copy.length - 1; i > 0; i--) {
            int j = rng.nextInt(i + 1);
            int t = copy[i]; copy[i] = copy[j]; copy[j] = t;
        }
        for (int i = 0; i < n; i++) out.add(copy[i]);
        return out;
    }
}
