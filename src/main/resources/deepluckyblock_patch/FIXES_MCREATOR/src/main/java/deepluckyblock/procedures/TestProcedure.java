package deepluckyblock.procedures;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import deepluckyblock.compat.LuckyToolsCompatibility;
import deepluckyblock.entity.EndKnightEntity;
import deepluckyblock.entity.ShadowMirrorEntity;
import deepluckyblock.entity.SlimeKingEntity;
import deepluckyblock.init.DeepLuckyBlockModEntities;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = "deep_lucky_block")
public class TestProcedure {

    // ACTIVE/DESACTIVE les logs de debug [SLB-DEBUG]. Mettre a false avant publication.
    private static final boolean DEBUG_LOGS = false;

    private static final int ROULETTE_STEPS     = 14;
    // Roulette 2 positive : conserve strictement ses 13 etapes d'origine.
    // Remplace par le systeme de roue (WHEEL_*) : la branche positive n'utilise
    // plus un nombre fixe de crans, elle tourne jusqu'a la case gagnante.
    // Roulette BAD : 37 affichages, soit un tour europeen complet. Chaque spin
    // passe donc visiblement une fois sur le 0 vert avant son resultat final.
    private static final int BAD_ROULETTE_STEPS = 37;
    // ╔══════════════════════════════════════════════════════════════════════╗
    // ║  ☠  TAUX DE LA TÊTE DE MORT  —  RÉGLAGE PRINCIPAL                    ║
    // ╠══════════════════════════════════════════════════════════════════════╣
    // ║                                                                      ║
    // ║  POSITIVE_SKULL_CHANCE = chance, EN POURCENT, qu'une roulette         ║
    // ║  POSITIVE se termine sur une tête de mort (☠) et bascule vers un      ║
    // ║  événement GenerateBad.                                              ║
    // ║                                                                      ║
    // ║  ⚠ ATTENTION — LE TAUX RÉEL EST LA MOITIÉ DE CETTE VALEUR.           ║
    // ║                                                                      ║
    // ║  La tête de mort n'est tirée QUE si la roulette est déjà positive.    ║
    // ║  Or `mainPos` est un 50/50 strict (ligne ~210 : rng.nextBoolean()).   ║
    // ║  Deux conditions supplémentaires l'excluent encore :                  ║
    // ║      • forceEternalPayload (Idol ETERNAL) → jamais de tête de mort    ║
    // ║      • le bloc doit donc passer 50 % AVANT même de tenter le tirage   ║
    // ║                                                                      ║
    // ║  TAUX RÉEL ≈ POSITIVE_SKULL_CHANCE / 2   (sur TOUS les lucky blocks)  ║
    // ║                                                                      ║
    // ║  ┌────────────────────┬──────────────────┬────────────────────────┐  ║
    // ║  │ Valeur ci-dessous  │ Sur roulette +   │ Sur TOUS les blocs     │  ║
    // ║  ├────────────────────┼──────────────────┼────────────────────────┤  ║
    // ║  │   2  (par défaut)  │       2 %        │  1 %   = 1 bloc / 100  │  ║
    // ║  │   5                │       5 %        │  2,5 % = 1 bloc /  40  │  ║
    // ║  │  10                │      10 %        │  5 %   = 1 bloc /  20  │  ║
    // ║  │  20                │      20 %        │ 10 %   = 1 bloc /  10  │  ║
    // ║  │  40                │      40 %        │ 20 %   = 1 bloc /   5  │  ║
    // ║  │ 100                │     100 %        │ 50 %   = 1 bloc /   2  │  ║
    // ║  └────────────────────┴──────────────────┴────────────────────────┘  ║
    // ║                                                                      ║
    // ║  POURQUOI TU NE LA VOIS JAMAIS :                                     ║
    // ║  avec la valeur 2, il faut ouvrir ~100 lucky blocks en moyenne pour   ║
    // ║  en voir UNE. Sur 50 blocs, tu as ~39 % de chance de n'en voir aucune.║
    // ║                                                                      ║
    // ║  POUR CALIBRER : mets 100 temporairement, vérifie que l'animation et  ║
    // ║  l'événement BAD se déclenchent bien, puis redescends à la valeur     ║
    // ║  voulue (10 ou 20 donnent un rythme visible sans être envahissant).   ║
    // ║                                                                      ║
    // ╚══════════════════════════════════════════════════════════════════════╝
    public static int POSITIVE_SKULL_CHANCE = 2;

    /** true = journalise chaque tirage de roulette dans la console (diagnostic). */
    public static boolean SKULL_DEBUG = false;

    private static final int PAUSE_BETWEEN      = 4;
    private static final int PAUSE_BEFORE_SPAWN = 10;
    // Delai SUPPLEMENTAIRE uniquement quand la roulette positive tombe sur le skull.
    // Permet de bien voir la tete de mort avant que l'evenement BAD parte.
    private static final int PAUSE_AFTER_SKULL  = 15;
    private static final int HOLO_LIFETIME      = 60;
    private static final int GLOW_DURATION      = 100;
    private static final int BIG_LUCK_THRESHOLD = 70;


    private static final String PIXEL_GREEN = "§a█";
    private static final String PIXEL_RED   = "§c█";
    // Tete de mort : caractere texte simple, sans bordure ni police custom.
    // Toujours BLANC, quelle que soit la case de la roulette.
    private static final String SKULL_CHAR = "\u2620";

    /** Tete de mort en texte, toujours blanche et grasse. Aucune bordure. */
    private static Component skullDisplay() {
        return Component.literal(SKULL_CHAR)
                .withStyle(ChatFormatting.WHITE, ChatFormatting.BOLD);
    }

    // Glyphes bicolores dedies a la roulette BAD : chaque couleur est posee
    // sur un fond carre blanc tres fin. La texture conserve les vraies couleurs
    // et reste lisible sur tous les blocs, sans crochets ni parentheses.
    private static final String BACKED_BLACK_GLYPH = "\uE000";
    private static final String BACKED_RED_GLYPH   = "\uE001";
    private static final String BACKED_GREEN_GLYPH = "\uE002";
    private static final ResourceLocation ROULETTE_FONT =
            ResourceLocation.tryParse("deep_lucky_block:roulette");

    // Ordre physique officiel d'une roulette europeenne simple-zero.
    // 37 cases : 18 rouges, 18 noires, un seul zero vert.
    private static final int[] EUROPEAN_WHEEL = {
        0,32,15,19,4,21,2,25,17,34,6,27,13,36,11,30,8,23,10,
        5,24,16,33,1,20,14,31,9,22,18,29,7,28,12,35,3,26
    };

    private record BadRouletteSpin(int wheelIndex, int number,
                                   GenerateBadProcedure.BadEventCategory category) {}

    private static boolean isRouletteRed(int number) {
        return switch (number) {
            case 1,3,5,7,9,12,14,16,18,19,21,23,25,27,30,32,34,36 -> true;
            default -> false;
        };
    }

    private static GenerateBadProcedure.BadEventCategory rouletteCategory(int number) {
        if (number == 0) return GenerateBadProcedure.BadEventCategory.GREEN;
        return isRouletteRed(number)
                ? GenerateBadProcedure.BadEventCategory.RED
                : GenerateBadProcedure.BadEventCategory.BLACK;
    }

    private static BadRouletteSpin spinBadRoulette(RandomSource random) {
        // Probabilites custom demandees : 1/20 GREEN, puis les 19/20 restants
        // repartis egalement entre les 18 poches rouges et les 18 noires.
        // L'ordre visuel EUROPEAN_WHEEL et l'animation restent strictement identiques.
        int wheelIndex = random.nextInt(20) == 0
                ? 0
                : 1 + random.nextInt(EUROPEAN_WHEEL.length - 1);
        int number = EUROPEAN_WHEEL[wheelIndex];
        return new BadRouletteSpin(wheelIndex, number, rouletteCategory(number));
    }

    /** Tirage final force dans une couleur, utilise uniquement par la malediction Lucky Idol. */
    private static BadRouletteSpin spinBadRouletteForCategory(
            GenerateBadProcedure.BadEventCategory category, RandomSource random) {
        int[] matching = new int[EUROPEAN_WHEEL.length];
        int count = 0;
        for (int wheelIndex = 0; wheelIndex < EUROPEAN_WHEEL.length; wheelIndex++) {
            int number = EUROPEAN_WHEEL[wheelIndex];
            if (rouletteCategory(number) == category) {
                matching[count++] = wheelIndex;
            }
        }
        if (count <= 0) return spinBadRoulette(random);
        int wheelIndex = matching[random.nextInt(count)];
        int number = EUROPEAN_WHEEL[wheelIndex];
        return new BadRouletteSpin(wheelIndex, number, category);
    }

    private static BadRouletteSpin[] buildBadRouletteAnimation(BadRouletteSpin result,
                                                                RandomSource random) {
        int totalSteps = BAD_ROULETTE_STEPS;
        BadRouletteSpin[] animation = new BadRouletteSpin[totalSteps];
        int direction = random.nextBoolean() ? 1 : -1;

        // Parcours REEL des poches physiques de la roue europeenne. Les 37
        // affichages couvrent exactement un tour complet et terminent sur le
        // resultat tire. Le 0 vert reste visible une fois pendant le passage.
        // Seul le tirage final est pondere a 1/20 GREEN ; le visuel ne change pas.
        for (int step = 0; step < totalSteps; step++) {
            int pocketsBeforeResult = totalSteps - 1 - step;
            int wheelIndex = Math.floorMod(
                    result.wheelIndex() - direction * pocketsBeforeResult,
                    EUROPEAN_WHEEL.length);
            int number = EUROPEAN_WHEEL[wheelIndex];
            animation[step] = new BadRouletteSpin(
                    wheelIndex, number, rouletteCategory(number));
        }
        animation[totalSteps - 1] = result;
        return animation;
    }

    private static Component backedRouletteSquare(
            GenerateBadProcedure.BadEventCategory category) {
        String glyph = switch (category) {
            case BLACK -> BACKED_BLACK_GLYPH;
            case RED -> BACKED_RED_GLYPH;
            case GREEN -> BACKED_GREEN_GLYPH;
        };
        return Component.literal(glyph)
                // Le blanc ne recolore pas le centre de la texture : les deux
                // vraies couleurs du PNG sont conservees (bord blanc + centre).
                .withStyle(style -> style.withFont(ROULETTE_FONT).withColor(0xFFFFFF));
    }

    private static Component roulettePixel(GenerateBadProcedure.BadEventCategory category) {
        return backedRouletteSquare(category);
    }

    private static Component rouletteDisplay(int number) {
        // Ligne du bas : uniquement le chiffre. Aucun carre, aucune pastille,
        // aucun prefixe. Police Minecraft par defaut, blanc et non gras.
        return Component.literal(Integer.toString(number))
                .withStyle(style -> style
                        .withFont(Style.DEFAULT_FONT)
                        .withColor(ChatFormatting.WHITE)
                        .withBold(false));
    }

    private static final CopyOnWriteArrayList<ScheduledTask> TASK_QUEUE =
            new CopyOnWriteArrayList<>();

    // =========================================================================
    // ENTRY POINT (comportement normal, sans bonus luck)
    // =========================================================================
    public static void execute(LevelAccessor world, double x, double y, double z) {
        // Injection externe : aucun changement n'est necessaire dans le fichier
        // DeepLuckyBlockBlock regenere par MCreator.
        if (world instanceof Level compatLevel
                && LuckyToolsCompatibility.consumeAndExecute(compatLevel, x, y, z)) return;
        if (!(world instanceof ServerLevel level)) return;
        deepluckyblock.util.DeepEnchant.rememberServerAccess(level.registryAccess());

        RandomSource rng      = RandomSource.create();
        boolean      mainPos  = deepluckyblock.procedures.GenerateLuckyItemProcedure.DEBUG_STRUCTURE_LOOP || rollPercent(rng, 50.0); // Système UNIFIÉ : branche via rollPercent (50 % au bonus 0), identique à l'infusé
        // NOUVEAU MODÈLE : la roue EST le tirage (1 crâne + 9 couleurs à luck 0).
        WheelResolution wheelRes = null;
        int          mainLuck;
        if (mainPos) {
            wheelRes = resolvePositiveWheel(0, rng);
            // Si la roue tombe sur une couleur, c'est le résultat définitif.
            // Si elle tombe sur le crâne, on affiche en roulette 1 la luck
            // « entrevue » avant la bascule BAD.
            mainLuck = wheelRes.skull() ? rollQualityLuck(0, rng) : wheelRes.resolvedLuck();
        } else {
            mainLuck = 1 + rng.nextInt(50);
        }

        double bx = Math.floor(x) + 0.5;
        double by = Math.floor(y);
        double bz = Math.floor(z) + 0.5;

        ArmorStand pixelHolo = spawnHolo(level, bx, by + 0.4, bz, PIXEL_GREEN);
        ArmorStand textHolo  = spawnHolo(level, bx, by - 0.3, bz, "§e§l⚡ Launching...");

        if (pixelHolo == null || textHolo == null) {
            boolean skullHit = mainPos && wheelRes != null && wheelRes.skull();
            boolean effPos = mainPos && !skullHit;
            GenerateBadProcedure.BadEventCategory badCategory = effPos
                    ? null : spinBadRoulette(rng).category();
            generateResult(level, bx, by, bz, effPos,
                    skullHit ? 1 + rng.nextInt(50) : mainLuck,
                    RandomSource.create(), badCategory, null, null);
            return;
        }

        buildSequence(level, pixelHolo, textHolo,
                bx, by, bz, mainPos, mainLuck, rng, null, false, 0, wheelRes);
    }

    // =========================================================================
    // ENTRY POINT AVEC LUCK BONUS (Deep's Lucky Block)
    // =========================================================================
    // ⛔ NOUVEAU (publication) : tirage avec luck infusée POSITIVE.
    //    Avant : +50 -> plancher 51-100 GARANTI (trop cheat : jamais de résultat
    //    bas, la grande majorité des blocs sortaient très haut).
    //    Maintenant : tirage sur 0-100 avec une courbe biaisée vers le haut.
    //    L'ancien calcul (minRoll/maxRoll) est conservé juste en dessous et
    //    reste utilisé pour les malédictions (bonus négatif).
    //    Remettre LUCK_BONUS_POSITIVE_CURVE sur false pour l'ancien plancher.
    private static final boolean LUCK_BONUS_POSITIVE_CURVE = true;
    private static final double LUCK_BONUS_BASE_EXPONENT = 1.8;
    // ⛔ MODIF (publication) : plancher de l'exposant abaissé de 0.4 à 0.05.
    //    Avant : à partir de +78, l'exposant était bloqué à 0.4 -> plus AUCUNE
    //    différence entre 78 et 100. Maintenant la courbe 1.8*(1-bonus/100)
    //    continue de descendre jusqu'à ~+97 (seul le très haut de la courbe,
    //    97-100, converge car c'est le sommet de l'échelle 0-100).
    //    Jamais 0 : un exposant 0 donnerait pow(u,0)=1 -> luck 101 -> DOOMED.
    private static final double LUCK_BONUS_MIN_EXPONENT = 0.03;

    // ⛔ NOUVEAU (publication) : modèle PROGRESSIF.
    //    La probabilité de tomber sur GenerateLuckyItem (branche positive) évolue
    //    linéairement : 0 % à -75 → 50 % à 0 → 100 % à +75.
    //    À +75 et plus : bad skippé, qualité tirée sur 0-100 (courbe biaisée).
    //    À -75 et moins : 100 % GenerateBad.
    //    Remettre sur false pour réactiver l'ancien calcul (plancher +50).
    private static final boolean LUCK_PROGRESSIVE_SCALE = true;

    // ⛔ NOUVEAU (publication) : courbe en CLOCHE Beta(alpha, 3) pour la QUALITÉ
    //    des récompenses positives. Remplace l'ancienne courbe puissance
    //    (toujours croissante -> à +100 ETERNAL devenait « le plus fréquent »).
    //    La cloche monte PUIS redescend : son sommet voyage de USED (bonus 0) à
    //    LEGENDARY (bonus 100) en passant par chaque grade dans l'ordre, et
    //    ETERNAL reste toujours rare (~0,2 % à +100). Mode linéaire : 0 → 0.92.
    //    Remettre LUCK_BELL_CURVE sur false pour l'ancienne courbe en exposant.
    private static final boolean LUCK_BELL_CURVE = true;
    private static final double LUCK_BELL_MODE_MIN = 0.30; // bonus 0 -> COMMON
    private static final double LUCK_BELL_MODE_MAX = 0.92; // bonus 100 -> LEGENDARY

    /** Sommet de la cloche : 0.30 (bonus 0 -> COMMON) -> 0.92 (bonus 100), linéaire. */
    /** Sommet de la cloche, LISSE sur tout -100..+100 :
     *  -100 → 0.05 (≈USED) · 0 → 0.30 (COMMON) · +100 → 0.92 (LEGENDARY). */
    private static double bellModeForBonus(int bonus) {
        if (bonus < 0) {
            return LUCK_BELL_MODE_MIN + bonus * (LUCK_BELL_MODE_MIN - 0.05) / 100.0;
        }
        return LUCK_BELL_MODE_MIN + (LUCK_BELL_MODE_MAX - LUCK_BELL_MODE_MIN) * bonus / 100.0;
    }

    /** alpha = (1+mode)/(1-mode) : 1 (bonus 0) -> 24 (bonus 100). */
    private static double bellAlphaForBonus(int bonus) {
        double mode = bellModeForBonus(bonus);
        return (1.0 + mode) / Math.max(1e-6, 1.0 - mode);
    }

    /** CDF de Beta(a, 3) pour a entier (forme close). */
    private static double betaCdfInt(double x, int a) {
        if (x <= 0.0) return 0.0;
        if (x >= 1.0) return 1.0;
        double coef = a * (a + 1.0) * (a + 2.0) / 2.0;
        return coef * (Math.pow(x, a) / a
                - 2.0 * Math.pow(x, a + 1) / (a + 1.0)
                + Math.pow(x, a + 2) / (a + 2.0));
    }

    /** CDF de Beta(alpha, 3) pour alpha continu (mélange floor/ceil). */
    private static double betaCdf(double x, double alpha) {
        int a0 = (int) Math.floor(alpha);
        double frac = alpha - a0;
        return (1.0 - frac) * betaCdfInt(x, a0) + frac * betaCdfInt(x, a0 + 1);
    }

    /** Tire une luck 0..100 selon la cloche Beta(alpha, 3). */
    private static int sampleBellLuck(int luckBonus, RandomSource random) {
        double alpha = bellAlphaForBonus(luckBonus);
        double total = 0.0;
        double[] cum = new double[101];
        for (int v = 0; v <= 100; v++) {
            double p = betaCdf((v + 1) / 101.0, alpha) - betaCdf(v / 101.0, alpha);
            total += p;
            cum[v] = total;
        }
        double r = random.nextDouble() * total;
        for (int v = 0; v <= 100; v++) {
            if (r < cum[v]) return v;
        }
        return 100;
    }

    /** Coefficient binomial (double, n <= 32 sans souci de précision). */
    private static double binomial(int n, int k) {
        if (k < 0 || k > n) return 0.0;
        k = Math.min(k, n - k);
        double res = 1.0;
        for (int i = 1; i <= k; i++) {
            res = res * (n - k + i) / i;
        }
        return res;
    }

    /** CDF de Beta(a, b) pour a et b entiers (somme finie). */
    private static double betaCdfIntAB(double x, int a, int b) {
        if (x <= 0.0) return 0.0;
        if (x >= 1.0) return 1.0;
        int n = a + b - 1;
        double sum = 0.0;
        for (int j = a; j <= n; j++) {
            sum += binomial(n, j) * Math.pow(x, j) * Math.pow(1.0 - x, n - j);
        }
        return sum;
    }

    // ⛔ NOUVEAU (publication) : bloc NORMAL (bonus 0) — cloche Beta(4,8) centrée
    //    sur COMMON (mode ~30). La MAJORITÉ des picks est donc le grade COMMUN :
    //    USED ~4 % · WORN ~24 % · COMMON ~31 % · UNCOMMON ~23 % · UNUSUAL ~12 %
    //    · REMARKABLE ~5 % · RARE ~1,3 % · au-delà rare.
    // ⛔ NOUVEAU (publication) : bloc NORMAL (bonus 0) — cloche Beta(4,8) centrée
    //    sur COMMON (majorité), + queue de sommet 6 % pour DIFFUSER les hauts
    //    rangs : à luck 0, ETERNAL ~0,9 % · MASTERWORK ~0,9 % · RELIC ~0,6 % ·
    //    MYTHIC ~1,2 % · LEGENDARY ~1,5 % · EXCEPTIONAL ~0,9 %.
    private static final double NATURAL_TOP_TAIL = 0.06;

    private static int sampleNaturalLuck(RandomSource random) {
        // Queue de sommet : rangs EXCEPTIONAL..ETERNAL (poids fixes).
        if (random.nextDouble() < NATURAL_TOP_TAIL) {
            double r = random.nextDouble();
            if (r < 0.15) return randomInTier(QualityTier.EXCEPTIONAL, random); // 0,9 %
            if (r < 0.40) return randomInTier(QualityTier.LEGENDARY, random);   // 1,5 %
            if (r < 0.60) return randomInTier(QualityTier.MYTHIC, random);      // 1,2 %
            if (r < 0.70) return randomInTier(QualityTier.RELIC, random);       // 0,6 %
            if (r < 0.85) return randomInTier(QualityTier.MASTERWORK, random);  // 0,9 %
            return 100;                                                         // ETERNAL 0,9 %
        }
        // Cloche Beta(4,8) centrée sur COMMON.
        final int A = 4, B = 8;
        double total = 0.0;
        double[] cum = new double[101];
        for (int v = 0; v <= 100; v++) {
            double p = betaCdfIntAB((v + 1) / 101.0, A, B) - betaCdfIntAB(v / 101.0, A, B);
            total += p;
            cum[v] = total;
        }
        double r = random.nextDouble() * total;
        for (int v = 0; v <= 100; v++) {
            if (r < cum[v]) return v;
        }
        return 100;
    }

    /** Luck aléatoire uniforme dans le palier d'un grade. */
    private static int randomInTier(QualityTier t, RandomSource random) {
        return t.minLuck + random.nextInt(t.maxLuck - t.minLuck + 1);
    }

    // ⛔ NOUVEAU (publication) : segment 80-100 — le CENTRE reste sur
    //    EXCEPTIONAL-LEGENDARY, et les ailes montent petit à petit jusqu'au
    //    +100 (max absolu) : ETERNAL 5 % · MASTERWORK 8 % · RELIC 9,5 % ·
    //    MYTHIC 20,5 % · LEGENDARY 22,5 % · EXCEPTIONAL 22,5 % · EPIC 6 % ·
    //    OUTSTANDING 6 %.
    //    NB : RELIC garde ~9,5 % au +100 (continuité des 9,41 % de +90) — il
    //    n'est PAS supprimé. MASTERWORK passe sous RELIC : la queue haute doit
    //    rester strictement décroissante (MYTHIC ≥ RELIC ≥ MASTERWORK ≥
    //    ETERNAL), sinon on recrée un creux/bosse entre grades.
    //    Remettre LUCK_HIGH_SEGMENT sur false pour garder la cloche jusqu'à 100.
    private static final boolean LUCK_HIGH_SEGMENT = true;
    private static final int HIGH_SEGMENT_START = 80;

    /** Les 16 grades positifs, du pire au meilleur (ordre du QualityTier). */
    private static final QualityTier[] POSITIVE_TIERS = {
        QualityTier.USED, QualityTier.WORN, QualityTier.COMMON, QualityTier.UNCOMMON,
        QualityTier.UNUSUAL, QualityTier.REMARKABLE, QualityTier.OUTSTANDING,
        QualityTier.RARE, QualityTier.EPIC, QualityTier.EXCEPTIONAL, QualityTier.LEGENDARY,
        QualityTier.MYTHIC,
        QualityTier.RELIC, QualityTier.MASTERWORK, QualityTier.ETERNAL
    };

    /** Répartition des grades au début du segment (+80). */
    private static final double[] TIER_PROB_AT_80 = {
        0.000, 0.000, 0.000, 0.000, 0.000, 0.000, 0.074, 0.091,
        0.111, 0.241, 0.232, 0.111, 0.065, 0.056, 0.018
    };

    /** Répartition des grades au max absolu (+100). Queue strictement
     *  décroissante : MYTHIC 20,5 % ≥ RELIC 9,5 % ≥ MASTERWORK 8 % ≥ ETERNAL 5 %. */
    private static final double[] TIER_PROB_AT_100 = {
        0.000, 0.000, 0.000, 0.000, 0.000, 0.000, 0.057, 0.057,
        0.057, 0.212, 0.212, 0.193, 0.090, 0.075, 0.047
    };

    /** Interpole TIER_PROB_AT_80 -> TIER_PROB_AT_100, tire un grade puis une luck dans ce grade. */
    private static int sampleHighTierLuck(int luckBonus, RandomSource random) {
        double w = (luckBonus - HIGH_SEGMENT_START) / 20.0;
        double total = 0.0;
        double[] cum = new double[POSITIVE_TIERS.length];
        for (int i = 0; i < POSITIVE_TIERS.length; i++) {
            double p = (1.0 - w) * TIER_PROB_AT_80[i] + w * TIER_PROB_AT_100[i];
            total += p;
            cum[i] = total;
        }
        double r = random.nextDouble() * total;
        for (int i = 0; i < POSITIVE_TIERS.length; i++) {
            if (r < cum[i]) {
                QualityTier t = POSITIVE_TIERS[i];
                return t.minLuck + random.nextInt(t.maxLuck - t.minLuck + 1);
            }
        }
        return 100;
    }

    // ⛔ NOUVEAU (publication) : COURBE UNIQUE ET LISSE de -100 à +100.
    //    Plus aucun morceau/saut : une seule cloche Beta(a, 8) dont le sommet
    //    glisse (USED → ... → LEGENDARY) + une « queue de sommet » qui grandit
    //    progressivement (6 % à 0 → 100 % à +100). À +100 : ETERNAL 5 % ·
    //    MASTERWORK 8 % · RELIC 9,5 % · MYTHIC 20,5 % ·
    //    LEGENDARY/EXCEPTIONAL 22,5 % · EPIC/OUTSTANDING 6 %. À 0 : COMMON ~29 % · ETERNAL ~0,9 %.
    //    Les anciens sampleurs (sampleNaturalLuck, sampleHighTierLuck,
    //    sampleBellLuck) sont conservés ci-dessus mais plus utilisés.

    // ⛔ NOUVEAU (publication) : COURBE LOGIQUE, SANS CREUX NI BOSSE.
    //    Système de KEYFRAMES : chaque palier de bonus définit une distribution
    //    des 15 grades en cloche asymétrique (Laplace) — strictement décroissante
    //    des deux côtés du pic. Le pic glisse de grade en grade, DANS L'ORDRE :
    //    USED(-100) → WORN(-80) → COMMON(-31) → UNCOMMON(+8) → UNUSUAL(+28)
    //    → REMARKABLE(+46) → OUTSTANDING(+69) → RARE(+78) → EPIC(+82)
    //    → EXCEPTIONAL(+85) → cible +100 : OUTSTANDING/EPIC 6 % ·
    //    EXCEPTIONAL/LEGENDARY 22,5 % · MYTHIC 20,5 % · RELIC 9,5 % ·
    //    MASTERWORK 8 % · ETERNAL 5 %. Queue STRICTEMENT décroissante après le
    //    pic (MYTHIC ≥ RELIC ≥ MASTERWORK ≥ ETERNAL) : RELIC n'est jamais
    //    supprimé ni doublé par MASTERWORK (vérifié sur -100..+100 par
    //    audit_curve.py : aucun creux, aucune bosse, aucun trou interne).
    //    T220 : table exacte du JSON utilisateur. À luck 0 :
    //    LEGENDARY = 0.8319916800831992 %, sans arrondi destructeur.
    private static final int[] KEY_BONUSES = {-100, -50, 0, 20, 40, 55, 66, 74, 78, 82, 90, 100};

    private static final double[][] KEYFRAMES = {
        {0.6511030220604411, 0.250735014700294, 0.019680393607872156, 0.01611032220644413, 0.013180263605272106, 0.010800216004320087, 0.0072301446028920574, 0.0065501310026200524, 0.005920118402368047, 0.004850097001940039, 0.0039700794015880315, 0.003250065001300026, 0.0026600532010640212, 0.0021800436008720175, 0.0017800356007120141},
        {0.39552, 0.43624, 0.1079, 0.0574, 0.00099, 0.0008799999999999999, 0.00055, 0.00029, 0.00023, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0},
        {0.05138948610513896, 0.13096869031309688, 0.24154758452415478, 0.20392796072039282, 0.14201857981420188, 0.1088889111108889, 0.05800941990580095, 0.022679773202267977, 0.01052989470105299, 0.00941990580094199, 0.008319916800831993, 0.0050099499005009955, 0.0038999610003899966, 0.0027999720002799978, 0.0005899941000589994},
        {0.018660186601866025, 0.06866068660686608, 0.16301163011630118, 0.20608206082060826, 0.1657916579165792, 0.12522125221252214, 0.11136111361113613, 0.06497064970649707, 0.038790387903879044, 0.014630146301463018, 0.00859008590085901, 0.005570055700557007, 0.003560035600356004, 0.0025500255002550033, 0.0025500255002550033},
        {0.0, 0.039710794215884325, 0.0871217424348487, 0.16314326286525732, 0.20342406848136962, 0.15271305426108525, 0.13038260765215307, 0.09010180203604072, 0.04842096841936839, 0.030560611212224247, 0.021630432608652172, 0.01121022420448409, 0.008180163603272065, 0.006700134002680055, 0.006700134002680055},
        {0.0, 0.0, 0.05136, 0.10369, 0.18273, 0.19923, 0.15756, 0.11327, 0.08929999999999999, 0.04196, 0.021419999999999998, 0.01191, 0.01012, 0.00857, 0.00888},
        {0.0, 0.0, 0.016197665171719198, 0.03771899264083841, 0.1377394511415785, 0.18847712382706816, 0.21493825097845015, 0.12097391645684691, 0.09354843178067974, 0.05922908399386828, 0.04848547572424643, 0.03464639620831045, 0.0190418174659083, 0.017254360435721466, 0.011749034174764063},
        {0.0, 0.0, 0.0220993544540212, 0.05668849820047918, 0.12233820296714702, 0.13783824519622423, 0.15808692673488894, 0.17306771265620907, 0.11824039909118078, 0.08296642325322827, 0.0502139058820851, 0.032235415834631236, 0.01742503431074771, 0.014322689030863414, 0.014477192388293921},
        {0.0, 0.0, 0.03371325045879728, 0.046605611422900955, 0.0881408239932871, 0.11831542030163764, 0.13837231493280241, 0.1582108204213107, 0.11641415735408567, 0.0938491218710215, 0.07526357982979627, 0.055195326268448995, 0.03169461707934032, 0.026008397638297084, 0.018216558428273792},
        {0.0, 0.0, 0.03994960050399496, 0.03556964430355696, 0.06246937530624693, 0.0964790352096479, 0.12722872771272287, 0.15969840301596983, 0.13084869151308487, 0.11087889121108788, 0.0892991070089299, 0.06945930540694592, 0.034469655303446964, 0.023219767802321975, 0.02042979570204298},
        {0.0, 0.0, 0.0, 0.011419999999999996, 0.020089999999999993, 0.08985999999999998, 0.14489999999999997, 0.18499999999999994, 0.18499999999999994, 0.12406999999999996, 0.08839999999999998, 0.06759999999999998, 0.03506999999999999, 0.025279999999999993, 0.023309999999999994},
        {0.0, 0.0, 0.0, 0.04018040180401804, 0.05590055900559006, 0.10794107941079412, 0.14256142561425617, 0.14571145711457115, 0.13785137851378515, 0.0999509995099951, 0.0842508425084251, 0.06693066930669307, 0.04424044240442405, 0.041150411504115046, 0.033330333303333036}
    };

    /** T215 : table exhaustive de toutes les lucks craftables (-100..+100).
     *
     *  ⛔ T222 : CETTE TABLE AVAIT ETE PERDUE EN T216 (remplacee par un calcul
     *     a la volee). Elle est restauree ici, alimentee par les KEYFRAMES
     *     EXACTES du JSON (T220) : les 201 lucks sont interpolees UNE SEULE
     *     FOIS au chargement de la classe, bornees a zero et renormalisees a
     *     1.0, puis simplement lues a chaque tirage. */
    private static final double[][] TIER_PROB_BY_BONUS = buildTierProbabilityTable();

    private static double[][] buildTierProbabilityTable() {
        double[][] table = new double[201][POSITIVE_TIERS.length];
        for (int bonus = -100; bonus <= 100; bonus++) {
            int hi = 1;
            while (hi < KEY_BONUSES.length && bonus > KEY_BONUSES[hi]) hi++;
            if (hi >= KEY_BONUSES.length) hi = KEY_BONUSES.length - 1;
            int lo = Math.max(0, hi - 1);
            double t = KEY_BONUSES[hi] == KEY_BONUSES[lo] ? 0.0
                    : (bonus - KEY_BONUSES[lo]) / (double) (KEY_BONUSES[hi] - KEY_BONUSES[lo]);
            double sum = 0.0;
            for (int tier = 0; tier < POSITIVE_TIERS.length; tier++) {
                double value = (1.0 - t) * KEYFRAMES[lo][tier] + t * KEYFRAMES[hi][tier];
                table[bonus + 100][tier] = Math.max(0.0, value);
                sum += table[bonus + 100][tier];
            }
            if (sum > 0.0) for (int tier = 0; tier < POSITIVE_TIERS.length; tier++)
                table[bonus + 100][tier] /= sum;
        }
        return table;
    }

    /** Probabilité d'un grade pour un bonus : lecture directe de la table T215. */
    /**
     * ⛔ T231 — VECTEUR DE PROBABILITES CALCULE A LA DEMANDE.
     *
     * Preuve en jeu (/dlb w 0 100, deux fois) : Used 39 % / Worn 18 % /
     * Common 15 %, puis Used 38 % / Worn 25 % / Common 9 %. Moyenne 38,5 /
     * 21,5 / 12 / 8,5 = exactement les quotas 4/2/1/1 appliques dans l'ordre
     * NATUREL de POSITIVE_TIERS (Used, Worn, Common, Uncommon), et non dans
     * l'ordre des probabilites. Autrement dit le comparateur voyait toutes les
     * probabilites EGALES et retombait sur son departage par index.
     *
     * La seule entree du comparateur etait la table statique TIER_PROB_BY_BONUS.
     * On cesse d'en dependre : les 15 probabilites sont desormais interpolees
     * ici, a chaque appel, directement depuis KEYFRAMES. C'est 15 multiplications,
     * et cela supprime toute une classe de pannes (ordre d'initialisation des
     * champs statiques, table partiellement remplie, etc.).
     */
    /** T231 : accesseur lecture seule pour la commande de diagnostic. */
    public static QualityTier[] POSITIVE_TIERS_VIEW() { return POSITIVE_TIERS.clone(); }

    public static double[] tierProbsAt(int bonus) {
        int b = Mth.clamp(bonus, -100, 100);
        int hi = 1;
        while (hi < KEY_BONUSES.length && b > KEY_BONUSES[hi]) hi++;
        if (hi >= KEY_BONUSES.length) hi = KEY_BONUSES.length - 1;
        int lo = Math.max(0, hi - 1);
        double t = (KEY_BONUSES[hi] == KEY_BONUSES[lo]) ? 0.0
                 : (b - KEY_BONUSES[lo]) / (double) (KEY_BONUSES[hi] - KEY_BONUSES[lo]);
        double[] out = new double[POSITIVE_TIERS.length];
        double sum = 0.0;
        for (int i = 0; i < out.length; i++) {
            out[i] = Math.max(0.0, (1.0 - t) * KEYFRAMES[lo][i] + t * KEYFRAMES[hi][i]);
            sum += out[i];
        }
        if (sum > 0.0) for (int i = 0; i < out.length; i++) out[i] /= sum;
        return out;
    }

    private static double keyframeTierProb(int bonus, int tierIdx) {
        // T231 : filet de securite — si la table statique est vide ou degeneree
        // (toutes les valeurs d'une ligne identiques), on recalcule a la volee.
        double[][] tab = TIER_PROB_BY_BONUS;
        int b = Mth.clamp(bonus, -100, 100);
        if (tab == null || tab.length <= b + 100 || tab[b + 100] == null) return tierProbsAt(bonus)[tierIdx];
        double[] row = tab[b + 100];
        double mn = row[0], mx = row[0];
        for (double v : row) { if (v < mn) mn = v; if (v > mx) mx = v; }
        if (mx - mn < 1e-12) return tierProbsAt(bonus)[tierIdx]; // ligne degeneree
        return row[tierIdx];
    }

    /** Tire un index de grade (0..14) selon la courbe keyframes du bonus. */
    /** ⚠️ T228 : plus appele par la roue (remplacee par la composition par
     *  quotas). Conserve comme reference du tirage independant d'origine. */
    @SuppressWarnings("unused")
    private static int sampleKeyframeTierIndex(int luckBonus, RandomSource random) {
        double total = 0.0;
        double[] cum = new double[POSITIVE_TIERS.length];
        for (int i = 0; i < POSITIVE_TIERS.length; i++) {
            total += keyframeTierProb(luckBonus, i);
            cum[i] = total;
        }
        double r = random.nextDouble() * total;
        for (int i = 0; i < POSITIVE_TIERS.length; i++) {
            if (r < cum[i]) return i;
        }
        return POSITIVE_TIERS.length - 1;
    }

    /** Tire une luck 0..100 selon la courbe keyframes (lisse, sans creux). */
    private static int sampleKeyframeLuck(int luckBonus, RandomSource random) {
        double total = 0.0;
        double[] cum = new double[POSITIVE_TIERS.length];
        for (int i = 0; i < POSITIVE_TIERS.length; i++) {
            double p = keyframeTierProb(luckBonus, i);
            total += p;
            cum[i] = total;
        }
        double r = random.nextDouble() * total;
        for (int i = 0; i < POSITIVE_TIERS.length; i++) {
            if (r < cum[i]) {
                QualityTier t = POSITIVE_TIERS[i];
                return t.minLuck + random.nextInt(t.maxLuck - t.minLuck + 1);
            }
        }
        return 100;
    }

    /** Tirage de QUALITÉ unifié (branche positive) : courbe keyframes. */
    private static int rollQualityLuck(int luckBonus, RandomSource random) {
        return sampleKeyframeLuck(luckBonus, random);
    }

    /**
     * T338-MOB-PIECE-BY-PIECE : Simule le tirage indépendant d'UNE pièce d'équipement
     * directement depuis un Deep Lucky Block infusé autour de {@code desiredBonusLuck} (-100..+100),
     * avec une dispersion naturelle par pièce (±12) autour de la moyenne désirée pour
     * que chaque pièce équipée par un mob tire son propre grade réaliste.
     */
    public static int rollSimulatedLuckyBlockPieceLuck(int desiredBonusLuck, RandomSource random) {
        int jitter = random.nextInt(25) - 12; // [-12, +12] autour de la moyenne désirée
        int effectiveBonus = Mth.clamp(desiredBonusLuck + jitter, -100, 100);
        if (effectiveBonus <= 20) {
            double cursedChance = effectiveBonus <= -40 ? 0.14
                    : (effectiveBonus <= 0 ? 0.06 : 0.025);
            if (random.nextDouble() < cursedChance) {
                return random.nextInt(9); // 0..8 -> BROKEN / CURSED / USED
            }
        }
        return sampleKeyframeLuck(effectiveBonus, random);
    }

    public static void executeWithLuckBonus(Level world, double x, double y, double z, int luckBonus) {
        if (LuckyToolsCompatibility.consumeAndExecute(world, x, y, z)) return;
        // FIX v7 (resolu) : trace de diagnostic retiree (se declenchait a
        // CHAQUE roll du Lucky Block -> spam garanti sur une session longue).
        luckBonus = Math.max(-100, Math.min(100, luckBonus));

        if (luckBonus == 0) {
            execute(world, x, y, z);
            return;
        }

        if (!(world instanceof ServerLevel sl)) {
            execute(world, x, y, z);
            return;
        }

        RandomSource random = sl.getRandom();
        double bx = Math.floor(x) + 0.5;
        double by = Math.floor(y);
        double bz = Math.floor(z) + 0.5;

        int minRoll, maxRoll;
        if (luckBonus >= 50) {
            minRoll = 51 + (int) Math.round((luckBonus - 50) * (9.0 / 50.0)); // +50 -> minRoll=51, +100 -> minRoll=60
            maxRoll = 100;
        } else if (luckBonus > 0) {
            minRoll = 1 + luckBonus;
            maxRoll = 100;
        } else if (luckBonus <= -50) {
            minRoll = 1;
            maxRoll = 50 - (int) Math.round((Math.abs(luckBonus) - 50) * (10.0 / 50.0)); // -50 -> maxRoll=50, -100 -> maxRoll=40
        } else {
            minRoll = 1;
            maxRoll = 100 + luckBonus;
        }

        int effectiveLuck;
        boolean mainPos;
        // NOUVEAU MODÈLE : la roue EST le tirage (1 crâne + 9 couleurs ;
        // 2 crânes + 8 couleurs de +76 à +99, puis 3 crânes + 7 couleurs à +100).
        WheelResolution wheelRes = null;

        // ⛔ NOUVEAU : modèle PROGRESSIF — la branche est tirée selon une
        //    probabilité linéaire (0 % à -75, 50 % à 0, 100 % à +75), et la
        //    qualité des positifs se tire sur 0-100 (courbe biaisée vers le
        //    haut quand le bonus est positif).
        if (LUCK_PROGRESSIVE_SCALE) {
            double positiveChance = 50.0 + luckBonus * (50.0 / 75.0);
            positiveChance = Math.max(0.0, Math.min(100.0, positiveChance));
            boolean positiveBranch = rollPercent(random, positiveChance);
            mainPos = deepluckyblock.procedures.GenerateLuckyItemProcedure.DEBUG_STRUCTURE_LOOP || positiveBranch;

            if (mainPos) {
                wheelRes = resolvePositiveWheel(luckBonus, random);
                // Couleur gagnante = résultat définitif ; crâne = on affiche la
                // luck « entrevue » (tirage qualité) avant la bascule BAD.
                effectiveLuck = wheelRes.skull()
                        ? rollQualityLuck(luckBonus, random)
                        : wheelRes.resolvedLuck();
            } else {
                effectiveLuck = 1 + random.nextInt(50);
            }
        } else {
            effectiveLuck = minRoll + random.nextInt(maxRoll - minRoll + 1);

            // ⛔ NOUVEAU : à partir de +50 de luck infusée, on ignore le plancher dur
            //    (51-100 garanti = trop cheat) et on tire la QUALITÉ sur 0-100 avec
            //    une courbe biaisée vers le haut. La branche reste TOUJOURS positive
            //    à +50 et plus (voir mainPos) : le bad est donc bien skippé.
            if (LUCK_BONUS_POSITIVE_CURVE && luckBonus >= 50) {
                double exponent = Math.max(LUCK_BONUS_MIN_EXPONENT,
                        LUCK_BONUS_BASE_EXPONENT * (1.0 - luckBonus / 100.0));
                effectiveLuck = (int) (Math.pow(random.nextDouble(), exponent) * 101);
            }

            // mainPos : +50 et plus => TOUJOURS positif (skip bad). Sinon, règle
            // d'origine conservée : positif seulement si la luck tirée dépasse 50.
            mainPos = deepluckyblock.procedures.GenerateLuckyItemProcedure.DEBUG_STRUCTURE_LOOP || (luckBonus >= 50 || effectiveLuck > 50);
        }

        // FIX v7 (resolu) : trace de diagnostic retiree (memes raisons que ci-dessus).

        String label = (luckBonus > 0) ? ("§b§l⚡ +" + luckBonus + "% Luck Bonus!") : ("§c§l⚡ " + luckBonus + "% Curse!");
        ArmorStand pixelHolo = spawnHolo(sl, bx, by + 0.4, bz, mainPos ? PIXEL_GREEN : PIXEL_RED);
        ArmorStand textHolo  = spawnHolo(sl, bx, by - 0.3, bz, label);

        if (pixelHolo == null || textHolo == null) {
            boolean skullHit = mainPos && wheelRes != null && wheelRes.skull();
            boolean effPos = mainPos && !skullHit;
            GenerateBadProcedure.BadEventCategory badCategory = effPos
                    ? null : spinBadRoulette(random).category();
            generateResult(sl, bx, by, bz, effPos,
                    skullHit ? 1 + random.nextInt(50) : effectiveLuck, random,
                    badCategory, null, null);
            return;
        }

        buildSequence(sl, pixelHolo, textHolo, bx, by, bz,
                mainPos, effectiveLuck, random, null, false, Math.max(0, luckBonus), wheelRes);
    }

    private record MainLuckRoll(boolean positive, int luck, WheelResolution wheel) {}

    /** Tirage normal strictement identique a execute(), encapsule pour le Belt. */
    private static MainLuckRoll rollNaturalMainLuck(RandomSource random) {
        boolean positive = GenerateLuckyItemProcedure.DEBUG_STRUCTURE_LOOP || rollPercent(random, 50.0);
        if (!positive) return new MainLuckRoll(false, 1 + random.nextInt(50), null);
        WheelResolution wheel = resolvePositiveWheel(0, random);
        int luck = wheel.skull() ? rollQualityLuck(0, random) : wheel.resolvedLuck();
        return new MainLuckRoll(true, luck, wheel);
    }

    /** Tirage bonus strictement identique a executeWithLuckBonus(). */
    private static MainLuckRoll rollMainLuckWithBonus(int luckBonus, RandomSource random) {
        int minRoll;
        int maxRoll;
        if (luckBonus >= 50) {
            minRoll = 51 + (int) Math.round((luckBonus - 50) * (9.0 / 50.0));
            maxRoll = 100;
        } else if (luckBonus > 0) {
            minRoll = 1 + luckBonus;
            maxRoll = 100;
        } else if (luckBonus <= -50) {
            minRoll = 1;
            maxRoll = 50 - (int) Math.round(
                    (Math.abs(luckBonus) - 50) * (10.0 / 50.0));
        } else {
            minRoll = 1;
            maxRoll = 100 + luckBonus;
        }
        int luck;
        boolean positive;

        // ⛔ NOUVEAU : modèle PROGRESSIF, identique à executeWithLuckBonus
        //    (utilisé par la Lucky Belt).
        if (LUCK_PROGRESSIVE_SCALE) {
            double positiveChance = 50.0 + luckBonus * (50.0 / 75.0);
            positiveChance = Math.max(0.0, Math.min(100.0, positiveChance));
            positive = GenerateLuckyItemProcedure.DEBUG_STRUCTURE_LOOP
                    || rollPercent(random, positiveChance);

            if (positive) {
                WheelResolution wheel = resolvePositiveWheel(luckBonus, random);
                luck = wheel.skull() ? rollQualityLuck(luckBonus, random)
                                     : wheel.resolvedLuck();
                return new MainLuckRoll(true, luck, wheel);
            } else {
                luck = 1 + random.nextInt(50);
                return new MainLuckRoll(false, luck, null);
            }
        } else {
            luck = minRoll + random.nextInt(maxRoll - minRoll + 1);

            // ⛔ NOUVEAU : identique à executeWithLuckBonus — à +50 et plus, la
            //    QUALITÉ se tire sur 0-100 (courbe biaisée), la branche reste
            //    toujours positive (skip bad), utilisé par la Lucky Belt.
            if (LUCK_BONUS_POSITIVE_CURVE && luckBonus >= 50) {
                double exponent = Math.max(LUCK_BONUS_MIN_EXPONENT,
                        LUCK_BONUS_BASE_EXPONENT * (1.0 - luckBonus / 100.0));
                luck = (int) (Math.pow(random.nextDouble(), exponent) * 101);
            }

            positive = GenerateLuckyItemProcedure.DEBUG_STRUCTURE_LOOP || (luckBonus >= 50 || luck > 50);
        }

        return new MainLuckRoll(positive, luck, null);
    }

    /**
     * Lucky Belt : si le premier tirage n'atteint pas le meilleur palier Deep
     * (LEGENDARY+, luck 90), une seconde tentative remplace la premiere. Si le
     * premier est deja top-tier, il est conserve, comme dans Lucky Tools.
     */
    private static void executeWithLuckyBeltReroll(Level world,
                                                    double x, double y, double z,
                                                    int luckBonus) {
        if (!(world instanceof ServerLevel level)) return;
        deepluckyblock.util.DeepEnchant.rememberServerAccess(level.registryAccess());
        RandomSource random = level.getRandom();
        luckBonus = Math.max(-100, Math.min(100, luckBonus));

        MainLuckRoll chosen = luckBonus == 0
                ? rollNaturalMainLuck(random)
                : rollMainLuckWithBonus(luckBonus, random);
        // Un crâne gagnant n'est JAMAIS un top tier, même si la luck « entrevue »
        // affichée en roulette 1 était haute.
        boolean topTier = chosen.positive() && chosen.wheel() != null
                && !chosen.wheel().skull() && chosen.luck() >= 90;
        if (!topTier) {
            chosen = luckBonus == 0
                    ? rollNaturalMainLuck(random)
                    : rollMainLuckWithBonus(luckBonus, random);
        }

        double bx = Math.floor(x) + 0.5;
        double by = Math.floor(y);
        double bz = Math.floor(z) + 0.5;
        ArmorStand pixelHolo = spawnHolo(level, bx, by + 0.4, bz,
                chosen.positive() ? PIXEL_GREEN : PIXEL_RED);
        ArmorStand textHolo = spawnHolo(level, bx, by - 0.3, bz,
                "§6§l◇ ◇");

        if (pixelHolo == null || textHolo == null) {
            boolean skullHit = chosen.positive() && chosen.wheel() != null
                    && chosen.wheel().skull();
            boolean effPos = chosen.positive() && !skullHit;
            GenerateBadProcedure.BadEventCategory badCategory = effPos
                    ? null : spinBadRoulette(random).category();
            generateResult(level, bx, by, bz, effPos,
                    skullHit ? 1 + random.nextInt(50) : chosen.luck(), random,
                    badCategory, null, null);
            return;
        }
        buildSequence(level, pixelHolo, textHolo,
                bx, by, bz, chosen.positive(), chosen.luck(), random,
                null, false, Math.max(0, luckBonus), chosen.wheel());
    }

    // =========================================================================
    // COMPATIBILITE OPTIONNELLE LUCKY TOOLS (LAINK)
    // =========================================================================
    public static void executeLuckyToolsOutcome(Level world, double x, double y, double z,
                                                int effectiveLuck,
                                                boolean forceBest,
                                                boolean forceCurse) {
        executeLuckyToolsOutcome(world, x, y, z, effectiveLuck,
                forceBest, forceCurse, false);
    }

    public static void executeLuckyToolsOutcome(Level world, double x, double y, double z,
                                                int effectiveLuck,
                                                boolean forceBest,
                                                boolean forceCurse,
                                                boolean luckyBeltReroll) {
        effectiveLuck = Math.max(-100, Math.min(100, effectiveLuck));

        // Sans resultat force, la Wand et le Ring alimentent simplement le
        // systeme de luck deja existant du Deep Lucky Block.
        if (!forceBest && !forceCurse) {
            if (luckyBeltReroll) {
                executeWithLuckyBeltReroll(world, x, y, z, effectiveLuck);
            } else {
                executeWithLuckBonus(world, x, y, z, effectiveLuck);
            }
            return;
        }
        if (!(world instanceof ServerLevel level)) return;
        deepluckyblock.util.DeepEnchant.rememberServerAccess(level.registryAccess());

        RandomSource random = level.getRandom();
        boolean mainPos = forceBest;
        int mainLuck = forceBest ? 100 : 1;
        GenerateBadProcedure.BadEventCategory forcedBadCategory = null;
        if (forceCurse && !forceBest) {
            // Le Lucky Idol doit forcer un resultat inferieur : jamais le pool
            // GREEN. RED et BLACK restent equiprobables.
            forcedBadCategory = random.nextBoolean()
                    ? GenerateBadProcedure.BadEventCategory.RED
                    : GenerateBadProcedure.BadEventCategory.BLACK;
        }

        double bx = Math.floor(x) + 0.5;
        double by = Math.floor(y);
        double bz = Math.floor(z) + 0.5;
        // Aucun texte non localise ajoute ici : Lucky Tools affiche deja son
        // propre message traduit lors de l'utilisation de l'Idol.
        String label = forceBest
                ? "§6§l★ ★ ★"
                : "§5§l☠ ☠ ☠";
        ArmorStand pixelHolo = spawnHolo(level, bx, by + 0.4, bz,
                mainPos ? PIXEL_GREEN : PIXEL_RED);
        ArmorStand textHolo = spawnHolo(level, bx, by - 0.3, bz, label);

        if (pixelHolo == null || textHolo == null) {
            generateResult(level, bx, by, bz, mainPos, mainLuck, random,
                    forcedBadCategory, null, null, forceBest);
            return;
        }

        buildSequence(level, pixelHolo, textHolo,
                bx, by, bz, mainPos, mainLuck, random,
                forcedBadCategory, forceBest);
    }

    // =========================================================================
    // SEQUENCE (AVEC FIX ROULETTE : LE DERNIER NOMBRE EST LE VRAI LUCK !)
    // =========================================================================
    private static void buildSequence(ServerLevel level,
                                      ArmorStand pixelHolo, ArmorStand textHolo,
                                      double x, double y, double z,
                                      boolean mainPos, int mainLuck,
                                      RandomSource rng) {
        buildSequence(level, pixelHolo, textHolo, x, y, z,
                mainPos, mainLuck, rng, null, false, 0, null);
    }

    private static void buildSequence(ServerLevel level,
                                      ArmorStand pixelHolo, ArmorStand textHolo,
                                      double x, double y, double z,
                                      boolean mainPos, int mainLuck,
                                      RandomSource rng,
                                      GenerateBadProcedure.BadEventCategory forcedBadCategory,
                                      boolean forceEternalPayload) {
        buildSequence(level, pixelHolo, textHolo, x, y, z,
                mainPos, mainLuck, rng, forcedBadCategory, forceEternalPayload, 0, null);
    }

    private static void buildSequence(ServerLevel level,
                                      ArmorStand pixelHolo, ArmorStand textHolo,
                                      double x, double y, double z,
                                      boolean mainPos, int mainLuck,
                                      RandomSource rng,
                                      GenerateBadProcedure.BadEventCategory forcedBadCategory,
                                      boolean forceEternalPayload,
                                      int positiveLuckBonus) {
        buildSequence(level, pixelHolo, textHolo, x, y, z,
                mainPos, mainLuck, rng, forcedBadCategory, forceEternalPayload,
                positiveLuckBonus, null);
    }

    private static void buildSequence(ServerLevel level,
                                      ArmorStand pixelHolo, ArmorStand textHolo,
                                      double x, double y, double z,
                                      boolean mainPos, int mainLuck,
                                      RandomSource rng,
                                      GenerateBadProcedure.BadEventCategory forcedBadCategory,
                                      boolean forceEternalPayload,
                                      int positiveLuckBonus,
                                      WheelResolution wheelRes) {

        // ── NOUVEAU MODÈLE ROUE (apparitions de crâne) ──────────────────────
        // La roue EST le tirage : 1 crâne + 9 couleurs (luck infusée ≤ 75) ou
        // 2 crânes + 8 couleurs (luck +76..+99), ou 3 crânes + 7 couleurs (luck +100). Les couleurs sont tirées
        // indépendamment dans la range keyframes (doublons possibles). La case
        // d'arrêt décide du résultat ; 30 % du temps il bascule en mode
        // INATTENDU sur la case suivante. forceEternalPayload conserve
        // l'ancienne roue ciblée sans crâne (Lucky Idol / Lucky Tools).
        if (mainPos && !forceEternalPayload && wheelRes == null) {
            wheelRes = resolvePositiveWheel(positiveLuckBonus, rng);
        }
        final WheelResolution finalWheelRes = wheelRes;
        final boolean positiveSkull = mainPos && !forceEternalPayload
                && finalWheelRes != null && finalWheelRes.skull();
        // Luck affichée/générée : le résultat de la roue. En cas de crâne, on
        // garde la luck « entrevue » passée en paramètre (roulette 1) avant la
        // bascule BAD.
        final int finalMainLuck = (mainPos && !forceEternalPayload
                && finalWheelRes != null && !finalWheelRes.skull())
                ? finalWheelRes.resolvedLuck() : mainLuck;

        // Trace de diagnostic : passe SKULL_DEBUG a true pour verifier dans la
        // console que le tirage se declenche bien.
        if (SKULL_DEBUG) {
            com.mojang.logging.LogUtils.getLogger().info(
                "[deep_lucky_block] roulette : positive={} skullGagnant={} stop={} final={} surprise={} bonus=+{}",
                mainPos, positiveSkull,
                finalWheelRes != null ? finalWheelRes.stopIndex() : -1,
                finalWheelRes != null ? finalWheelRes.finalIndex() : -1,
                finalWheelRes != null && finalWheelRes.surprise(),
                positiveLuckBonus);
        }
        final BadRouletteSpin skullBadSpin = positiveSkull ? spinBadRoulette(rng) : null;
        final int skullBadLuck = positiveSkull ? 1 + rng.nextInt(50) : finalMainLuck;

        final boolean isLuckyBonus = mainPos && !positiveSkull && !forceEternalPayload
                && (rng.nextInt(100) < 10); // Aucun bonus Lucky ne fuit vers l'event BAD.
        final BadRouletteSpin badSpin = mainPos ? null
                : (forcedBadCategory != null
                    ? spinBadRouletteForCategory(forcedBadCategory, rng)
                    : spinBadRoulette(rng));
        final BadRouletteSpin[] badAnimation = mainPos
                ? null : buildBadRouletteAnimation(badSpin, rng);
        final int startTick = currentTick(level);
        int cursor = startTick + 1;

        // Roulette 1 : lors du dernier pas, affiche exactement la luck finale au lieu d'un nombre aléatoire !
        for (int i = 0; i < ROULETTE_STEPS; i++) {
            final int step   = i;
            final int atTick = cursor;
            schedule(level, atTick, () -> {
                setHolo(pixelHolo, (step % 2 == 0) ? PIXEL_GREEN : PIXEL_RED);
                int displayNumber = (step == ROULETTE_STEPS - 1)
                        ? finalMainLuck : (1 + RandomSource.create().nextInt(100));
                setHolo(textHolo, "§e§l⚡ " + displayNumber);
                float pitch = 0.6f + ((float) step / ROULETTE_STEPS) * 1.3f;
                playSound(level, x, y, z,
                        SoundEvents.NOTE_BLOCK_HAT.value(), 0.9f, clamp(pitch));
                particles(level, x, y - 0.2, z,
                        ParticleTypes.ENCHANT, 5, 0.3, 0.2, 0.3);
            });
            cursor += stepDelay(i, ROULETTE_STEPS);
        }

        // Revelation 1
        final int rev1Tick = cursor;
        schedule(level, rev1Tick, () -> {
            setHolo(pixelHolo, mainPos ? PIXEL_GREEN : PIXEL_RED);
            if (mainPos) {
                setHolo(textHolo, "§a§lLUCKY §f" + finalMainLuck + "/100 §a▲");
                playSound(level, x, y, z, SoundEvents.NOTE_BLOCK_BELL.value(), 1.5f, 2.0f);
                playSound(level, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, 1.2f, 1.9f);
                particles(level, x, y - 0.2, z, ParticleTypes.HAPPY_VILLAGER, 18, 0.4, 0.4, 0.4);
                particles(level, x, y - 0.2, z, ParticleTypes.TOTEM_OF_UNDYING, 10, 0.3, 0.3, 0.3);
            } else {
                setHolo(textHolo, "§c§lBAD §f" + finalMainLuck + "/100 §c▼");
                playSound(level, x, y, z, SoundEvents.NOTE_BLOCK_BASS.value(), 1.5f, 0.3f);
                playSound(level, x, y, z, SoundEvents.ANVIL_LAND, 0.6f, 0.4f);
                particles(level, x, y - 0.2, z, ParticleTypes.ANGRY_VILLAGER, 18, 0.4, 0.4, 0.4);
                particles(level, x, y - 0.2, z, ParticleTypes.SOUL, 10, 0.3, 0.3, 0.3);
            }
        });
        cursor++;
        cursor += PAUSE_BETWEEN;

        // Roulette 2 :
        // - branche positive : roulette de grades GenerateLuckyItem inchangee ;
        // - branche BAD : un tour physique complet des 37 poches europeennes.
        // ── Construction de la roue (branche positive uniquement) ────────────
        // NOUVEAU MODÈLE : la roue a déjà été tirée (resolvePositiveWheel) —
        // même composition à l'affichage qu'au tirage : 1 crâne + 9 couleurs
        // (luck ≤ 75) ou 2 crânes + 8 couleurs (luck +76..+99), ou 3 crânes + 7 couleurs (luck +100), ordre aléatoire.
        // forceEternalPayload conserve l'ancienne roue ciblée sans crâne.
        final WheelSlot[] wheel = mainPos
                ? (finalWheelRes != null
                        ? finalWheelRes.wheel()
                        : buildGradeWheel(finalMainLuck, false, rng))
                : null;
        // stopIndex = case où la roue s'arrête physiquement ; winIndex = case
        // du résultat réel (case suivante en mode INATTENDU).
        final int stopIndex = (mainPos && finalWheelRes != null)
                ? finalWheelRes.stopIndex()
                : (mainPos ? findWinningIndex(wheel, finalMainLuck, false) : 0);
        final int winIndex = (mainPos && finalWheelRes != null)
                ? finalWheelRes.finalIndex() : stopIndex;
        final int startIndex = (mainPos && finalWheelRes != null)
                ? finalWheelRes.startIndex()
                : (mainPos ? rng.nextInt(wheel.length) : 0);

        // Mode INATTENDU (30 %) : la roue s'arrête réellement sur stopIndex,
        // marque la pause, puis BIM bascule sur la case suivante (winIndex).
        final boolean surprise = mainPos && finalWheelRes != null
                && finalWheelRes.surprise();

        // Nombre de crans : on part de startIndex et on atteint stopIndex apres
        // WHEEL_TOTAL_STEPS crans exactement (plusieurs tours complets),
        // + 1 cran supplémentaire en mode INATTENDU.
        final int baseWheelSteps = mainPos
                ? WHEEL_TOTAL_STEPS + Math.floorMod(stopIndex - startIndex - WHEEL_TOTAL_STEPS, WHEEL_SIZE)
                : 0;

        final int roulette2Steps = mainPos
                ? baseWheelSteps + (surprise ? 2 : 1)
                : BAD_ROULETTE_STEPS;
        for (int i = 0; i < roulette2Steps; i++) {
            final int step   = i;
            final int atTick = cursor;
            final int totalPositive = roulette2Steps;
            schedule(level, atTick, () -> {
                if (mainPos) {
                    boolean isLast = step == totalPositive - 1;
                    int slotIdx;
                    if (isLast) {
                        // Résultat réel : la case suivante en mode INATTENDU.
                        slotIdx = winIndex;
                    } else {
                        // Défilement droit -> gauche : l'ordre mélangé de la
                        // roue passe en boucle ; l'avant-dernier cran (mode
                        // INATTENDU) tombe naturellement sur stopIndex, la
                        // vraie case d'arrêt, maintenue par stepDelayWheel.
                        slotIdx = (startIndex + step) % WHEEL_SIZE;
                    }
                    WheelSlot slot = wheel[slotIdx];
                    setHolo(pixelHolo, wheelPixel(slot));
                    setHolo(textHolo, wheelLabel(slot));

                    if (isLast && slot.skull()) {
                        playSound(level, x, y, z, SoundEvents.WITHER_SPAWN, 0.9f, 0.7f);
                        particles(level, x, y - 0.2, z,
                                ParticleTypes.SOUL, 18, 0.35, 0.35, 0.35);
                    } else if (isLast && surprise) {
                        // Clac sec du basculement de derniere seconde.
                        playSound(level, x, y, z, SoundEvents.NOTE_BLOCK_BELL.value(), 1.0f, 1.6f);
                        particles(level, x, y - 0.2, z,
                                ParticleTypes.END_ROD, 12, 0.3, 0.3, 0.3);
                    }
                } else {
                    BadRouletteSpin display = badAnimation[step];
                    setHolo(pixelHolo, roulettePixel(display.category()));
                    setHolo(textHolo, rouletteDisplay(display.number()));
                }
                float pitch = 0.6f + ((float) step / roulette2Steps) * 1.3f;
                playSound(level, x, y, z, SoundEvents.NOTE_BLOCK_HAT.value(), 0.9f, clamp(pitch));
                particles(level, x, y - 0.2, z,
                        mainPos ? ParticleTypes.ENCHANT : ParticleTypes.SMOKE,
                        5, 0.3, 0.2, 0.3);
            });

            if (mainPos) {
                cursor += stepDelayWheel(i, roulette2Steps, surprise);
            } else {
                int delay = stepDelayBadRoulette(i, BAD_ROULETTE_STEPS);
                // Le passage sur 0 reste affiche au moins 0,2 seconde pour etre
                // clairement visible, meme pendant la phase la plus rapide.
                if (badAnimation[i].category()
                        == GenerateBadProcedure.BadEventCategory.GREEN) {
                    delay = Math.max(delay, 4);
                }
                cursor += delay;
            }
        }

        if (isLuckyBonus) {
            final int bonusTick = cursor;
            schedule(level, bonusTick, () -> {
                setHolo(textHolo, "§6§l★ Lucky ! ★");
                playSound(level, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, 1.3f, 1.8f);
                particles(level, x, y - 0.2, z, ParticleTypes.TOTEM_OF_UNDYING, 15, 0.4, 0.4, 0.4);
                deepluckyblock.procedures.GenerateLuckyItemProcedure.FORCE_BONUS_ENCHANT = true;
            });
            cursor += 10; // 0.5 seconde
        }

        final int spawnTick = cursor + PAUSE_BEFORE_SPAWN + (positiveSkull ? PAUSE_AFTER_SKULL : 0);
        schedule(level, cursor, () -> {
            if (positiveSkull) {
                // Pas de roulette BAD supplémentaire : la tête reste affichée
                // plus longtemps avant que l'événement BAD parte réellement.
                setHolo(pixelHolo, skullDisplay());
                setHolo(textHolo, skullDisplay());
            } else if (mainPos) {
                setHolo(textHolo, "§b§l⚡ Calculating...");
            } else {
                setHolo(pixelHolo, roulettePixel(badSpin.category()));
                setHolo(textHolo, rouletteDisplay(badSpin.number()));
            }
        });

        schedule(level, spawnTick, () -> {
            // Securite : FORCE_BONUS_ENCHANT est un flag STATIQUE GLOBAL. S'il
            // reste arme (bloc precedent interrompu, deux blocs casses coup sur
            // coup, item du bonus jamais genere), il « fuit » sur la generation
            // suivante — y compris sur une branche NEGATIVE, ce qui faisait
            // apparaitre un item de GenerateLuckyItem au milieu d'un event BAD.
            // On le desarme donc systematiquement avant toute branche negative.
            boolean positiveBranch = mainPos && !positiveSkull;
            if (!positiveBranch) {
                GenerateLuckyItemProcedure.FORCE_BONUS_ENCHANT = false;
            }
            generateResult(level, x, y, z,
                    positiveBranch,
                    positiveSkull ? skullBadLuck : finalMainLuck,
                    RandomSource.create(),
                    positiveSkull ? skullBadSpin.category()
                            : (badSpin != null ? badSpin.category() : null),
                    pixelHolo, textHolo, forceEternalPayload);
        });

        schedule(level, spawnTick + HOLO_LIFETIME, () -> {
            killHolo(pixelHolo);
            killHolo(textHolo);
        });
    }

    // =========================================================================
    // RESULT GENERATION
    // =========================================================================
    private static void generateResult(ServerLevel level,
                                       double x, double y, double z,
                                       boolean mainPos, int mainLuck,
                                       RandomSource rng,
                                       GenerateBadProcedure.BadEventCategory badCategory,
                                       ArmorStand pixelHolo, ArmorStand textHolo) {
        generateResult(level, x, y, z, mainPos, mainLuck, rng,
                badCategory, pixelHolo, textHolo, false);
    }

    private static void generateResult(ServerLevel level,
                                       double x, double y, double z,
                                       boolean mainPos, int mainLuck,
                                       RandomSource rng,
                                       GenerateBadProcedure.BadEventCategory badCategory,
                                       ArmorStand pixelHolo, ArmorStand textHolo,
                                       boolean forceEternalPayload) {
        BranchResult result = null;
        try {
            if (mainPos || deepluckyblock.procedures.GenerateLuckyItemProcedure.DEBUG_STRUCTURE_LOOP) {
                result = forceEternalPayload
                        ? GenerateLuckyItemProcedure.generateLuckyToolsEternalPayload(
                                level, x, y, z, rng)
                        : runPositiveBranch(level, x, y, z, mainLuck, rng);
            } else {
                result = runNegativeBranch(level, x, y, z, mainLuck, rng, badCategory);
            }
        } catch (Exception e) {
            deepluckyblock.util.DebugLog.trace(e);
        }

        if (result == null) {
            result                = new BranchResult();
            result.negative       = !mainPos;
            result.branchLuck     = 50;
            result.title          = mainPos ? "Reward" : "Curse";
            result.titleColorCode = mainPos ? "a" : "c";
            result.rank           = resolveRank(50, !mainPos);
            result.items          = new ArrayList<>();
            if (mainPos) result.items.add(new ItemStack(Items.DIAMOND));
            result.particles      = new ArrayList<>();
            result.particles.add(mainPos ? "ENCHANT" : "SOUL");
        }

        ItemRank rank      = result.rank != null ? result.rank : resolveRank(result.branchLuck, !mainPos);
        String   colorCode = result.titleColorCode != null ? result.titleColorCode : extractColor(result.title);
        String   title     = result.title != null ? result.title : "Event";
        int      realBranch = result.branchLuck;
        boolean badGreen = !mainPos && badCategory == GenerateBadProcedure.BadEventCategory.GREEN;

        // Echelle native Average Luck (jamais negative) :
        // BAD BLACK = 0..24, BAD RED = 25..49, BAD GREEN = 50 ;
        // GenerateLuckyItem = 50..100 selon la luck affichee.
        double averageLuckScore = mainPos
                ? 50.0 + Math.max(0, Math.min(100, mainLuck)) * 0.5
                : GenerateBadProcedure.averageLuckScoreForEventNumber(result.badEventNumber);
        LuckyToolsCompatibility.recordResolvedOutcome(
                level, x, y, z, averageLuckScore);

        // Positif : couleur du grade. Bad : couleur exacte de la poche roulette.
        if (pixelHolo != null) {
            if (mainPos) {
                setHolo(pixelHolo, pixelForLuck(mainLuck));
            } else {
                setHolo(pixelHolo, roulettePixel(badCategory != null
                        ? badCategory : GenerateBadProcedure.BadEventCategory.RED));
            }
        }
        if (textHolo != null) {
            // La branche BAD conserve le numero/couleur final de la roulette.
            // Aucun hologramme de grade multicolore ne vient l'ecraser.
            if (mainPos) {
                setHolo(textHolo, formatRankHologram(rank, realBranch));
            }

            // Le teaser apparait AVANT l'action. Il suggere le danger sans nommer l'event.
            schedule(level, currentTick(level) + 4, () -> {
                if (textHolo.isAlive()) {
                    String displayColor = (colorCode != null && colorCode.length() == 1) ? colorCode : "d";
                    setHolo(textHolo, Component.literal("§" + displayColor + "§l")
                            .append(GenerateBadProcedure.localizeEventTitle(title)));
                }
            });

            if (mainPos || badGreen) {
                particles(level, x, y - 0.2, z, ParticleTypes.HAPPY_VILLAGER, 28, 0.5, 0.5, 0.5);
            } else {
                particles(level, x, y - 0.2, z, ParticleTypes.SOUL, 28, 0.5, 0.5, 0.5);
            }
        }

        if (result.hasStructure || title.toLowerCase().contains("structure"))
            doStructurePoof(level, x, y, z);

        if (result.hasStructure && result.structureId >= 0) {
            try {
                // T226 : le rayon de 64 etait un piege silencieux. Si aucun joueur
                // n'etait trouve, la structure etait simplement perdue alors que son
                // drapeau « deja apparue » venait d'etre ecrit. On elargit a 256 et
                // on LIBERE la reservation si vraiment personne n'est la.
                Player player = level.getNearestPlayer(x, y, z, 256, false);
                if (player != null) {
                    // 🎆 T229 : 1 a 4 fusees thematiques depuis le BLOC CASSE.
                    //    La structure, elle, est posee par Structures5Procedure
                    //    exactement la ou elle devait aller : rien ici ne touche
                    //    a sa position.
                    StructureFireworksProcedure.launch(level, x, y, z, result.structureId);
                    Structures5Procedure.execute(level, player, result.structureId);
                } else {
                    GenerateLuckyItemProcedure.releaseStructureReservation(result.structureId);
                }
            } catch (Exception e) {
                GenerateLuckyItemProcedure.releaseStructureReservation(result.structureId);
                deepluckyblock.util.DebugLog.trace(e);
            }
        }

        // Notification de streak negative (achievement "Bad Luck Streak...", pity,
        // action-bar, son d'enclume) : DOIT se declencher au moment ou l'event
        // negatif est reellement revele au joueur, pas au moment ou la branche
        // est resolue en interne (sinon le toast/son arrive avant le teaser et
        // jusqu'a 24 ticks (~1.2s) avant l'evenement lui-meme, donnant
        // l'impression d'un achievement deconnecte de ce qui se passe en jeu).
        if (result.negative) {
            if (textHolo != null) {
                schedule(level, currentTick(level) + 24, () -> {
                    try { GenerateLuckyItemProcedure.notifyNegativeBranch(level, x, y, z); }
                    catch (Throwable e) { if (DEBUG_LOGS) deepluckyblock.util.DebugLog.trace(e); }
                });
            } else {
                try { GenerateLuckyItemProcedure.notifyNegativeBranch(level, x, y, z); }
                catch (Throwable e) { if (DEBUG_LOGS) deepluckyblock.util.DebugLog.trace(e); }
            }
        }

        if (result.deferredAction != null) {
            final Runnable action = result.deferredAction;
            if (result.negative && textHolo != null) {
                // 20 ticks de tension apres l'affichage du teaser, puis l'event se revele tout seul.
                schedule(level, currentTick(level) + 24, () -> {
                    try { action.run(); }
                    catch (Throwable e) { deepluckyblock.util.DebugLog.trace(e); }
                });
            } else {
                try { action.run(); }
                catch (Throwable e) { deepluckyblock.util.DebugLog.trace(e); }
            }
        }

        List<ItemStack> drops = pickDropsSafe(result.items);
        if (!drops.isEmpty()) {
            spawnGlowingDrops(level, x, y, z, drops, rng);
        }

        playResultSounds(level, x, y, z, mainLuck, mainPos || badGreen, drops.isEmpty() ? 0 : drops.size());
        applyFx(level, x, y, z, rank, colorCode, rng, result);
    }

    private static String formatRankHologram(ItemRank rank, int branchLuck) {
        return switch (rank) {
            case MASTERWORK ->
                "§f§l§n✦ " + rank.name() + " ✦  §r§f(" + branchLuck + "/100)";
            case ETERNAL ->
                "§6§l✦ §1§l§o" + rank.name() + " §r§6§l✦  §r§f(" + branchLuck + "/100)";
            default ->
                "§" + rankColor(rank) + "§l✦ " + rank.name() + "  §f(" + branchLuck + "/100)";
        };
    }

    // =========================================================================
    // RESULT SOUND SYSTEM
    // =========================================================================
    private static void playResultSounds(ServerLevel level, double x, double y, double z,
                                         int mainLuck, boolean mainPos, int itemCount) {

        if (mainPos && mainLuck > BIG_LUCK_THRESHOLD) {
            playSound(level, x, y, z, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
            playSound(level, x, y, z, SoundEvents.PLAYER_LEVELUP, 1.2f, 1.0f);
            playSound(level, x, y, z, SoundEvents.ENDER_CHEST_OPEN, 1.5f, 0.7f);
            playSound(level, x, y, z, SoundEvents.RAID_HORN.value(), 0.6f, 1.4f);
            playSound(level, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, 1.5f, 1.2f);
            return;
        }

        if (mainPos) {
            playSound(level, x, y, z, SoundEvents.VILLAGER_YES, 1.0f, 1.1f);
            playSound(level, x, y, z, SoundEvents.VILLAGER_TRADE, 1.2f, 1.1f);
            playSound(level, x, y, z, SoundEvents.AMETHYST_BLOCK_CHIME, 0.9f, 1.5f);
            playSound(level, x, y, z, SoundEvents.EXPERIENCE_ORB_PICKUP, 1.0f, 1.3f);
        } else {
            playSound(level, x, y, z, SoundEvents.VILLAGER_NO, 1.2f, 0.8f);
            playSound(level, x, y, z, SoundEvents.ANVIL_LAND, 0.7f, 0.5f);
            playSound(level, x, y, z, SoundEvents.NOTE_BLOCK_BASS.value(), 1.0f, 0.4f);
        }
    }

    // =========================================================================
    // BRANCHES
    // =========================================================================
    private static BranchResult runPositiveBranch(Level level,
                                                  double x, double y, double z,
                                                  int mainLuck,
                                                  RandomSource rng) {
        BranchResult result = null;
        try {
            result = GenerateLuckyItemProcedure.generatePayload(
                    level, x, y, z, mainLuck, rng);
        } catch (Exception ignored) {}

        if (result != null) {
            result.negative = false;
            return result;
        }

        try {
            QualityTier tier  = QualityTier.getQualityTier(mainLuck);
            ItemStack   stack = GenerateLuckyItemProcedure.generate(level, mainLuck, tier, rng);
            BranchResult fb   = new BranchResult();
            fb.negative       = false;
            fb.branchLuck     = mainLuck;
            fb.title          = tier.colorCode + tier.displayName;
            fb.titleColorCode = extractColor(tier.colorCode);
            fb.rank           = resolveRank(mainLuck, false);
            fb.items          = new ArrayList<>();
            if (stack != null && !stack.isEmpty()) fb.items.add(stack);
            fb.particles      = new ArrayList<>(List.of("ENCHANT"));
            return fb;
        } catch (Exception e) {
            deepluckyblock.util.DebugLog.trace(e);
        }
        return null;
    }

    private static BranchResult runNegativeBranch(Level level,
                                                  double x, double y, double z,
                                                  int mainLuck,
                                                  RandomSource rng,
                                                  GenerateBadProcedure.BadEventCategory category) {
        // Filet de securite ultime : aucune branche negative ne doit jamais
        // heriter du bonus « ★ Lucky ! ★ » d'une generation positive precedente.
        GenerateLuckyItemProcedure.FORCE_BONUS_ENCHANT = false;

        try {
            BranchResult result = GenerateBadProcedure.generate(
                    level, x, y, z, mainLuck, rng,
                    category != null ? category : GenerateBadProcedure.BadEventCategory.RED);
            if (result != null) {
                result.negative = true;
                return result;
            }
        } catch (Throwable e) {
            deepluckyblock.util.DebugLog.err("[SLB-DEBUG] Negative event generation failed");
            deepluckyblock.util.DebugLog.trace(e);
        }

        BranchResult fb   = new BranchResult();
        fb.negative       = true;
        fb.branchLuck     = mainLuck;
        fb.title          = "Something went quiet...";
        fb.titleColorCode = "8";
        fb.rank           = resolveRank(mainLuck, true);
        fb.items          = new ArrayList<>();
        fb.particles      = new ArrayList<>(List.of("SOUL", "SMOKE"));
        return fb;
    }

    // =========================================================================
    // SCHEDULING
    // =========================================================================
    public static void schedule(ServerLevel level, int absoluteTick, Runnable task) {
        TASK_QUEUE.add(new ScheduledTask(absoluteTick, task));
    }

    public static int currentTick(ServerLevel level) {
        return level.getServer().getTickCount();
    }

    // Les chiens cadeaux peuvent apparaître en hauteur pendant les tests ou une
    // structure. Ils restent vulnérables au combat, mais ne meurent plus d'une
    // chute avant que la laisse de téléportation les ramène au joueur.
    @SubscribeEvent
    public static void protectGiftWolvesFromFall(LivingIncomingDamageEvent event) {
        if (event.getEntity() instanceof Wolf wolf
                && wolf.getPersistentData().getBoolean("slb_gift_wolf")
                && event.getSource().getMsgId().equals("fall")) {
            event.setCanceled(true);
            wolf.fallDistance = 0.0F;
        }
    }

    /**
     * Budget de temps accorde aux taches planifiees, PAR TICK (millisecondes).
     *
     * FIX CRITIQUE (freeze definitif rapporte : « aucune des structures n'a
     * charge [...] tout est freeze, rien ne s'update, ca calcule a l'infini »,
     * apres ~1 h d'attente).
     *
     * CAUSE RACINE : ce drainage executait TOUTES les taches echues dans le
     * MEME tick. Or une generation de structure planifie ~650 taches de 600
     * colonnes chacune (prefill, clear, 8 smoothPass...). Des que le serveur
     * prend du retard -- et il en prend forcement, le log montre deja
     * « 245 ticks behind » avant meme le premier spawn -- toutes les taches
     * dont le targetTick est depasse deviennent echues D'UN COUP. Le tick
     * tente alors d'en executer des centaines, dure plusieurs secondes,
     * accumule encore plus de retard, ce qui rend encore plus de taches
     * echues au tick suivant : la boucle diverge et ne se termine jamais.
     *
     * CORRECTIF : le drainage respecte un BUDGET de temps. On execute les
     * taches les plus anciennes d'abord, et on s'arrete des que le budget est
     * epuise -- le reste attend le tick suivant. Le serveur reste donc
     * toujours reactif, quelle que soit la taille de la file.
     */
    /**
     * T75 : 12 ms -> 35 ms (consigne utilisateur : « faut clairement diviser tout
     * tes temps totaux, du cassage de bloc jusqu'au message structure discovered
     * [...] au moins par 3 minimum ! »).
     *
     * MESURE : le pipeline terrain est decoupe en tranches et chaque tick n'en
     * executait que 12 ms sur les 50 ms disponibles, soit 24 % du temps. Une etape
     * de 3 s de calcul occupait donc 10 a 14 s de temps REEL -- c'est exactement ce
     * que montre le log utilisateur : « [DLB-STRUCTURE] l'etape qui vient de finir
     * a pris 10149 ms de temps REEL (prepZone 2/11 : prefillFoundation termine) »,
     * puis 4328 ms, 2927 ms, etc. A 35 ms/tick (70 % du tick) le meme travail
     * s'execute ~2,9 fois plus vite en temps reel.
     *
     * POURQUOI C'EST SANS RISQUE DE GEL : le budget ne fait que BORNER le travail
     * d'un tick ; les tranches elles-memes sont deja bornees (SLOW_COLUMN_MS,
     * FINAL_SLICE_MS, FIXLIQ_SLICE_MS...). Le pire tick possible reste donc
     * « 35 ms + la derniere tranche en cours », soit ~1,5 tick, tres loin des
     * 12 979 ms de gel qui avaient motive l'ancienne valeur.
     */
    private static final long TASK_BUDGET_MS = 35;

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (TASK_QUEUE.isEmpty()) return;

        long dbgT0 = System.currentTimeMillis();
        int dbgQueueSize = TASK_QUEUE.size();
        int now = event.getServer().getTickCount();

        List<ScheduledTask> due = new ArrayList<>();
        for (ScheduledTask t : TASK_QUEUE)
            if (now >= t.targetTick) due.add(t);
        if (due.isEmpty()) return;

        // Les plus anciennes d'abord : garantit que la structure commencee en
        // premier finit en premier, et qu'aucune tache ne reste en famine.
        due.sort(java.util.Comparator.comparingInt(t -> t.targetTick));

        int ran = 0;
        List<ScheduledTask> done = new ArrayList<>();
        for (ScheduledTask t : due) {
            done.add(t);
            try { t.task.run(); }
            catch (Exception e) { deepluckyblock.util.DebugLog.trace(e); }
            ran++;
            if (System.currentTimeMillis() - dbgT0 >= TASK_BUDGET_MS) break;
        }
        TASK_QUEUE.removeAll(done);

        long dbgMs = System.currentTimeMillis() - dbgT0;
        if (DEBUG_LOGS && (dbgQueueSize > 20 || dbgMs > 5))
            if (deepluckyblock.util.DebugLog.ENABLED) deepluckyblock.util.DebugLog.out("[SLB-DEBUG] TASK_QUEUE: " + dbgQueueSize + " total, " + ran + " ran, " + dbgMs + "ms");
    }

    private static final class ScheduledTask {
        final int      targetTick;
        final Runnable task;
        ScheduledTask(int tick, Runnable r) { targetTick = tick; task = r; }
    }

    // =========================================================================
    // HOLOGRAMS
    // =========================================================================
    private static ArmorStand spawnHolo(ServerLevel level, double x, double y, double z, String text) {
        try {
            ArmorStand stand = new ArmorStand(EntityType.ARMOR_STAND, level);
            stand.moveTo(x, y, z, 0f, 0f);
            stand.setInvisible(true);
            stand.setNoGravity(true);
            stand.setInvulnerable(true);
            stand.setSilent(true);
            stand.setCustomName(Component.literal(text));
            stand.setCustomNameVisible(true);
            stand.addTag("SupremeLuckyHolo");

            net.minecraft.nbt.CompoundTag nbt = new net.minecraft.nbt.CompoundTag();
            stand.save(nbt);
            nbt.putBoolean("Small", true);
            nbt.putBoolean("NoBasePlate", true);
            nbt.putBoolean("NoGravity", true);
            nbt.putBoolean("Invisible", true);
            stand.load(nbt);

            level.addFreshEntity(stand);
            return stand;
        } catch (Exception e) {
            deepluckyblock.util.DebugLog.trace(e);
            return null;
        }
    }

    private static void setHolo(ArmorStand stand, String text) {
        setHolo(stand, Component.literal(text));
    }

    private static void setHolo(ArmorStand stand, Component text) {
        if (stand != null && stand.isAlive()) {
            stand.setCustomName(text);
            stand.setCustomNameVisible(true);
        }
    }

    private static void killHolo(ArmorStand stand) {
        if (stand != null && stand.isAlive()) stand.discard();
    }

    // =========================================================================
    // GLOWING DROPS
    // =========================================================================
    private static void spawnGlowingDrops(ServerLevel level, double x, double y, double z,
                                          List<ItemStack> drops, RandomSource rng) {
        List<ItemEntity> spawnedItems = new ArrayList<>();

        for (int i = 0; i < drops.size(); i++) {
            double ox = (rng.nextDouble() - 0.5) * (i == 0 ? 0.8 : 1.3);
            double oz = (rng.nextDouble() - 0.5) * (i == 0 ? 0.8 : 1.3);
            ItemEntity ie = new ItemEntity(level, x + 0.5 + ox, y + 1.1, z + 0.5 + oz, drops.get(i));
            ie.setDeltaMovement(0, 0.32 + i * 0.04, 0);
            ie.setGlowingTag(true);
            level.addFreshEntity(ie);
            spawnedItems.add(ie);
        }

        final int removeTick = currentTick(level) + GLOW_DURATION;
        for (ItemEntity item : spawnedItems) {
            schedule(level, removeTick, () -> {
                if (item.isAlive()) item.setGlowingTag(false);
            });
        }

        for (int t = 0; t < GLOW_DURATION; t += 10) {
            final int sparkTick = currentTick(level) + t;
            schedule(level, sparkTick, () -> {
                for (ItemEntity item : spawnedItems) {
                    if (item.isAlive()) {
                        level.sendParticles(ParticleTypes.END_ROD,
                                item.getX(), item.getY() + 0.3, item.getZ(),
                                3, 0.15, 0.15, 0.15, 0.02);
                    }
                }
            });
        }
    }

    // =========================================================================
    // TIMING
    // =========================================================================
    private static int stepDelay(int step, int totalSteps) {
        double t = (double) step / Math.max(1, totalSteps - 1);
        return Math.max(1, (int) Math.round(1.0 + Math.pow(t, 1.8) * 3.5));
    }

    // Timing de la roulette 2 POSITIVE (NOUVEAU MODÈLE).
    /**
     * Cadence de la roue de grades.
     *
     * Profil demandé : UN premier coup LENT (~0,5 s = 10 ticks), puis un GROS
     * coup d'accélération comme une vraie roulette (jusqu'à 1 tick par cran),
     * puis ralentissement PROGRESSIF jusqu'à l'arrêt sur le résultat.
     *
     * Mode INATTENDU (surprise, 30 %) : la roue s'arrête réellement sur la
     * case d'arrêt (avant-dernier cran), elle est maintenue ~0,45 s pour que
     * la pause se voie, puis BIM : le dernier cran claque très vite vers la
     * case suivante, le vrai résultat.
     */
    private static int stepDelayWheel(int step, int totalSteps, boolean surprise) {
        int last = totalSteps - 1;

        // Mode INATTENDU : vraie pause sur la case d'arret, puis BIM tres sec.
        if (surprise && step == last - 1) return 9;
        if (surprise && step == last) return 2;

        // Premier coup lent : ~0,5 s, comme une roue qu'on lance a la main.
        if (step == 0) return 10;

        // Gros coup d'acceleration : 6 ticks -> 1 tick/cran sur ~22 % du parcours.
        int accelEnd = Math.max(2, (int) Math.round(totalSteps * 0.22));
        if (step < accelEnd) {
            double t = (double) (step - 1) / Math.max(1, accelEnd - 1);
            return Math.max(1, (int) Math.round(6 - t * 5));
        }
        // Ralentissement progressif : 1 tick -> ~11 ticks, arret dramatique.
        double t = (double) (step - accelEnd) / Math.max(1, last - accelEnd);
        return Math.max(1, (int) Math.round(1 + Math.pow(t, 2.2) * 10.0));
    }

    private static int stepDelayV2(int step, int totalSteps) {
        int accelCount = Math.max(2, totalSteps / 4); // ~25% en acceleration
        if (step < accelCount) {
            // Acceleration : le delai diminue (la roue prend de la vitesse)
            double t = (double) step / accelCount;
            return Math.max(2, (int) Math.round(2 + (1.0 - t) * (accelCount + 1)));
        }
        // Deceleration : le delai augmente petit a petit jusqu'a l'arret
        double t = (double) (step - accelCount) / Math.max(1, totalSteps - 1 - accelCount);
        return Math.max(2, (int) Math.round(2 + Math.pow(t, 1.6) * 8.0));
    }

    // Timing dedie a la roulette BAD de 37 poches : rapide sur la majeure
    // partie du tour puis ralentissement progressif. Duree totale ~3,5-4 s.
    private static int stepDelayBadRoulette(int step, int totalSteps) {
        int accelCount = 8;
        if (step < accelCount) {
            return step < 3 ? 2 : 1;
        }
        double t = (double) (step - accelCount)
                / Math.max(1, totalSteps - 1 - accelCount);
        return Math.max(1, 1 + (int) Math.round(Math.pow(t, 2.0) * 3.0));
    }

    private static float clamp(float v) { return Math.min(v, 2.0f); }

    // =========================================================================
    // SOUNDS & PARTICLES
    // =========================================================================
    private static void playSound(ServerLevel level, double x, double y, double z,
                                  SoundEvent sound, float volume, float pitch) {
        level.playSound(null, x, y, z, sound, SoundSource.BLOCKS, volume, pitch);
    }

    private static void particles(ServerLevel level, double x, double y, double z,
                                  ParticleOptions type, int count, double dx, double dy, double dz) {
        level.sendParticles(type, x, y, z, count, dx, dy, dz, 0.05);
    }

    // =========================================================================
    // DROPS — safe version
    // =========================================================================
    private static List<ItemStack> pickDropsSafe(List<ItemStack> candidates) {
        List<ItemStack> out = new ArrayList<>();
        if (candidates == null || candidates.isEmpty()) return out;
        for (ItemStack s : candidates) {
            if (s != null && !s.isEmpty()) {
                out.add(s.copy());
            }
        }
        return out;
    }

    // =========================================================================
    // STRUCTURE POOF
    // =========================================================================
    public static void doStructurePoof(ServerLevel level, double x, double y, double z) {
        double cx = x + 0.5, cy = y + 1.0, cz = z + 0.5;
        level.playSound(null, cx, cy, cz, SoundEvents.GENERIC_EXPLODE, SoundSource.BLOCKS, 1.5f, 0.8f);
        level.playSound(null, cx, cy, cz, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1.2f, 1.0f);
        level.playSound(null, cx, cy, cz, SoundEvents.PORTAL_TRAVEL, SoundSource.BLOCKS, 0.6f, 1.5f);
        level.playSound(null, cx, cy, cz, SoundEvents.LIGHTNING_BOLT_THUNDER, SoundSource.BLOCKS, 0.5f, 1.2f);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, cx, cy, cz, 5, 1.5, 1.5, 1.5, 0.0);
        level.sendParticles(ParticleTypes.EXPLOSION, cx, cy, cz, 20, 3.0, 2.0, 3.0, 0.1);
        level.sendParticles(ParticleTypes.FLASH, cx, cy, cz, 8, 2.0, 2.0, 2.0, 0.0);
        level.sendParticles(ParticleTypes.CLOUD, cx, cy, cz, 200, 4.0, 2.5, 4.0, 0.15);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, cx, cy, cz, 100, 3.5, 2.5, 3.5, 0.08);
        level.sendParticles(ParticleTypes.END_ROD, cx, cy, cz, 80, 3.0, 3.0, 3.0, 0.2);
        level.sendParticles(ParticleTypes.FIREWORK, cx, cy + 2, cz, 60, 2.5, 2.0, 2.5, 0.3);
        level.sendParticles(ParticleTypes.ENCHANT, cx, cy + 3, cz, 100, 3.0, 3.0, 3.0, 0.5);
        level.sendParticles(ParticleTypes.DRAGON_BREATH, cx, cy, cz, 40, 3.0, 1.5, 3.0, 0.1);
        level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING, cx, cy + 1, cz, 60, 2.0, 2.0, 2.0, 0.4);
    }

    // =========================================================================
    // FINAL FX
    // =========================================================================
    private static void applyFx(ServerLevel level, double x, double y, double z,
                                ItemRank rank, String colorCode,
                                RandomSource rng, BranchResult result) {
        List<ParticleOptions> set = new ArrayList<>();
        set.addAll(rankParticles(rank));
        set.addAll(colorParticles(colorCode));
        if (result.particles != null)
            for (String p : result.particles) {
                ParticleOptions po = namedParticle(p);
                if (po != null) set.add(po);
            }
        new HashSet<>(set).forEach(p ->
                level.sendParticles(p, x + 0.5, y + 1.2, z + 0.5, 16, 0.45, 0.45, 0.45, 0.02));
    }

    private static List<ParticleOptions> rankParticles(ItemRank r) {
        return switch (r) {
            case BROKEN     -> List.of(ParticleTypes.SMOKE, ParticleTypes.ASH);
            case CURSED     -> List.of(ParticleTypes.SOUL, ParticleTypes.SQUID_INK);
            case USED       -> List.of(ParticleTypes.ASH);
            case COMMON     -> List.of(ParticleTypes.CLOUD);
            case UNCOMMON   -> List.of(ParticleTypes.HAPPY_VILLAGER);
            case RARE       -> List.of(ParticleTypes.HAPPY_VILLAGER, ParticleTypes.ENCHANT);
            case EPIC       -> List.of(ParticleTypes.ENCHANT, ParticleTypes.END_ROD);
            case LEGENDARY  -> List.of(ParticleTypes.END_ROD, ParticleTypes.FIREWORK);
            case MYTHIC     -> List.of(ParticleTypes.TOTEM_OF_UNDYING, ParticleTypes.DRAGON_BREATH);
            case RELIC      -> List.of(ParticleTypes.TOTEM_OF_UNDYING, ParticleTypes.END_ROD, ParticleTypes.FIREWORK);
            case MASTERWORK -> List.of(ParticleTypes.END_ROD, ParticleTypes.FIREWORK, ParticleTypes.DRAGON_BREATH);
            case ETERNAL    -> List.of(ParticleTypes.TOTEM_OF_UNDYING, ParticleTypes.DRAGON_BREATH, ParticleTypes.END_ROD, ParticleTypes.FIREWORK, ParticleTypes.ENCHANT);
            default         -> List.of(ParticleTypes.ENCHANT);
        };
    }

    private static List<ParticleOptions> colorParticles(String c) {
        if (c == null) return List.of(ParticleTypes.ENCHANT);
        return switch (c) {
            case "1" -> List.of(ParticleTypes.ENCHANT, ParticleTypes.END_ROD);
            case "2" -> List.of(ParticleTypes.HAPPY_VILLAGER, ParticleTypes.CLOUD);
            case "4" -> List.of(ParticleTypes.SMOKE, ParticleTypes.DRAGON_BREATH);
            case "5" -> List.of(ParticleTypes.END_ROD, ParticleTypes.ENCHANT);
            case "6" -> List.of(ParticleTypes.FIREWORK, ParticleTypes.END_ROD);
            case "e" -> List.of(ParticleTypes.FIREWORK, ParticleTypes.HAPPY_VILLAGER);
            case "d" -> List.of(ParticleTypes.END_ROD, ParticleTypes.ENCHANT);
            case "b" -> List.of(ParticleTypes.ENCHANT, ParticleTypes.CLOUD);
            case "a" -> List.of(ParticleTypes.HAPPY_VILLAGER, ParticleTypes.CLOUD);
            case "9" -> List.of(ParticleTypes.SOUL, ParticleTypes.ENCHANT);
            case "c" -> List.of(ParticleTypes.SMOKE, ParticleTypes.SOUL);
            case "f" -> List.of(ParticleTypes.CLOUD, ParticleTypes.END_ROD);
            default  -> List.of(ParticleTypes.ENCHANT);
        };
    }

    private static ParticleOptions namedParticle(String name) {
        if (name == null) return null;
        return switch (name.trim().toUpperCase()) {
            case "SMOKE"            -> ParticleTypes.SMOKE;
            case "SOUL"             -> ParticleTypes.SOUL;
            case "EXPLOSION"        -> ParticleTypes.EXPLOSION;
            case "ASH"              -> ParticleTypes.ASH;
            case "SQUID_INK"        -> ParticleTypes.SQUID_INK;
            case "CLOUD"            -> ParticleTypes.CLOUD;
            case "ENCHANT"          -> ParticleTypes.ENCHANT;
            case "END_ROD"          -> ParticleTypes.END_ROD;
            case "FIREWORK"         -> ParticleTypes.FIREWORK;
            case "DRAGON_BREATH"    -> ParticleTypes.DRAGON_BREATH;
            case "TOTEM_OF_UNDYING" -> ParticleTypes.TOTEM_OF_UNDYING;
            case "HAPPY_VILLAGER"   -> ParticleTypes.HAPPY_VILLAGER;
            case "PORTAL"           -> ParticleTypes.PORTAL;
            case "CRIT"             -> ParticleTypes.CRIT;
            case "FLAME"            -> ParticleTypes.FLAME;
            case "SCULK_SOUL"       -> ParticleTypes.SCULK_SOUL;
            case "ITEM_SLIME"       -> ParticleTypes.ITEM_SLIME;
            case "SNEEZE"           -> ParticleTypes.SNEEZE;
            case "ELECTRIC_SPARK"    -> ParticleTypes.ELECTRIC_SPARK;
            case "WHITE_ASH"         -> ParticleTypes.WHITE_ASH;
            default                 -> null;
        };
    }

    // =========================================================================
    // UTILS
    // =========================================================================
    private static ItemRank resolveRank(int luck, boolean negative) {
        if (negative) {
            // Les trois états négatifs existent réellement dans TestProcedure.
            if (luck <= 10) return ItemRank.BROKEN;
            if (luck <= 30) return ItemRank.CURSED;
            return ItemRank.USED;
        }
        return QualityTier.getQualityTier(luck).rank;
    }

    private static String rankColor(ItemRank r) {
        return switch (r) {
            case BROKEN     -> "8";
            case CURSED     -> "5";
            case COMMON     -> "f";
            case UNCOMMON   -> "a";
            case RARE       -> "b";
            case EPIC       -> "d";
            case LEGENDARY  -> "e";
            case MYTHIC     -> "6";
            case RELIC      -> "c";
            case MASTERWORK -> "f";
            case ETERNAL    -> "1";
            case USED       -> "8";
            default         -> "7";
        };
    }

    private static String extractColor(String text) {
        if (text == null) return "7";
        int i = text.indexOf('§');
        if (i >= 0 && i + 1 < text.length()) {
            char c = Character.toLowerCase(text.charAt(i + 1));
            if ((c >= '0' && c <= '9') || (c >= 'a' && c <= 'f'))
                return String.valueOf(c);
        }
        return "7";
    }

    /** Couleur du VRAI grade unifie (QualityTier). La roulette affiche exactement
     *  les memes couleurs que les items reels, du pire (rouge) au meilleur. */
    private static String gradeColorCode(int luck) {
        String cc = QualityTier.getQualityTier(luck).colorCode; // "§X" ou "§X§l"
        return (cc != null && cc.length() >= 2) ? cc.substring(1, 2) : "f";
    }

    /** Retourne un pixel colore (§X█) correspondant au VRAI grade du nombre de luck.
     *  Effet gambling : le carre change de couleur en temps reel pendant la roulette,
     *  avec les couleurs exactes des grades de GenerateLuckyItem. */
    // ╔══════════════════════════════════════════════════════════════════════╗
    // ║  ROUE DE GRADES — NOUVEAU MODÈLE : LA ROUE EST LE TIRAGE             ║
    // ╠══════════════════════════════════════════════════════════════════════╣
    // ║                                                                      ║
    // ║  COMPOSITION (selon la luck infusée)                               ║
    // ║   • luck ≤ 75 (y c. 0 et négative) : 1 crâne + 9 couleurs           ║
    // ║   • luck > 75 : 2 crânes + 8 couleurs                               ║
    // ║                                                                      ║
    // ║  Les couleurs sont tirées INDÉPENDAMMENT selon la courbe keyframes   ║
    // ║  du bonus (la « range » de lucky-block-calc.html) : la même couleur  ║
    // ║  peut sortir PLUSIEURS fois (vert vert vert gris gris clair...).     ║
    // ║                                                                      ║
    // ║  DÉROULÉ                                                             ║
    // ║  Ordre aléatoire des cases, puis la roue défile droit -> gauche en   ║
    // ║  boucle : un coup lent (~0,5 s), un gros coup d'accélération comme   ║
    // ║  une vraie roulette, puis ralentissement progressif jusqu'à l'arrêt. ║
    // ║  La case d'arrêt DÉCIDE du résultat ; 30 % du temps, mode            ║
    // ║  INATTENDU : la roue s'arrête, puis BIM, bascule sur la case         ║
    // ║  SUIVANTE qui devient le vrai résultat.                              ║
    // ║                                                                      ║
    // ║  Taux de bad géométrique : 10 % des tirages positifs (1 crâne/10)    ║
    // ║  jusqu'à +75, 20 % de +76 à +99 (2 crânes/10), puis 30 % à +100 (3 crânes/10).                               ║
    // ║                                                                      ║
    // ╚══════════════════════════════════════════════════════════════════════╝

    /** Taille totale de la roue positive. */
    private static final int WHEEL_SIZE = 10;

    /** Nombre de grades reels affiches quand la tete de mort est presente. */
    private static final int WHEEL_GRADE_SLOTS_WITH_SKULL = WHEEL_SIZE - 1;

    /** A partir de +85 % de luck positive, la roue n'affiche plus de skull. */
    private static final int POSITIVE_NO_SKULL_BONUS = 85;

    // ⛔ NOUVEAU (publication) : le crâne doit apparaître TOUJOURS, même à +100,
    //    simplement de plus en plus rare. L'ancienne règle (+85 -> plus de skull)
    //    est conservée plus bas, juste désactivée par le flag ci-dessous.
    private static final int POSITIVE_SKULL_HALFLIFE = 25;
    private static final boolean SKULL_ALWAYS_IN_WHEEL = true;

    // ⛔ NOUVEAU (publication) : taux de bascule BAD via le crâne.
    //    Objectif : 2 % de blocs bad à +75 (au lieu de ~0,25 % avant).
    //    Le crâne devient plus rare dans la roue quand le bonus monte, mais
    //    plus « mortel » quand il apparaît : le produit net (présence × victoire)
    //    reste constant à SKULL_BAD_TARGET_PERCENT % quel que soit le bonus.
    //    Remettre SKULL_BAD_FLAT_RATE sur false pour retrouver l'ancien taux
    //    fixe POSITIVE_SKULL_CHANCE (2 % de victoire quand le crâne est présent).
    private static final boolean SKULL_BAD_FLAT_RATE = true;
    private static final double SKULL_BAD_TARGET_PERCENT = 2.0;

    // ⛔ NOUVEAU (publication) : modèle à 10 slots — le crâne est UNE case de la
    //    roue (10 couleurs + crâne). Son taux de victoire suit la luck infusée :
    //    10 % de base (+0) → 20 % à +60 → 40 % à +100 (soit 1, 2 puis 4 crânes
    //    visibles sur 10 tirages). Ancien taux constant de 2 % désactivé par
    //    ce flag. Remettre sur false pour l'ancien système (pool × win).
    private static final boolean SKULL_SLOT_MODEL = true;
    private static final int SKULL_BASE_PERCENT = 10;
    private static final int SKULL_MID_BONUS = 60;
    private static final int SKULL_MID_PERCENT = 20;
    private static final int SKULL_MAX_PERCENT = 40;

    /** Taux de crâne (bad) selon la luck infusée : 10 % → 20 % → 40 %, linéaire par morceaux. */
    private static double skullBadRateForBonus(int bonus) {
        if (bonus <= 0) return SKULL_BASE_PERCENT;
        if (bonus <= SKULL_MID_BONUS) {
            return SKULL_BASE_PERCENT
                    + (SKULL_MID_PERCENT - SKULL_BASE_PERCENT) * bonus / (double) SKULL_MID_BONUS;
        }
        return SKULL_MID_PERCENT
                + (SKULL_MAX_PERCENT - SKULL_MID_PERCENT) * (bonus - SKULL_MID_BONUS)
                        / (double) (100 - SKULL_MID_BONUS);
    }

    /** Nombre de crans parcourus par la roue avant de s'arreter. */
    private static final int WHEEL_TOTAL_STEPS = 26;

    /** Chance, en %, du saut « INATTENDU » : la roulette s'arrete sur une case
     *  puis BIM, bascule sur la case SUIVANTE qui devient le vrai resultat. */
    private static final int WHEEL_SURPRISE_CHANCE = 30;

    /** Luck infusée STRICTEMENT supérieure à ce seuil → 2 crânes + 8 couleurs
     *  dans la roue ; sinon 1 crâne + 9 couleurs. */
    private static final int SKULL_DOUBLE_FROM_BONUS = 75;
    /** T216 : à la luck maximale, 3 crânes sur les 10 cases = 30 %. */
    private static final int SKULL_TRIPLE_FROM_BONUS = 100;


    // ── LEGACY : ancien système d'apparition du crâne (présence aléatoire
    //    dans le pool × chance de victoire), REMPLACÉ par resolvePositiveWheel
    //    (la roue EST le tirage). Conservé uniquement pour référence.
    private static double positiveSkullPoolChance(int positiveLuckBonus) {
        if (SKULL_SLOT_MODEL) {
            // Modèle à 10 slots : la présence du crâne dans la roue EST le taux
            // de bad (10 % -> 20 % -> 40 %). Le crâne apparaît donc d'autant
            // plus souvent que la luck est haute (4 tirages sur 10 à +100).
            return skullBadRateForBonus(positiveLuckBonus);
        }
        if (positiveLuckBonus <= 0) return 100.0D;
        if (SKULL_ALWAYS_IN_WHEEL) {
            // Décroissance exponentielle douce : le skull reste TOUJOURS présent
            // dans le pool (même à +100), mais de plus en plus rare. Jamais 0.
            //   +0 -> 100 % · +25 -> 50 % · +50 -> 25 % · +75 -> 12,5 % · +100 -> 6,25 %
            return 100.0D * Math.pow(0.5, positiveLuckBonus / (double) POSITIVE_SKULL_HALFLIFE);
        }
        if (positiveLuckBonus >= POSITIVE_NO_SKULL_BONUS) return 0.0D;
        return (POSITIVE_NO_SKULL_BONUS - positiveLuckBonus) * 100.0D / POSITIVE_NO_SKULL_BONUS;
    }

    // ⛔ NOUVEAU : chance de victoire du crâne SACHANT qu'il est présent dans la
    //    roue. Avec SKULL_SLOT_MODEL : 100 % (le crâne, une fois présent, est
    //    la case gagnante -> net = taux de présence). Avec SKULL_BAD_FLAT_RATE :
    //    win = cible / pool (taux bad net constant). Sinon : POSITIVE_SKULL_CHANCE.
    private static double positiveSkullWinChance(int positiveLuckBonus) {
        if (SKULL_SLOT_MODEL) {
            return 100.0D;
        }
        if (SKULL_BAD_FLAT_RATE) {
            double pool = positiveSkullPoolChance(positiveLuckBonus);
            if (pool <= 0.0D) return 0.0D;
            return Math.min(100.0D, SKULL_BAD_TARGET_PERCENT * 100.0D / pool);
        }
        return POSITIVE_SKULL_CHANCE;
    }

    private static boolean rollPercent(RandomSource rng, double chancePercent) {
        double clamped = Mth.clamp(chancePercent, 0.0D, 100.0D);
        return clamped > 0.0D && rng.nextDouble() * 100.0D < clamped;
    }

    /**
     * Une case de la roue : soit un grade (luck + couleur), soit la tete de mort.
     */
    private record WheelSlot(int luck, boolean skull) {}

    /**
     * NOUVEAU MODÈLE (apparitions de crâne) : LA ROUE EST LE TIRAGE.
     *
     * Composition, selon la luck infusée :
     *   • luck ≤ 75 (y compris luck 0 et négative) : 1 crâne + 9 couleurs ;
     *   • luck > 75 : 2 crânes + 8 couleurs.
     *
     * Les couleurs sont tirées INDÉPENDAMMENT selon la courbe keyframes du
     * bonus (la « range » du calculateur lucky-block-calc.html) : la même
     * couleur peut donc sortir PLUSIEURS fois (ex. vert vert vert gris...).
     *
     * L'ordre des cases est mélangé au départ, la case d'arrêt est tirée au
     * sort, et WHEEL_SURPRISE_CHANCE % (30 %) du temps le résultat bascule en
     * mode INATTENDU : la roulette s'arrête, puis BIM, saute sur la case
     * SUIVANTE qui devient le vrai résultat.
     *
     * Taux de bad résultant (géométrique) : 10 % des tirages positifs
     * (1 crâne/10) jusqu'à +75, 20 % de +76 à +99 (2 crânes/10), puis 30 % à +100 (3 crânes/10).
     */
    private record WheelResolution(WheelSlot[] wheel, int startIndex, int stopIndex,
                                   int finalIndex, boolean surprise, boolean skull,
                                   int resolvedLuck) {}

    /**
     * T227 — point d'entree de TEST pour /dlb w &lt;luck infusee&gt; &lt;count&gt;.
     *
     * Retour en jeu : « j'ai vraiment l'impression que notre systeme de roulette
     * est pas du tout le meme que celui qu'on a faconne ensemble en html » et
     * « je tente une generation sur 30 blocs a luck 0, ca me selectionne
     * uniquement du broken item ».
     *
     * Cause : /dlb g prend un RANG FORCE, pas une luck infusee. /dlb g 0 30
     * force donc 30 fois le rang 0, qui tombe sous CURSED_LUCK_MAX -> 30 items
     * casses. Ce n'est pas la roulette qui est fausse, c'est la commande qui ne
     * la traverse pas.
     *
     * Cette methode-ci traverse la VRAIE roue (resolvePositiveWheel), celle qui
     * tire 10 cases independamment dans les keyframes, exactement comme le HTML.
     *
     * @param wheelOut si non nul, recoit la description lisible des 10 cases
     * @return le luck resolu (0..100), ou -1 si la case gagnante est un crane
     */
    public static int debugRollWheel(int infusedLuck, RandomSource rng, List<String> wheelOut) {
        WheelResolution r = resolvePositiveWheel(infusedLuck, rng);
        if (wheelOut != null) {
            for (int i = 0; i < r.wheel().length; i++) {
                WheelSlot sl = r.wheel()[i];
                String cell = sl.skull()
                        ? "§8[CRANE]"
                        : QualityTier.getQualityTier(sl.luck()).colorCode
                          + QualityTier.getQualityTier(sl.luck()).displayName + "§7(" + sl.luck() + ")";
                wheelOut.add((i == r.finalIndex() ? "§n" : "") + cell + "§r");
            }
            wheelOut.add(r.surprise() ? "§6(saut INATTENDU)" : "§7(arret direct)");
        }
        return r.skull() ? -1 : r.resolvedLuck();
    }

    /**
     * ⛔ T236 — NOMBRE DE CASES DE QUOTA, EN FONCTION DE LA LUCK.
     *
     * ⚠️ T234 avait INVERSE cette consigne : le nombre etait applique aux cases
     *    LIBRES au lieu des cases de QUOTA. Correction du joueur, mot pour mot :
     *    « /dlb probs 0 doit afficher Common etc x6 + 3 libre(s) ».
     *    Donc 6 = cases distribuees par le gabarit [4,2,1,1], et les cases
     *    libres sont ce qui RESTE : 9 - 6 = 3.
     *
     * Consigne : 6 cases de quota a luck 0, 4 a luck 100, intermediaire
     * interpole lineairement puis arrondi. En dessous de 0 on reste a 6.
     *
     * Plus la valeur est HAUTE, plus les quotas mordent et plus le rang gagnant
     * colle au rang annonce. Plus elle est BASSE, plus la queue rare respire.
     *
     *   luck    quota  couleurs  libres   top-1   Eternal
     *     0..25    6       9        3      30 %   0,018 -> 0,108 %
     *    26..75    5       9        4      30 %   0,145 -> 0,617 %
     *    76..99    4       8        4      20 %   0,654 -> 1,293 %
     *      100     3       7        4      10 %   1,333 %
     *
     * ⛔ T240 — le luck 100 rend 3, et non 4 comme l'interpolation le voudrait.
     *   Mesure : Eternal valait 1,2931 % a luck 99 et retombait a 0,9999 % a
     *   luck 100. Le creux apparaissait pile au luck MAXIMUM.
     *   Cause : a 100 le 3e crane apparait, la roue perd une case couleur
     *   (8 -> 7). Le quota restant a 4, c'est la case LIBRE qui sautait (4 -> 3),
     *   or Eternal ne vit que sur les cases libres : il perdait un quart de sa
     *   chance au moment ou le joueur a le plus investi.
     *   Avec un quota de 3 : 4 cases libres conservees, Eternal 1,3332 %, et la
     *   courbe devient strictement croissante de 0 a 100. Contrepartie assumee :
     *   le rang annonce tombe a 10 % au luck maximum.
     */
    public static int quotaBudgetFor(int bonus) {
        if (bonus <= 0) return 6;
        if (bonus >= 100) return 3;   // T240 : 3 et non 4, voir ci-dessus
        return (int) Math.round(6.0 - 2.0 * bonus / 100.0);
    }

    private static WheelResolution resolvePositiveWheel(int luckBonus, RandomSource rng) {
        int bonus = Mth.clamp(luckBonus, -100, 100);
        int skullCount = bonus >= SKULL_TRIPLE_FROM_BONUS ? 3
                : (bonus > SKULL_DOUBLE_FROM_BONUS ? 2 : 1);
        int colorCount = WHEEL_SIZE - skullCount;

        // ⛔ T228 — COMPOSITION PAR QUOTAS (remplace les tirages independants).
        //
        // Consigne exacte : « la roue dois selectionner par exemple 9 cases
        // couleur, il mets d'abord 4 de la rarete avec la plus haute proba,
        // puis il ajoute 2 de celui qui en a legerement moins mais reste top 2
        // priority per infusion, 1 de la suivante, un encore de la suivante,
        // et le restant est pick aleatoirement parmi ce qu'il reste, en suivant
        // les probas, par exemple eternal a moins de chance d'etre pris que
        // unusual, etc, faut bien calculer ca pour chaque luck infusee ».
        //
        // 1) on classe les 15 grades par probabilite DECROISSANTE a CETTE luck ;
        // 2) le quota est reparti 50 / 30 / 20 sur les 3 premiers ;
        // 3) les cases restantes sont tirees au sort, ponderees par leur proba,
        //    UNIQUEMENT parmi les grades qui n'ont pas recu de quota.
        //
        // ⚠️ AU MOINS une case libre est toujours garantie par le min(...)
        //    ci-dessous, sinon des grades deviendraient inatteignables.
        //    Sans reserve, a luck +100 il n'y a que 7 cases couleur et
        //    4+2+1 = 7 les consomme toutes : Legendary, Mythic, Relic,
        //    Masterwork et ETERNAL deviendraient litteralement impossibles a
        //    luck maximale (verifie : 215 combinaisons grade/luck mortes).
        //    Avec la reserve : 0 grade inatteignable sur les 201 lucks.
        // ⛔ T237 — le quota se repartit 50 / 30 / 20 sur le TOP-3.
        //   Consigne : « mets les 3 avec le plus de proba dans l'ordre suivant :
        //   rang avec le plus de proba prend 50 % du quota, le second 30 %, et
        //   le dernier 20 %, le reste est pick avec le reste de l'algo ».
        //   Remplace l'ancien gabarit fixe {4,2,1,1}.
        final double[] QUOTA_SHARE = {0.50, 0.30, 0.20};
        // ⛔ T238 — deux grades dont les probas different de moins de 10 % en
        //   RELATIF sont consideres a egalite. Consigne : « fais un melange worn
        //   et unusual, vu qu'ils sont a la meme proba [...] on pick parmi les
        //   deux a chaque fois randomly ». A luck 0, Unusual 14,20 % et Worn
        //   13,10 % sont a 7,8 % l'un de l'autre : ils forment un groupe et leur
        //   ordre est retire a chaque roue, pondere par leur proba (52 / 48).
        final double TIE_RELATIVE = 0.10;
        // T236 : budget = cases de QUOTA. Le min(...) garantit au moins une case
        //        libre, sans quoi des grades deviendraient inatteignables.
        final int QUOTA_BUDGET = Math.min(quotaBudgetFor(bonus), colorCount - 1);
        // T231 : probabilites calculees ici, plus de lecture de table statique.
        final double[] P = tierProbsAt(bonus);
        Integer[] order = new Integer[POSITIVE_TIERS.length];
        for (int i = 0; i < order.length; i++) order[i] = i;
        java.util.Arrays.sort(order, (a, c) -> {
            int cmp = Double.compare(P[c], P[a]);          // proba DECROISSANTE
            return cmp != 0 ? cmp : Integer.compare(a, c); // departage stable
        });

        // T238 : a l'interieur d'un groupe d'ex aequo, l'ordre est tire au sort,
        // pondere par les probas. Sans cela le meme grade gagnait TOUJOURS la
        // place, alors que son voisin a quasiment la meme chance.
        int gi = 0;
        while (gi < order.length) {
            int gj = gi + 1;
            double ref = P[order[gi]];
            while (gj < order.length && ref > 0.0 && (ref - P[order[gj]]) / ref <= TIE_RELATIVE) gj++;
            for (int a = gi; a < gj - 1; a++) {
                double tot = 0.0;
                for (int b = a; b < gj; b++) tot += P[order[b]];
                int chosen = a;
                if (tot > 0.0) {
                    double r = rng.nextDouble() * tot, acc = 0.0;
                    for (int b = a; b < gj; b++) { acc += P[order[b]]; if (r < acc) { chosen = b; break; } }
                }
                Integer tmp = order[a]; order[a] = order[chosen]; order[chosen] = tmp;
            }
            gi = gj;
        }

        List<WheelSlot> slots = new ArrayList<>();
        // ⛔ T236 — le nombre de cases de QUOTA depend de la luck :
        //   6 a luck 0, 4 a luck 100. Voir quotaBudgetFor().
        int budget = Math.max(0, QUOTA_BUDGET);

        // Repartition 50/30/20 par la METHODE DU PLUS FORT RESTE : partie entiere
        // de chaque part, puis les unites manquantes vont aux plus fortes parties
        // fractionnaires. La somme vaut ainsi EXACTEMENT budget. Un arrondi naif
        // ferait deborder la roue : budget 5 donne 2,5 + 1,5 + 1,0 qui s'arrondit
        // a 3 + 2 + 1 = 6 cases au lieu de 5.
        int[] part = new int[3];
        double[] frac = new double[3];
        int placed = 0;
        for (int k = 0; k < 3; k++) {
            double raw = budget * QUOTA_SHARE[k];
            part[k] = (int) Math.floor(raw);
            frac[k] = raw - part[k];
            placed += part[k];
        }
        while (placed < budget) {
            int best = 0;
            double bestFrac = -1.0;
            for (int k = 0; k < 3; k++) if (frac[k] > bestFrac) { bestFrac = frac[k]; best = k; }
            part[best]++;
            frac[best] = -1.0;
            placed++;
        }

        // Pose des quotas sur les 3 grades les plus probables a cette luck.
        for (int k = 0; k < 3 && k < order.length; k++) {
            if (part[k] <= 0) continue;
            QualityTier t = POSITIVE_TIERS[order[k]];
            for (int n = 0; n < part[k]; n++) slots.add(new WheelSlot(randomInTier(t, rng), false));
        }

        // ⛔ T238 — les cases restantes tirent dans la POOL COMPLETE, les 15 grades,
        //   y compris ceux qui ont deja recu un quota. Consigne : « les manquants
        //   sont bien selectionnes dans la pool complete, ce qui veut dire qu'en
        //   luck 0, je peux completement dans les emplacements libres si j'ai pas
        //   de chance avoir full common [...] il faut bien suivre les probas ».
        //   Avant, exclure les grades sous quota deformait la distribution.
        //   A luck 0 : Common passe de 30,00 % a 37,25 %, et les 3 cases libres
        //   sortent toutes Common dans 1,41 % des cas, soit 6 Common sur 9.
        double poolTotal = 0.0;
        for (int i = 0; i < POSITIVE_TIERS.length; i++) poolTotal += P[i];
        for (int n = placed; n < colorCount; n++) {
            int pick = -1;
            if (poolTotal > 0.0) {
                double r = rng.nextDouble() * poolTotal, acc = 0.0;
                for (int i = 0; i < POSITIVE_TIERS.length; i++) {
                    acc += P[i];
                    if (r < acc) { pick = i; break; }
                }
            }
            if (pick < 0) pick = order[0];
            slots.add(new WheelSlot(randomInTier(POSITIVE_TIERS[pick], rng), false));
        }
        for (int i = 0; i < skullCount; i++) slots.add(new WheelSlot(0, true));

        // Ordre aléatoire des cases dans la roue.
        Collections.shuffle(slots, new java.util.Random(rng.nextLong()));
        WheelSlot[] wheel = slots.toArray(new WheelSlot[0]);

        int stopIndex = rng.nextInt(WHEEL_SIZE);
        boolean surprise = rng.nextInt(100) < WHEEL_SURPRISE_CHANCE;
        int finalIndex = surprise ? (stopIndex + 1) % WHEEL_SIZE : stopIndex;
        WheelSlot outcome = wheel[finalIndex];
        return new WheelResolution(wheel, rng.nextInt(WHEEL_SIZE), stopIndex, finalIndex,
                surprise, outcome.skull(), outcome.skull() ? 0 : outcome.luck());
    }

    /**
     * T312-DLB-LAUNCH : Tire la VRAIE roue d'un Lucky Block infusé à {@code luckBonus}
     * (-100..+100), mais garantit une issue finale POSITIVE (aucune bascule sur crâne),
     * tout en conservant strictement la distribution réelle des grades hors crâne.
     */
    private static WheelResolution resolvePositiveWheelGuaranteedGood(int luckBonus, RandomSource rng) {
        WheelResolution res = resolvePositiveWheel(luckBonus, rng);
        if (!res.skull()) {
            return res;
        }
        WheelSlot[] wheel = res.wheel();
        for (int attempt = 0; attempt < 32; attempt++) {
            int stopIdx = rng.nextInt(WHEEL_SIZE);
            boolean surp = rng.nextInt(100) < WHEEL_SURPRISE_CHANCE;
            int finalIdx = surp ? (stopIdx + 1) % WHEEL_SIZE : stopIdx;
            WheelSlot slot = wheel[finalIdx];
            if (!slot.skull()) {
                return new WheelResolution(wheel, res.startIndex(), stopIdx, finalIdx,
                        surp, false, slot.luck());
            }
        }
        for (int idx = 0; idx < wheel.length; idx++) {
            if (!wheel[idx].skull()) {
                return new WheelResolution(wheel, res.startIndex(), idx, idx,
                        false, false, wheel[idx].luck());
            }
        }
        int fallbackLuck = rollQualityLuck(luckBonus, rng);
        return new WheelResolution(wheel, res.startIndex(), 0, 0, false, false, fallbackLuck);
    }

    /**
     * T312-DLB-LAUNCH : Point d'entrée de `/dlb g <luck> <count>` et `/dlb b <luck> <count>`.
     * Lance un vrai Lucky Block infusé à {@code infusedLuck} (-100..+100) avec hologramme,
     * roulette complète et récompenses/événements authentiques, forcé en GOOD ({@code forceGood=true},
     * garanti positif sans bascule crâne) ou en BAD ({@code forceGood=false}).
     */
    public static void executeForcedInfusedOutcome(ServerLevel level,
                                                   double x, double y, double z,
                                                   int infusedLuck,
                                                   boolean forceGood) {
        if (level == null) return;
        int clampedLuck = Mth.clamp(infusedLuck, -100, 100);
        RandomSource rng = level.getRandom();
        double bx = Math.floor(x) + 0.5;
        double by = Math.floor(y);
        double bz = Math.floor(z) + 0.5;

        String label;
        if (clampedLuck > 0) {
            label = "§b§l⚡ +" + clampedLuck + "% Luck Bonus!";
        } else if (clampedLuck < 0) {
            label = "§c§l⚡ " + clampedLuck + "% Curse!";
        } else {
            label = "§e§l⚡ Launching...";
        }

        if (forceGood) {
            WheelResolution wheelRes = resolvePositiveWheelGuaranteedGood(clampedLuck, rng);
            int effectiveLuck = wheelRes.resolvedLuck();
            ArmorStand pixelHolo = spawnHolo(level, bx, by + 0.4, bz, PIXEL_GREEN);
            ArmorStand textHolo  = spawnHolo(level, bx, by - 0.3, bz, label);
            if (pixelHolo == null || textHolo == null) {
                generateResult(level, bx, by, bz, true, effectiveLuck, rng, null, null, null);
                return;
            }
            buildSequence(level, pixelHolo, textHolo, bx, by, bz,
                    true, effectiveLuck, rng, null, false, Math.max(0, clampedLuck), wheelRes);
        } else {
            int effectiveLuck = 1 + rng.nextInt(50);
            ArmorStand pixelHolo = spawnHolo(level, bx, by + 0.4, bz, PIXEL_RED);
            ArmorStand textHolo  = spawnHolo(level, bx, by - 0.3, bz, label);
            if (pixelHolo == null || textHolo == null) {
                GenerateBadProcedure.BadEventCategory badCategory = spinBadRoulette(rng).category();
                generateResult(level, bx, by, bz, false, effectiveLuck, rng, badCategory, null, null);
                return;
            }
            buildSequence(level, pixelHolo, textHolo, bx, by, bz,
                    false, effectiveLuck, rng, null, false, Math.max(0, clampedLuck), null);
        }
    }

    /**
     * Construit la roue de 10 cases pour un resultat donne.
     *
     * Selection CIBLEE des grades : on part du tier du resultat final et on
     * prend ses voisins immediats, en s'eloignant progressivement. Le grade
     * gagnant est toujours present. L'ordre est ensuite melange pour que la
     * position du gagnant ne soit pas devinable.
     *
     * Si le skull fait partie du pool, on prend 9 couleurs + 1 tete de mort.
     * Sinon on prend 10 couleurs pures.
     *
     * @param finalLuck    luck reellement obtenu (determine le tier gagnant)
     * @param includeSkull true si la tete de mort doit apparaitre dans la roue
     * @param rng          source aleatoire
     * @return la roue construite
     */
    private static WheelSlot[] buildGradeWheel(int finalLuck, boolean includeSkull, RandomSource rng) {
        int gradeSlots = includeSkull ? WHEEL_GRADE_SLOTS_WITH_SKULL : WHEEL_SIZE;
        QualityTier[] tiers = QualityTier.values();
        QualityTier winner  = QualityTier.getQualityTier(finalLuck);

        int winnerIdx = 0;
        for (int i = 0; i < tiers.length; i++) if (tiers[i] == winner) { winnerIdx = i; break; }

        // Selection ciblee : voisins de plus en plus eloignes du gagnant.
        java.util.LinkedHashSet<QualityTier> picked = new java.util.LinkedHashSet<>();
        picked.add(winner);
        for (int radius = 1; radius < tiers.length && picked.size() < gradeSlots; radius++) {
            int lo = winnerIdx - radius, hi = winnerIdx + radius;
            // Ordre alterne, legerement randomise pour varier les roues.
            if (rng.nextBoolean()) {
                if (lo >= 0) picked.add(tiers[lo]);
                if (picked.size() < gradeSlots && hi < tiers.length) picked.add(tiers[hi]);
            } else {
                if (hi < tiers.length) picked.add(tiers[hi]);
                if (picked.size() < gradeSlots && lo >= 0) picked.add(tiers[lo]);
            }
        }
        // Securite : complete si le mod a moins de grades disponibles.
        for (QualityTier t : tiers) {
            if (picked.size() >= gradeSlots) break;
            picked.add(t);
        }

        java.util.List<QualityTier> list = new ArrayList<>(picked);
        while (list.size() > gradeSlots) list.remove(list.size() - 1);
        Collections.shuffle(list, new java.util.Random(rng.nextLong()));

        WheelSlot[] wheel = new WheelSlot[WHEEL_SIZE];
        for (int i = 0; i < gradeSlots; i++) {
            QualityTier t = list.get(i);
            // Le gagnant garde son luck exact ; les autres prennent un luck
            // representatif de leur palier, pour afficher un nombre credible.
            int luck = (t == winner) ? finalLuck
                     : t.minLuck + rng.nextInt(Math.max(1, t.maxLuck - t.minLuck + 1));
            wheel[i] = new WheelSlot(luck, false);
        }
        if (includeSkull) {
            wheel[WHEEL_SIZE - 1] = new WheelSlot(0, true);
        }
        return wheel;
    }

    /** Retrouve l'index de la case gagnante dans une roue construite. */
    private static int findWinningIndex(WheelSlot[] wheel, int finalLuck, boolean skullWins) {
        if (skullWins) {
            for (int i = 0; i < wheel.length; i++) {
                if (wheel[i].skull()) return i;
            }
            return 0;
        }
        for (int i = 0; i < wheel.length; i++) {
            if (!wheel[i].skull() && wheel[i].luck() == finalLuck) return i;
        }
        return 0;
    }

    /** Affichage d'une case : tete de mort blanche, ou pixel colore du grade. */
    private static Component wheelPixel(WheelSlot slot) {
        return slot.skull() ? skullDisplay()
                            : Component.literal(pixelForLuck(slot.luck()));
    }

    /** Ligne de texte d'une case. */
    private static Component wheelLabel(WheelSlot slot) {
        return slot.skull() ? skullDisplay()
                            : Component.literal("\u00a7b\u26a1 " + slot.luck());
    }

    private static String pixelForLuck(int luck) {
        return "§" + gradeColorCode(luck) + "\u2588";
    }

    // =========================================================================
    // TYPES
    // =========================================================================
    public enum ItemRank {
        BROKEN, CURSED,
        COMMON, UNCOMMON, RARE, EPIC, LEGENDARY, MYTHIC, RELIC, MASTERWORK, ETERNAL,
        USED
    }

    public static class BranchResult {
        public int             branchLuck;
        public String          title;
        public String          titleColorCode;
        public ItemRank        rank;
        public List<ItemStack> items;
        public List<String>    particles;
        public boolean         negative;
        public Runnable        deferredAction;
        public boolean         hasStructure;
        public int             structureId = -1;
        // Numero canonique GenerateBad (1-86), 0 pour une recompense positive.
        public int             badEventNumber = 0;
    }

    // =========================================================================
    // SYSTEME DE GRADES UNIFIE (unique pour GenerateLuckyItem ET la roulette).
    // Ordre de rarete du pire au meilleur, plage de luck -20 a 100.
    // Couleurs : rouge (maudit) -> gris (dechet) -> beaucoup de vert (commun)
    // -> un peu de bleu (rare) -> rose/violet (epic) -> jaune (legendaire,
    // extremement rare) -> or/rouge/speciaux (haut panier).
    // minEnchants/maxEnchants/minLevel/maxLevel = base des nombres d'enchantements.
    // =========================================================================
    public enum QualityTier {
        DOOMED     ("Doomed",      "§8", -20, -11, 0, 1, 1, 2,    ItemRank.BROKEN),
        CURSED     ("Cursed",      "§0", -10,  -1, 0, 1, 1, 2,    ItemRank.CURSED),
        // ⛔ NOUVEAU (publication) : paliers rééquilibrés — largeurs
        //    [13,12,11,10,9,9,8,7,6,5,4,3,2,1,1] (somme 101).
        //    Résultat : distribution STRICTEMENT décroissante à luck naturelle ET
        //    pic « le plus fréquent » qui progresse de grade en grade dans l'ordre,
        //    en s'arrêtant à LEGENDARY à +100 (la cloche redescend ensuite :
        //    MYTHIC/RELIC/MASTERWORK/ETERNAL redeviennent rares).
        //    ANCIEN barème conservé en commentaire :
        //    USED 0-8 · WORN 9-21 · COMMON 22-33 · UNCOMMON 34-44 · UNUSUAL 45-54
        //    · REMARKABLE 55-63 · OUTSTANDING 64-77 · RARE 78-82 · EPIC 83-86
        //    · EXCEPTIONAL 85-89 · LEGENDARY 90-93 · MYTHIC 94-96.
        USED       ("Used",        "§8",   0,  12, 1, 2, 1, 2,    ItemRank.USED),
        WORN       ("Worn",        "§7",  13,  24, 1, 2, 1, 3,    ItemRank.USED),
        COMMON     ("Common",      "§a",  25,  35, 2, 3, 1, 3,    ItemRank.COMMON),
        UNCOMMON   ("Uncommon",    "§2",  36,  45, 2, 4, 2, 4,    ItemRank.UNCOMMON),
        UNUSUAL    ("Unusual",     "§a",  46,  54, 3, 4, 2, 5,    ItemRank.UNCOMMON),
        REMARKABLE ("Remarkable",  "§e",  55,  63, 3, 5, 2, 6,    ItemRank.UNCOMMON),
        OUTSTANDING("Outstanding", "§b",  64,   77, 4, 6, 3, 7,    ItemRank.RARE),
        RARE       ("Rare",        "§9",  78,  82, 4, 6, 3, 8,    ItemRank.EPIC),
        EPIC       ("Epic",        "§5",  83,   86, 5, 6, 3, 8,    ItemRank.EPIC),
        EXCEPTIONAL("Exceptional", "§d",  87,   90, 5, 7, 3, 9,    ItemRank.EPIC),
        LEGENDARY  ("Legendary",   "§6",  91,   93, 6, 7, 4, 10,   ItemRank.LEGENDARY),
        MYTHIC     ("Mythic",      "§4",  94,  96, 6, 8, 4, 12,   ItemRank.MYTHIC),
        RELIC      ("Relic",       "§c",  97,  98, 7, 8, 5, 13,   ItemRank.RELIC),
        MASTERWORK ("Masterwork",  "§1",  99,  99, 7, 9, 5, 15,   ItemRank.MASTERWORK),
        ETERNAL    ("Eternal",     "§f", 100, 100, 8, 10, 6, 100, ItemRank.ETERNAL);

        public final String displayName, colorCode;
        public final int minLuck, maxLuck, minEnchants, maxEnchants, minLevel, maxLevel;
        public final ItemRank rank;

        QualityTier(String n, String c, int a, int b, int d, int e, int f, int g, ItemRank r) {
            displayName = n; colorCode = c; minLuck = a; maxLuck = b;
            minEnchants = d; maxEnchants = e; minLevel = f; maxLevel = g; rank = r;
        }

        /**
         * Couleur EXACTE du rang, en RGB.
         *
         * <p>Cinq rangs sortent des 16 codes vanilla : les 16 codes ne
         * proposent que DEUX gris (§7 et §8) pour trois rangs « junk », et aucun
         * mélange vert/jaune ni vert/cyan. Ces cinq rangs sont rendus via
         * {@code TextColor}
         * et leur couleur est transmise au glint par le tag NBT
         * {@code dlbGlint} — {@code getString()} d'un composant stylé ne
         * contient aucun code §, le parsing hérité ne peut donc pas les voir.
         *
         * <p>Valeurs : DOOMED/USED/WORN = 3 gris neutres distincts (aucun
         * blanc) ; UNUSUAL = vert-jaune, teinte 73,8° (entre le vert §a à 120°
         * et le jaune §e à 60°) ; REMARKABLE = vert-cyan #55FFAA, teinte 150,0°,
         * qui est la moyenne arithmétique exacte de §a (#55FF55) et de §b
         * (#55FFFF).
         *
         * <p>{@link #colorCode} reste volontairement en {@code "§X"} : la
         * roulette, les titres, les hologrammes, les particules et le chemin
         * « Creator Event » le consomment sous forme de chaîne. Ils affichent
         * donc le code vanilla le plus proche pour ces quatre rangs.
         */
        public int exactRgb() {
            return switch (this) {
                case DOOMED    -> 0x4A4A4A;
                case USED      -> 0x828282;
                case WORN      -> 0xBEBEBE;
                case UNUSUAL   -> 0xAADD00;
                case REMARKABLE -> 0x55FFAA;
                default         -> vanillaRgb(colorCode);
            };
        }

        /** Vrai si la couleur exacte sort des 16 codes vanilla. */
        public boolean isHexColor() {
            return this == DOOMED || this == USED || this == WORN || this == UNUSUAL
                    || this == REMARKABLE;
        }

        /** RGB exact d'un code couleur vanilla (§0..§f). */
        public static int vanillaRgb(String cc) {
            char c = (cc != null && cc.length() >= 2) ? Character.toLowerCase(cc.charAt(1)) : 'f';
            return switch (c) {
                case '0' -> 0x000000; case '1' -> 0x0000AA; case '2' -> 0x00AA00;
                case '3' -> 0x00AAAA; case '4' -> 0xAA0000; case '5' -> 0xAA00AA;
                case '6' -> 0xFFAA00; case '7' -> 0xAAAAAA; case '8' -> 0x555555;
                case '9' -> 0x5555FF; case 'a' -> 0x55FF55; case 'b' -> 0x55FFFF;
                case 'c' -> 0xFF5555; case 'd' -> 0xFF55FF; case 'e' -> 0xFFFF55;
                case 'f' -> 0xFFFFFF;
                default  -> 0xFFFFFF;
            };
        }

        public static QualityTier getQualityTier(int luck) {
            for (QualityTier t : values())
                if (luck >= t.minLuck && luck <= t.maxLuck) return t;
            return DOOMED; // hors plage -> pire grade
        }
    }
}
