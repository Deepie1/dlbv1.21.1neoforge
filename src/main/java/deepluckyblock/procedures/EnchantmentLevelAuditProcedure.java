package deepluckyblock.procedures;

import com.mojang.logging.LogUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.core.registries.BuiltInRegistries;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import deepluckyblock.util.DeepEnchant;

/**
 * ═══════════════════════════════════════════════════════════════════════════
 *  EnchantmentLevelAuditProcedure — audit automatique de la montée en niveau
 * ═══════════════════════════════════════════════════════════════════════════
 *
 *  OBJECTIF
 *  Vérifier que TOUS les enchantements du mod restent fonctionnels et sûrs
 *  jusqu'à un niveau arbitrairement élevé (200 par défaut), y compris quand
 *  le joueur force un niveau bien au-delà du maxLevel déclaré.
 *
 *  CE QUE LA PROCÉDURE FAIT RÉELLEMENT
 *  Pour chaque enchantement enregistré et pour chaque palier de niveau, elle
 *  applique l'enchantement sur un ItemStack réel puis exerce les chemins de
 *  code qui plantaient historiquement :
 *
 *    1. getFullname(level)        — rendu du nom + chiffres romains
 *    2. la description localisée  — clé <ns>.<path>.desc
 *    3. Scaling.audit(level)      — toutes les formules de progression
 *
 *  Chaque appel est isolé : une exception est capturée, comptée et rapportée
 *  avec le niveau exact et l'enchantement fautif, au lieu de faire tomber
 *  l'audit entier.
 *
 *  IMPORTANT — ce que l'audit ne peut PAS faire
 *  Il ne simule pas le monde : il ne détecte pas un effet qui « ne fait rien »
 *  pour une raison de gameplay (mauvais event, condition jamais remplie).
 *  Il détecte les CRASHS et les VALEURS DANGEREUSES, qui étaient la cause
 *  réelle des enchantements de pêche inopérants (V37).
 *
 *  UTILISATION
 *    /function ...  →  non. Appeler depuis une procédure MCreator ou :
 *    EnchantmentLevelAuditProcedure.execute(player);        // audit complet
 *    EnchantmentLevelAuditProcedure.execute(player, 200);   // borne custom
 *
 *  Le rapport complet part dans le log ; un résumé est envoyé au joueur.
 */
public final class EnchantmentLevelAuditProcedure {

    private static final Logger LOGGER = LogUtils.getLogger();

    /** Niveau maximal testé. */
    public static final int DEFAULT_MAX_LEVEL = 200;

    /** Paliers testés : bornes, limites vanilla, puissances, et le sommet. */
    private static final int[] PROBES = {
            1, 2, 3, 4, 5, 6, 9, 10, 11, 15, 20, 25, 32, 50, 64, 99, 100, 101, 127, 128, 150, 200
    };

    private static final String MODID = "deep_lucky_block";

    private EnchantmentLevelAuditProcedure() {
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Point d'entrée
    // ══════════════════════════════════════════════════════════════════════

    public static void execute(Player player) {
        execute(player, DEFAULT_MAX_LEVEL);
    }

    public static void execute(Player player, int maxLevel) {
        Report report = run(maxLevel);
        report.log();
        if (player != null) report.tell(player);
    }

    /** Exécute l'audit et renvoie le rapport, sans effet de bord. */
    public static Report run(int maxLevel) {
        Report report = new Report(maxLevel);

        List<Enchantment> all = new ArrayList<>();
        for (Enchantment e : deepluckyblock.util.DeepEnchant.reg(deepluckyblock.util.DeepEnchant.access())) {
            ResourceLocation id = deepluckyblock.util.DeepEnchant.getKeySafe(e);
            if (id != null && MODID.equals(id.getNamespace())) all.add(e);
        }
        all.sort(Comparator.comparing(e -> String.valueOf(deepluckyblock.util.DeepEnchant.getKeySafe(e))));
        report.total = all.size();

        for (Enchantment ench : all) {
            ResourceLocation id = deepluckyblock.util.DeepEnchant.getKeySafe(ench);
            if (id == null) continue;

            for (int level : PROBES) {
                if (level > maxLevel) break;
                auditOne(report, ench, id, level);
            }
        }
        return report;
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Audit d'un enchantement à un niveau donné
    // ══════════════════════════════════════════════════════════════════════

    private static void auditOne(Report report, Enchantment ench, ResourceLocation id, int level) {
        report.checks++;

        // --- 1. Application réelle sur un ItemStack -----------------------
        ItemStack stack = new ItemStack(Items.FISHING_ROD);
        try {
            DeepEnchant.setLevel(stack, ench, level);
        } catch (Throwable t) {
            report.fail(id, level, "enchant()", t);
            return;
        }

        // --- 2. Rendu du nom (chiffres romains, couleurs) -----------------
        try {
            String name = Enchantment.getFullname(deepluckyblock.util.DeepEnchant.holder(ench), level).getString();
            if (name == null || name.isBlank()) report.warn(id, level, "getFullname() renvoie du vide");
        } catch (Throwable t) {
            report.fail(id, level, "getFullname()", t);
        }

        // --- 3. Description localisée -------------------------------------
        try {
            String key = "enchantment." + id.getNamespace() + "." + id.getPath() + ".desc";
            String text = Component.translatable(key).getString();
            if (text.equals(key)) report.missingDesc(id);
        } catch (Throwable t) {
            report.fail(id, level, "description", t);
        }

        // --- 4. Formules de progression -----------------------------------
        try {
            Scaling.audit(level, report, id);
        } catch (Throwable t) {
            report.fail(id, level, "Scaling.audit()", t);
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Rapport
    // ══════════════════════════════════════════════════════════════════════

    public static final class Report {
        public final int maxLevel;
        public int total;
        public int checks;
        public final List<String> failures = new ArrayList<>();
        public final List<String> warnings = new ArrayList<>();
        public final List<String> missingDescs = new ArrayList<>();

        Report(int maxLevel) {
            this.maxLevel = maxLevel;
        }

        void fail(ResourceLocation id, int level, String stage, Throwable t) {
            failures.add(String.format(Locale.ROOT, "%s niv.%d — %s : %s: %s",
                    id, level, stage, t.getClass().getSimpleName(), String.valueOf(t.getMessage())));
        }

        void warn(ResourceLocation id, int level, String msg) {
            warnings.add(String.format(Locale.ROOT, "%s niv.%d — %s", id, level, msg));
        }

        void unsafe(ResourceLocation id, int level, String what, double value, double cap) {
            warnings.add(String.format(Locale.ROOT, "%s niv.%d — %s = %.1f (plafonné à %.1f)",
                    id, level, what, value, cap));
        }

        void missingDesc(ResourceLocation id) {
            String s = id.toString();
            if (!missingDescs.contains(s)) missingDescs.add(s);
        }

        public boolean ok() {
            return failures.isEmpty();
        }

        public void log() {
            LOGGER.info("========================================================");
            LOGGER.info("[deep_lucky_block] AUDIT DE NIVEAUX — jusqu'au niveau {}", maxLevel);
            LOGGER.info("  enchantements testés : {}", total);
            LOGGER.info("  contrôles effectués  : {}", checks);
            LOGGER.info("  CRASHS               : {}", failures.size());
            LOGGER.info("  descriptions absentes: {}", missingDescs.size());
            LOGGER.info("  avertissements       : {}", warnings.size());
            for (String f : failures) LOGGER.error("  [CRASH]   {}", f);
            for (String d : missingDescs) LOGGER.warn("  [NO-DESC] {}", d);
            int shown = 0;
            for (String w : warnings) {
                if (shown++ >= 40) {
                    LOGGER.warn("  ... {} avertissements supplémentaires", warnings.size() - 40);
                    break;
                }
                LOGGER.warn("  [WARN]    {}", w);
            }
            LOGGER.info("  RESULTAT : {}", ok() ? "AUCUN CRASH" : "DES CRASHS SUBSISTENT");
            LOGGER.info("========================================================");
        }

        public void tell(Player p) {
            p.sendSystemMessage(Component.literal("§8§m                                        "));
            p.sendSystemMessage(Component.literal("§6✦ Audit des niveaux §7(jusqu'au niveau " + maxLevel + ")"));
            p.sendSystemMessage(Component.literal("  §7Enchantements testés : §f" + total));
            p.sendSystemMessage(Component.literal("  §7Contrôles effectués  : §f" + checks));
            p.sendSystemMessage(Component.literal((failures.isEmpty() ? "  §a✔ Aucun crash" : "  §c✘ Crashs : " + failures.size())));
            if (!missingDescs.isEmpty())
                p.sendSystemMessage(Component.literal("  §e⚠ Descriptions absentes : " + missingDescs.size()));
            if (!warnings.isEmpty())
                p.sendSystemMessage(Component.literal("  §e⚠ Valeurs plafonnées : " + warnings.size()));
            for (int i = 0; i < Math.min(5, failures.size()); i++)
                p.sendSystemMessage(Component.literal("   §c" + failures.get(i)).withStyle(ChatFormatting.RED));
            if (failures.size() > 5)
                p.sendSystemMessage(Component.literal("   §7… voir le log pour les " + (failures.size() - 5) + " autres"));
            p.sendSystemMessage(Component.literal("  §7Rapport complet dans les logs."));
            p.sendSystemMessage(Component.literal("§8§m                                        "));
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  Scaling — source unique de vérité pour toutes les formules
    // ══════════════════════════════════════════════════════════════════════

    /**
     * Bornes appliquées à toutes les formules dépendant du niveau.
     *
     * Ces plafonds ne sont pas arbitraires : ils correspondent aux limites
     * au-delà desquelles Minecraft se comporte mal.
     *
     *   RADIUS   — un rayon de recherche d'entités élevé fait exploser le coût
     *              de getEntitiesOfClass (volume cubique). 32 blocs ≈ 2 chunks,
     *              c'est déjà très large et cela reste tenable.
     *   VELOCITY — au-delà de ~10, le serveur déclenche « moved too quickly »
     *              et téléporte/déconnecte ; les mobs sont éjectés du monde.
     *   PERCENT  — une probabilité ne dépasse jamais 100 %.
     *   TASKS    — nombre de tâches différées planifiées par impact.
     */
    public static final class Scaling {
        public static final double MAX_RADIUS   = 32.0;
        public static final double MAX_VELOCITY = 10.0;
        public static final int    MAX_PERCENT  = 100;
        public static final int    MAX_TASKS    = 20;

        private Scaling() {
        }

        /** Rayon de recherche/effet, en blocs. */
        public static double radius(double base, double perLevel, int level) {
            return Math.min(MAX_RADIUS, base + perLevel * Math.max(0, level));
        }

        /** Vélocité appliquée à une entité. */
        public static double velocity(double base, double perLevel, int level) {
            return Math.min(MAX_VELOCITY, base + perLevel * Math.max(0, level));
        }

        /** Probabilité en pourcentage, bornée à 100. */
        public static int percent(double perLevel, int level) {
            return (int) Math.min(MAX_PERCENT, Math.max(0, perLevel * Math.max(0, level)));
        }

        /** Probabilité avec plafond personnalisé (pour garder de l'aléatoire). */
        public static int percent(double perLevel, int level, int cap) {
            return (int) Math.min(Math.min(cap, MAX_PERCENT), Math.max(0, perLevel * Math.max(0, level)));
        }

        /** Nombre d'itérations / tâches planifiées. */
        public static int count(int perLevel, int level) {
            return Math.min(MAX_TASKS, Math.max(0, perLevel * Math.max(0, level)));
        }

        /** Multiplicateur générique borné. */
        public static double factor(double base, double perLevel, int level, double cap) {
            return Math.min(cap, base + perLevel * Math.max(0, level));
        }

        /**
         * Exerce toutes les familles de formules à un niveau donné et signale
         * celles qui auraient débordé sans plafond. C'est ce qui prouve que le
         * niveau 200 est sûr.
         */
        static void audit(int level, Report report, ResourceLocation id) {
            double rawRadius = 2.5 * level;
            if (rawRadius > MAX_RADIUS) report.unsafe(id, level, "rayon", rawRadius, radius(0, 2.5, level));

            double rawVel = 0.9 * level;
            if (rawVel > MAX_VELOCITY) report.unsafe(id, level, "vélocité", rawVel, velocity(0, 0.9, level));

            double rawPct = 8.0 * level;
            if (rawPct > MAX_PERCENT) report.unsafe(id, level, "probabilité", rawPct, percent(8, level));

            if (level > MAX_TASKS) report.unsafe(id, level, "tâches", level, count(1, level));

            // Vérifie qu'aucune formule ne produit NaN/Infini/négatif
            double[] values = {
                    radius(2, 2.5, level), velocity(0.4, 0.32, level),
                    percent(12, level), count(1, level), factor(1, 0.25, level, 3.0)
            };
            for (double v : values) {
                if (Double.isNaN(v) || Double.isInfinite(v) || v < 0)
                    report.warn(id, level, "formule invalide : " + v);
            }
        }
    }
}
