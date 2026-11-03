package deepluckyblock.commands;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import deepluckyblock.DeepLuckyBlockMod;
import deepluckyblock.enchantments.DeepElement;
import deepluckyblock.enchantments.WeaponVisualEffectId;
import deepluckyblock.enchantments.WeaponVfxMotion;
import deepluckyblock.enchantments.WeaponVfxSubtle;
import deepluckyblock.enchantments.WeaponVfxVariant;
import deepluckyblock.enchantments.WeaponVfxServerListener;
import deepluckyblock.procedures.TestProcedure;
import deepluckyblock.util.DeepEnchant;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * T346-VFX-TEST : commande de test {@code /dlbvfx} pour voir LES 30 effets visuels d'armes sans
 * avoir à farmer d'équipement ni à reproduire les conditions de combat.
 *
 * <p>Deux familles d'effets :</p>
 * <ul>
 *   <li>Ceux qui se déclenchent tout seuls (impact, entaille, sceau) : {@code /dlbvfx play ...} et
 *       {@code /dlbvfx all} les jouent dans le monde, sans aucune action du joueur.</li>
 *   <li>Ceux qui exigent une ACTION du joueur (critique, clic droit, tir à l'arc, frappe mêlée) :
 *       {@code /dlbvfx give} fabrique l'arme enchantée exacte, {@code /dlbvfx hit} frappe une cible
 *       d'entraînement en passant par le VRAI pipeline de dégâts, et {@code /dlbvfx crit} force le
 *       chemin critique.</li>
 * </ul>
 *
 * <pre>
 *   /dlbvfx list                    -> les 30 effets (id, nom, catégorie, enchant à utiliser)
 *   /dlbvfx play 29                 -> joue l'effet #29 devant le joueur (aucune action requise)
 *   /dlbvfx play 15 beam 6          -> variante "rayon"/siphon, intensité 6
 *   /dlbvfx all                     -> les 30 effets à la suite, en cercle autour du joueur
 *   /dlbvfx demo                    -> boucle automatique (relancer la commande pour arrêter)
 *   /dlbvfx crit 8                  -> force le chemin CRITIQUE (Étoile Dorée / Bullseye / Cyber-X)
 *   /dlbvfx hit 3                   -> 3 vraies frappes (impact + crit) sur la cible d'entraînement
 *   /dlbvfx dummy                   -> invoque/supprime la cible d'entraînement
 *   /dlbvfx give 29                 -> donne les armes enchantées déclenchant l'effet #29
 *   /dlbvfx clear                   -> supprime toutes les cibles d'entraînement
 * </pre>
 *
 * <p>Les sous-commandes exigent un joueur (position, regard, arme en main). Les noms et les ids
 * numériques sont acceptés partout, avec complétion par Tab.</p>
 */
@EventBusSubscriber(modid = DeepLuckyBlockMod.MODID)
public final class DlbVfxCommand {

    private DlbVfxCommand() {}

    /** Cible d'entraînement : marquée en NBT persistant pour pouvoir la retrouver et la nettoyer. */
    private static final String DUMMY_TAG = deepluckyblock.util.TrainingDummyTracker.DUMMY_TAG;

    private static final int KIND_SWING = 0;

    /**
     * T350-ELEMENT-ORDER : ordre de défilement par FAMILLE ÉLÉMENTAIRE (Bois, Feu, Terre, Métal, Eau,
     * Foudre, Lumière, Ombre, Sang, Vide) puis par priorité décroissante. Les animations ne défilent
     * donc plus « dans le désordre » : on voit une famille complète, puis la suivante.
     */
    private static final int[] ALL_IDS;

    static {
        List<WeaponVisualEffectId> ordered = new ArrayList<>(List.of(WeaponVisualEffectId.values()));
        ordered.sort(java.util.Comparator
                .comparingInt((WeaponVisualEffectId fx) -> fx.element.ordinal())
                .thenComparingInt(fx -> -fx.priority));
        ALL_IDS = new int[ordered.size()];
        for (int i = 0; i < ALL_IDS.length; i++) {
            ALL_IDS[i] = ordered.get(i).id;
        }
    }

    /** Une démo en cours (par joueur) : joue les effets un par un, en cercle autour du joueur. */
    private static final class DemoState {
        int cursor;
        int cooldown;
        int remaining;
        boolean loop;
        int kind;
        /** T350 : 0 = 30 effets, 1 = par familles élémentaires, 2 = les 50 déclinaisons. */
        int mode;
        Vec3 ringCenter;
    }

    private static final Map<UUID, DemoState> DEMOS = new ConcurrentHashMap<>();

    /** T350 : noms (et couleurs) des familles élémentaires proposées par la commande. */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_FAMILIES =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    java.util.Arrays.stream(DeepElement.values())
                            .map(e -> e.name().toLowerCase(Locale.ROOT))
                            .toList(),
                    builder);

    /** T352-ELEMENT-COVERAGE : un enchantement existe-t-il vraiment dans le registre du serveur ? */
    private static boolean enchantmentExists(ServerPlayer player, String path) {
        try {
            var registry = player.serverLevel().registryAccess()
                    .registryOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
            var key = net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.ENCHANTMENT,
                    net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(
                            deepluckyblock.DeepLuckyBlockMod.MODID, path));
            return registry.get(key) != null;
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean opOnly() {
        return "1".equals(System.getProperty("dlb.cmdOp", "0"));
    }

    // =====================================================================
    // SUGGESTIONS / PARSING
    // =====================================================================

    private static final List<String> EFFECT_SUGGESTIONS = buildEffectSuggestions();

    private static List<String> buildEffectSuggestions() {
        List<String> out = new ArrayList<>(64);
        for (WeaponVisualEffectId fx : WeaponVisualEffectId.values()) {
            out.add(String.format(Locale.ROOT, "%02d", fx.id));
            out.add(fx.name().toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_EFFECTS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(EFFECT_SUGGESTIONS, builder);

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_KINDS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(
                    List.of("impact", "swing", "beam", "proj", "all"), builder);

    private static WeaponVisualEffectId parseEffect(String raw) {
        if (raw == null) return null;
        String k = raw.trim();
        if (k.isEmpty()) return null;
        try {
            WeaponVisualEffectId byId = WeaponVisualEffectId.byId(Integer.parseInt(k));
            if (byId != null) return byId;
        } catch (NumberFormatException ignored) {
            // pas un id numérique : on tente le nom
        }
        String upper = k.toUpperCase(Locale.ROOT).replace('-', '_');
        for (WeaponVisualEffectId fx : WeaponVisualEffectId.values()) {
            if (fx.name().equals(upper)) return fx;
        }
        return null;
    }

    private static int parseKind(String raw) {
        if (raw == null) return WeaponVfxServerListener.KIND_IMPACT;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "swing", "slash", "entaille" -> KIND_SWING;
            case "beam", "siphon", "rayon" -> WeaponVfxServerListener.KIND_BEAM_SIPHON;
            case "proj", "projectile", "arrow", "fleche" -> WeaponVfxServerListener.KIND_PROJECTILE_ATTACH;
            case "all", "tous" -> -1;
            default -> WeaponVfxServerListener.KIND_IMPACT;
        };
    }

    private static String kindLabel(int kind) {
        return switch (kind) {
            case KIND_SWING -> "swing";
            case WeaponVfxServerListener.KIND_BEAM_SIPHON -> "beam";
            case WeaponVfxServerListener.KIND_PROJECTILE_ATTACH -> "proj";
            default -> "impact";
        };
    }

    // =====================================================================
    // ENREGISTREMENT
    // =====================================================================

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("dlbvfx");
        if (opOnly()) {
            root.requires(src -> src.hasPermission(2));
        }

        root.executes(DlbVfxCommand::help)
                .then(Commands.literal("help").executes(DlbVfxCommand::help))
                .then(Commands.literal("list").executes(DlbVfxCommand::list))
                // /dlbvfx play <effet> [kind] [niveau]
                .then(Commands.literal("play")
                        .then(Commands.argument("effect", StringArgumentType.word())
                                .suggests(SUGGEST_EFFECTS)
                                .executes(ctx -> play(ctx, WeaponVfxServerListener.KIND_IMPACT, 4, true))
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .suggests(SUGGEST_KINDS)
                                        .executes(ctx -> play(ctx, parseKind(StringArgumentType.getString(ctx, "kind")), 4, true))
                                        .then(Commands.argument("level", IntegerArgumentType.integer(1, 12))
                                                .executes(ctx -> play(ctx,
                                                        parseKind(StringArgumentType.getString(ctx, "kind")),
                                                        IntegerArgumentType.getInteger(ctx, "level"), true))))))
                // /dlbvfx all [kind]
                .then(Commands.literal("all")
                        .executes(ctx -> startShow(ctx, WeaponVfxServerListener.KIND_IMPACT, false))
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests(SUGGEST_KINDS)
                                .executes(ctx -> startShow(ctx, parseKind(StringArgumentType.getString(ctx, "kind")), false))))
                // /dlbvfx elements  (12 familles élémentaires, animation par famille)
                .then(Commands.literal("motions").executes(DlbVfxCommand::motions))
                .then(Commands.literal("elements").executes(DlbVfxCommand::families)
                        .then(Commands.argument("family", StringArgumentType.word())
                                .suggests(SUGGEST_FAMILIES)
                                .executes(DlbVfxCommand::familyShow)))
                // /dlbvfx variants  (les 50 déclinaisons, de moins en moins de particules)
                .then(Commands.literal("variants").executes(DlbVfxCommand::variantsDemo))
                // /dlbvfx subtle [famille] [index]  (50 accents discrets)
                .then(Commands.literal("subtle")
                        .executes(ctx -> subtleDemo(ctx, null, 0))
                        .then(Commands.argument("family", StringArgumentType.word())
                                .suggests(SUGGEST_FAMILIES)
                                .executes(ctx -> subtleDemo(ctx,
                                        StringArgumentType.getString(ctx, "family"), 0))
                                .then(Commands.argument("index", IntegerArgumentType.integer(1, WeaponVfxSubtle.TOTAL))
                                        .executes(ctx -> subtleDemo(ctx,
                                                StringArgumentType.getString(ctx, "family"),
                                                IntegerArgumentType.getInteger(ctx, "index"))))))
                // /dlbvfx demo  (bascule boucle / stop)
                .then(Commands.literal("demo").executes(DlbVfxCommand::toggleDemo))
                .then(Commands.literal("stop").executes(DlbVfxCommand::stopDemo))
                // /dlbvfx crit [effet] [niveau]
                .then(Commands.literal("crit")
                        .executes(ctx -> crit(ctx, WeaponVisualEffectId.GOLDEN_CRIT_STAR, 4))
                        .then(Commands.argument("effect", StringArgumentType.word())
                                .suggests(SUGGEST_EFFECTS)
                                .executes(ctx -> crit(ctx, parseEffect(StringArgumentType.getString(ctx, "effect")), 4))
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 12))
                                        .executes(ctx -> crit(ctx, parseEffect(StringArgumentType.getString(ctx, "effect")),
                                                IntegerArgumentType.getInteger(ctx, "level"))))))
                // /dlbvfx hit [nombre]
                .then(Commands.literal("hit")
                        .executes(ctx -> hit(ctx, 1))
                        .then(Commands.argument("times", IntegerArgumentType.integer(1, 20))
                                .executes(ctx -> hit(ctx, IntegerArgumentType.getInteger(ctx, "times")))))
                // /dlbvfx dummy
                .then(Commands.literal("dummy").executes(DlbVfxCommand::dummyToggle))
                // /dlbvfx give <effet> [niveau]
                .then(Commands.literal("give")
                        .then(Commands.argument("effect", StringArgumentType.word())
                                .suggests(SUGGEST_EFFECTS)
                                .executes(ctx -> give(ctx, parseEffect(StringArgumentType.getString(ctx, "effect")), 3))
                                .then(Commands.argument("level", IntegerArgumentType.integer(1, 20))
                                        .executes(ctx -> give(ctx, parseEffect(StringArgumentType.getString(ctx, "effect")),
                                                IntegerArgumentType.getInteger(ctx, "level"))))))
                // /dlbvfx clear
                .then(Commands.literal("clear").executes(DlbVfxCommand::clear));

        event.getDispatcher().register(root);
    }

    // =====================================================================
    // AIDE / LISTE
    // =====================================================================

    /**
     * T361-MOTIONS-UNIQUES : liste les 22 familles de mouvement et, pour chacune, les enchantements
     * qui l'utilisent — et vérifie au passage qu'aucun effet ne partage la signature d'un autre.
     */
    private static int motions(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal("§b§l" + WeaponVfxMotion.SHAPE_COUNT
                + " familles de mouvement §7pour " + WeaponVisualEffectId.values().length + " effets"), false);
        java.util.Map<String, java.util.List<String>> byShape = new java.util.LinkedHashMap<>();
        java.util.Set<String> signatures = new java.util.HashSet<>();
        int duplicates = 0;
        for (WeaponVisualEffectId fx : WeaponVisualEffectId.values()) {
            WeaponVfxMotion.Profile prof = WeaponVfxMotion.of(fx);
            byShape.computeIfAbsent(WeaponVfxMotion.labelOf(prof.shape()), k -> new java.util.ArrayList<>())
                    .add("#" + fx.id + " " + fx.name());
            if (!signatures.add(WeaponVfxMotion.signatureOf(fx))) duplicates++;
        }
        for (java.util.Map.Entry<String, java.util.List<String>> e : byShape.entrySet()) {
            String names = String.join("§7, ", e.getValue());
            src.sendSuccess(() -> Component.literal("  §d" + e.getKey() + " §8(" + e.getValue().size() + ") §7: §f" + names), false);
        }
        final int dupCount = duplicates;
        final int total = WeaponVisualEffectId.values().length;
        src.sendSuccess(() -> Component.literal(dupCount == 0
                ? "§a\u2714 §7aucune signature partagée : les " + total
                  + " animations ont un mouvement réellement différent."
                : "§c\u2716 §7" + dupCount + " animation(s) réutilisent une signature de mouvement."), false);
        return 1;
    }


    private static int help(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal("§b§l/dlbvfx §7— test des " + WeaponVisualEffectId.values().length + " effets visuels d'armes"), false);
        line(src, "§e/dlbvfx list", "liste des effets + enchant à utiliser");
        line(src, "§e/dlbvfx play <effet> [impact|swing|beam|proj] [niveau]", "joue l'effet devant toi (aucune action requise)");
        line(src, "§e/dlbvfx all [kind]", "tous les effets à la suite, en cercle autour de toi");
        line(src, "§e/dlbvfx demo", "boucle automatique de tous les effets (relancer pour arrêter)");
        line(src, "§e/dlbvfx crit [effet] [niveau]", "force le chemin CRITIQUE (nécessite normalement un saut/sprint)");
        line(src, "§e/dlbvfx hit [nombre]", "vraies frappes sur la cible d'entraînement (impact + crit réels)");
        line(src, "§e/dlbvfx dummy", "invoque / retire la cible d'entraînement");
        line(src, "§e/dlbvfx give <effet> [niveau]", "donne les armes enchantées qui déclenchent cet effet");
        line(src, "§e/dlbvfx elements [famille]", "les §f" + deepluckyblock.enchantments.DeepElement.values().length + " §7familles élémentaires (couleurs + effets)");
        line(src, "§e/dlbvfx variants", "défile les déclinaisons (de moins en moins de particules)");
        line(src, "§e/dlbvfx subtle [famille] [index]", "les §f" + (deepluckyblock.enchantments.WeaponVfxSubtle.COUNT + deepluckyblock.enchantments.WeaponVfxSubtleEcho.COUNT) + " §7accents discrets : §f" + deepluckyblock.enchantments.WeaponVfxSubtle.COUNT + " §7élémentaires + §f" + deepluckyblock.enchantments.WeaponVfxSubtleEcho.COUNT + " §7échos des grosses animations");
        line(src, "§7Déclenchement", "§f5 % §7coup normal, §f76 % §7coup critique, §f10 % §7des déclenchements en grosse animation (§f+5 % §7dégâts)");
        line(src, "§7Arme multi-éléments", "§f6 % §7de chance de jouer en simultané une animation de §fchaque §7élément de l'arme");
        // T359-BIG-ANIM-SCALE : les grosses animations prennent la taille du mob touché.
        line(src, "§7Taille des animations", "§7les grosses animations se scalent sur le mob touché : §fminuscule §7(poulet) → §fénorme §7(ravager, dragon)");
        // T361-MOTIONS-UNIQUES : 40 trajectoires distinctes réparties en 22 familles de mouvement.
        line(src, "§7Mouvements", "§f" + WeaponVisualEffectId.values().length + " §7trajectoires distinctes en §f" + WeaponVfxMotion.SHAPE_COUNT + " §7familles (arc, spirale, mâchoire, dune, vortex...)");
        line(src, "§e/dlbvfx motions", "liste les §f" + WeaponVfxMotion.SHAPE_COUNT + " §7familles de mouvement et les effets qui les utilisent");
        // T360-NO-VANILLA-PARTICLES : plus une seule particule Minecraft dans tout le mod.
        line(src, "§7Particules vanilla", "§f0 §7— §7halos, slashes, traînées et auras sont tous des §fmini-cubes 3D §7(Multi-Size Pixel-Art)");
        line(src, "§7Halo cosmétique", "§7couronne de pixels §fpermanente §7sur tout équipement enchanté (jamais une salve unique)");
        line(src, "§e/dlbvfx clear", "supprime les cibles d'entraînement");
        line(src, "§7Exemples : §f/dlbvfx play 29 §7puis §f/dlbvfx give 8 §7puis §f/dlbvfx hit 3", "");
        return 1;
    }

    private static void line(CommandSourceStack src, String cmd, String desc) {
        src.sendSuccess(() -> Component.literal("  " + cmd + (desc.isEmpty() ? "" : " §8— §7" + desc)), false);
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();
        src.sendSuccess(() -> Component.literal("§b§l" + WeaponVisualEffectId.values().length + " effets visuels d'armes §7(§f/dlbvfx play <id>§7)"), false);
        for (WeaponVisualEffectId fx : WeaponVisualEffectId.values()) {
            List<String> keys = WeaponVisualEffectId.enchantKeysFor(fx);
            if (keys.isEmpty()) keys = WeaponVisualEffectId.gauntletKeysFor(fx);
            String enchant = keys.isEmpty() ? "§8—" : "§8" + keys.get(0);
            // T361-MOTIONS-UNIQUES : le mouvement est affiché à côté de chaque effet.
            String motionName = WeaponVfxMotion.labelOf(WeaponVfxMotion.of(fx).shape());
            String label = String.format(Locale.ROOT, "§f#%02d §b%-20s §7%-10s §d%-8s §8%-24s %s",
                    fx.id, fx.name(), fx.category.name().toLowerCase(Locale.ROOT),
                    fx.element.displayName, motionName, enchant);
            src.sendSuccess(() -> Component.literal("  " + label), false);
        }
        src.sendSuccess(() -> Component.literal("§7Astuce : §f/dlbvfx all §7pour tout voir d'un coup, §f/dlbvfx elements §7par famille, §f/dlbvfx variants §7pour les §f" + deepluckyblock.enchantments.WeaponVfxVariant.COUNT + " §7déclinaisons, §f/dlbvfx subtle §7pour les §f" + (deepluckyblock.enchantments.WeaponVfxSubtle.COUNT + deepluckyblock.enchantments.WeaponVfxSubtleEcho.COUNT) + " §7accents discrets."), false);
        return 1;
    }

    // =====================================================================
    // LECTURE DE LA CIBLE / DU REGARD
    // =====================================================================

    private static ServerPlayer requirePlayer(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return ctx.getSource().getPlayerOrException();
    }

    /** Point visé : entité regardée, bloc regardé, sinon 2,5 blocs devant les yeux. */
    private static Vec3 aimPoint(ServerPlayer player) {
        HitResult hit = player.pick(8.0, 0.0f, false);
        if (hit.getType() == HitResult.Type.ENTITY && hit instanceof EntityHitResult ehr) {
            Entity e = ehr.getEntity();
            return new Vec3(e.getX(), e.getY() + e.getBbHeight() * 0.55, e.getZ());
        }
        if (hit.getType() == HitResult.Type.BLOCK) {
            return hit.getLocation();
        }
        return player.getEyePosition().add(player.getLookAngle().scale(2.5));
    }

    private static void announce(ServerPlayer player, String text) {
        player.displayClientMessage(Component.literal(text), true);
    }

    // =====================================================================
    // /dlbvfx play  — AUCUNE action joueur requise
    // =====================================================================

    private static int play(CommandContext<CommandSourceStack> ctx, int kind, int level, boolean verbose)
            throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(ctx);
        WeaponVisualEffectId fx = parseEffect(StringArgumentType.getString(ctx, "effect"));
        if (fx == null) {
            ctx.getSource().sendFailure(Component.literal("§cEffet inconnu. Utilise /dlbvfx list ou un id 1.." + WeaponVisualEffectId.values().length + "."));
            return 0;
        }
        ServerLevel sl = player.serverLevel();

        if (kind == -1) {
            int i = 0;
            for (int k : new int[]{WeaponVfxServerListener.KIND_IMPACT, KIND_SWING,
                    WeaponVfxServerListener.KIND_BEAM_SIPHON, WeaponVfxServerListener.KIND_PROJECTILE_ATTACH}) {
                final int kk = k;
                final int delay = i++ * 6;
                if (delay == 0) {
                    playOne(sl, player, fx, kk, level);
                } else {
                    TestProcedure.schedule(sl, TestProcedure.currentTick(sl) + delay,
                            () -> playOne(sl, player, fx, kk, level));
                }
            }
        } else {
            playOne(sl, player, fx, kind, level);
        }

        if (verbose) {
            String extra = (kind == WeaponVfxServerListener.KIND_PROJECTILE_ATTACH)
                    ? " §7(flèche de démo tirée devant toi)"
                    : "";
            announce(player, "§b#%02d §f%s §7— %s §8niv.%d%s".formatted(fx.id, fx.name(), kindLabel(kind), level, extra));
        }
        return 1;
    }

    /** Joue un effet (aucune condition de combat). Pour "proj", une flèche de démo est créée. */
    private static void playOne(ServerLevel sl, ServerPlayer player, WeaponVisualEffectId fx, int kind, int level) {
        Vec3 from = player.getEyePosition();
        if (kind == WeaponVfxServerListener.KIND_PROJECTILE_ATTACH) {
            Entity created = EntityType.ARROW.create(player.level());
            if (created instanceof Arrow arrow) {
                Vec3 look = player.getLookAngle();
                arrow.setOwner(player);
                arrow.setCritArrow(true);
                arrow.moveTo(from.x + look.x, from.y - 0.15, from.z + look.z, player.getYRot(), player.getXRot());
                arrow.shoot(look.x, look.y, look.z, 1.7f, 0.0f);
                if (sl.addFreshEntity(arrow)) {
                    WeaponVfxServerListener.playDebug(player, fx, kind, level, from,
                            new Vec3(arrow.getId(), 0.0, 0.0));
                }
                return;
            }
            // pas de flèche disponible : on retombe sur un impact classique
            WeaponVfxServerListener.playDebug(player, fx, WeaponVfxServerListener.KIND_IMPACT, level, from, aimPoint(player));
            return;
        }
        if (kind == KIND_SWING) {
            // L'entaille se dessine depuis l'origine vers la direction du regard (yaw calculé côté client)
            Vec3 dir = player.getLookAngle().scale(3.4);
            WeaponVfxServerListener.playDebug(player, fx, kind, level, from, from.add(dir));
            return;
        }
        if (kind == WeaponVfxServerListener.KIND_BEAM_SIPHON) {
            // Rayon/siphon : de la cible vers le joueur (sens réel du flux)
            Vec3 to = aimPoint(player);
            Vec3 chest = new Vec3(player.getX(), player.getY() + player.getBbHeight() * 0.62, player.getZ());
            WeaponVfxServerListener.playDebug(player, fx, kind, level, to, chest);
            return;
        }
        WeaponVfxServerListener.playDebug(player, fx, kind, level, from, aimPoint(player));
    }

    // =====================================================================
    // /dlbvfx all & /dlbvfx demo  — défilé automatique
    // =====================================================================

    private static int startShow(CommandContext<CommandSourceStack> ctx, int kind, boolean loop)
            throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(ctx);
        DemoState st = new DemoState();
        st.cursor = 0;
        st.cooldown = 2;
        st.remaining = ALL_IDS.length;
        st.loop = loop;
        st.kind = (kind == -1) ? WeaponVfxServerListener.KIND_IMPACT : kind;
        st.ringCenter = player.position();
        DEMOS.put(player.getUUID(), st);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§b[Démo VFX] §f" + WeaponVisualEffectId.values().length + " effets en cercle autour de toi §7— §f/dlbvfx stop §7pour arrêter."), false);
        return 1;
    }

    private static int toggleDemo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(ctx);
        if (DEMOS.containsKey(player.getUUID())) {
            DEMOS.remove(player.getUUID());
            ctx.getSource().sendSuccess(() -> Component.literal("§7[Démo VFX] arrêtée."), false);
            return 1;
        }
        DemoState st = new DemoState();
        st.cursor = 0;
        st.cooldown = 2;
        st.remaining = Integer.MAX_VALUE;
        st.loop = true;
        st.kind = WeaponVfxServerListener.KIND_IMPACT;
        st.ringCenter = player.position();
        DEMOS.put(player.getUUID(), st);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§b[Démo VFX] §fboucle lancée (" + WeaponVisualEffectId.values().length + " effets) §7— §f/dlbvfx demo §7pour arrêter."), false);
        return 1;
    }

    private static int stopDemo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(ctx);
        boolean removed = DEMOS.remove(player.getUUID()) != null;
        ctx.getSource().sendSuccess(() -> Component.literal(removed
                ? "§7[Démo VFX] arrêtée."
                : "§7[Démo VFX] aucune démo en cours."), false);
        return removed ? 1 : 0;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        if (DEMOS.isEmpty()) return;
        Iterator<Map.Entry<UUID, DemoState>> it = DEMOS.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, DemoState> entry = it.next();
            ServerPlayer player = event.getServer().getPlayerList().getPlayer(entry.getKey());
            if (player == null || !player.isAlive()) {
                it.remove();
                continue;
            }
            DemoState st = entry.getValue();
            if (--st.cooldown > 0) continue;
            st.cooldown = 10;

            if (st.remaining <= 0) {
                it.remove();
                player.displayClientMessage(Component.literal("§7[Démo VFX] terminé."), true);
                continue;
            }
            st.remaining--;

            WeaponVisualEffectId fx;
            String densLabel = "";
            if (st.mode == 2) {
                WeaponVfxVariant.Entry var = WeaponVfxVariant.get(st.cursor % WeaponVfxVariant.COUNT);
                fx = var.effect();
                densLabel = String.format(Locale.ROOT, " §8· §7densité §f%.0f%%", var.density() * 100);
            } else {
                fx = WeaponVisualEffectId.byId(ALL_IDS[st.cursor % ALL_IDS.length]);
            }
            st.cursor++;
            if (fx == null) continue;

            // Cercle de rayon 2,4 blocs autour du joueur : chaque effet occupe une position distincte
            double ang = (st.cursor - 1) * (Math.PI * 2.0 / ALL_IDS.length);
            Vec3 to = player.position().add(Math.cos(ang) * 2.4, 1.05, Math.sin(ang) * 2.4);
            Vec3 from = player.getEyePosition();
            WeaponVfxServerListener.playDebug(player, fx, st.kind, 5, from, to);
            final String extra = densLabel;
            announce(player, "§b#%02d §f%s §8(%s · %s)".formatted(fx.id, fx.name(),
                    fx.element.displayName, fx.category.name().toLowerCase(Locale.ROOT)) + extra);
        }
    }

    // =====================================================================
    // /dlbvfx elements & /dlbvfx variants — FAMILLES ÉLÉMENTAIRES (T350)
    // =====================================================================

    private static int families(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = requirePlayer(ctx);
        src.sendSuccess(() -> Component.literal("§b§lFamilles élémentaires §7(Wu Xing étendu)"), false);
        // T352-ELEMENT-COVERAGE : audit de la couverture élémentaire — 5 enchantements dédiés et
        // au moins 3 grosses animations par famille, avec vérification RÉELLE dans le registre.
        src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "§7Recomptage : §f%d familles §8· §7minimum §f%d enchantements §7dont §f%d à distance §7et §f%d animations §7par famille.",
                DeepElement.values().length,
                deepluckyblock.enchantments.ElementalCatalog.MIN_ENCHANTMENTS,
                deepluckyblock.enchantments.ElementalCatalog.MIN_RANGED,
                deepluckyblock.enchantments.ElementalCatalog.MIN_ANIMATIONS)), false);
        for (DeepElement el : DeepElement.values()) {
            List<WeaponVisualEffectId> anims = WeaponVisualEffectId.animationsOf(el);
            List<String> ench = deepluckyblock.enchantments.ElementalCatalog.enchantmentsOf(el);
            int present = 0;
            for (String path : ench) {
                if (enchantmentExists(player, path)) present++;
            }
            final int animCount = anims.size();
            final int enchPresent = present;
            final int enchTotal = ench.size();
            final boolean ok = enchPresent >= deepluckyblock.enchantments.ElementalCatalog.MIN_ENCHANTMENTS
                    && animCount >= deepluckyblock.enchantments.ElementalCatalog.MIN_ANIMATIONS;
            final int rangedCount = deepluckyblock.enchantments.ElementalCatalog.rangedCount(el);
            String mark = ok ? "§a✔" : "§c✘";
            src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                    "  %s §f%-8s §7%02d anim. §8· §7%d/%d ench. §8· §7%d à distance §8· §7#%06X / #%06X",
                    mark, el.displayName, animCount, enchPresent, enchTotal,
                    rangedCount, el.primary, el.highlight)), false);
        }
        List<DeepElement> missing = deepluckyblock.enchantments.ElementalCatalog.incompleteElements();
        List<DeepElement> noRanged = deepluckyblock.enchantments.ElementalCatalog.elementsWithoutRanged();
        src.sendSuccess(() -> Component.literal(missing.isEmpty()
                ? "§aToutes les familles sont complètes (5 enchantements, 1 à distance, 3 animations)."
                : "§cFamilles incomplètes : §f" + missing), false);
        if (!noRanged.isEmpty()) {
            src.sendSuccess(() -> Component.literal("§cSans enchantement à distance : §f" + noRanged), false);
        }
        // Défilement dans l'ordre des familles
        DemoState st = new DemoState();
        st.cursor = 0;
        st.cooldown = 2;
        st.remaining = ALL_IDS.length;
        st.loop = true;
        st.kind = WeaponVfxServerListener.KIND_IMPACT;
        st.mode = 1;
        st.ringCenter = player.position();
        DEMOS.put(player.getUUID(), st);
        src.sendSuccess(() -> Component.literal("§b[Familles] §fdéfilement en cours, une famille après l'autre §7— §f/dlbvfx stop §7pour arrêter."), false);
        return 1;
    }

    private static int familyShow(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(ctx);
        String raw = StringArgumentType.getString(ctx, "family").toLowerCase(Locale.ROOT);
        DeepElement el = null;
        for (DeepElement e : DeepElement.values()) {
            if (e.name().toLowerCase(Locale.ROOT).equals(raw) || e.displayName.toLowerCase(Locale.ROOT).equals(raw)) {
                el = e;
                break;
            }
        }
        if (el == null) {
            ctx.getSource().sendFailure(Component.literal("§cFamille inconnue. Utilise /dlbvfx elements."));
            return 0;
        }
        ServerLevel sl = player.serverLevel();
        int i = 0;
        int shown = 0;
        for (WeaponVisualEffectId fx : WeaponVisualEffectId.values()) {
            if (fx.element != el) continue;
            Vec3 to = player.position().add(player.getLookAngle().scale(2.5)).add(0, 1.0, 0);
            final WeaponVisualEffectId target = fx;
            final int delay = i * 8;
            final int level = Math.max(3, Math.min(12, fx.priority / 10));
            if (delay == 0) {
                WeaponVfxServerListener.playDebug(player, target, WeaponVfxServerListener.KIND_IMPACT, level,
                        player.getEyePosition(), to);
            } else {
                TestProcedure.schedule(sl, TestProcedure.currentTick(sl) + delay,
                        () -> WeaponVfxServerListener.playDebug(player, target,
                                WeaponVfxServerListener.KIND_IMPACT, level, player.getEyePosition(), to));
            }
            i++;
            shown++;
        }
        final DeepElement fam = el;
        final int total = shown;
        ctx.getSource().sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "§b[%s] §f%d effet(s) enchaînés.", fam.displayName, total)), false);
        return 1;
    }

    // =====================================================================
    // /dlbvfx subtle  — les accents discrets (T351/T353, compteurs dynamiques)
    // =====================================================================

    private static int subtleDemo(CommandContext<CommandSourceStack> ctx, String familyRaw, int indexArg)
            throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(ctx);
        ServerLevel sl = player.serverLevel();
        CommandSourceStack src = ctx.getSource();

        DeepElement wanted = null;
        if (familyRaw != null && !familyRaw.isBlank()) {
            String raw = familyRaw.toLowerCase(Locale.ROOT);
            for (DeepElement e : DeepElement.values()) {
                if (e.name().toLowerCase(Locale.ROOT).equals(raw)
                        || e.displayName.toLowerCase(Locale.ROOT).equals(raw)) {
                    wanted = e;
                    break;
                }
            }
        }

        // T351-SUBTLE-ECHO : espace complet 0..99 (50 élémentaires + 50 échos de grosses animations).
        List<Integer> pool = new ArrayList<>();
        for (int i = 0; i < WeaponVfxSubtle.TOTAL; i++) {
            if (wanted == null || WeaponVfxSubtle.elementOf(i) == wanted) pool.add(i);
        }
        if (pool.isEmpty()) {
            src.sendFailure(Component.literal("§cAucun accent pour cette famille."));
            return 0;
        }

        int index = (indexArg <= 0) ? 1 : indexArg;
        final int chosen = pool.get(Math.floorMod(index - 1, pool.size()));
        Vec3 to = aimPoint(player);
        Vec3 from = player.getEyePosition();
        WeaponVfxServerListener.playSubtleDebug(player, chosen, from, to);

        // Petite salve de démonstration : les autres déclinaisons de la famille, une par 6 ticks.
        int n = Math.min(4, pool.size());
        for (int i = 0; i < n; i++) {
            final int extra = pool.get(Math.floorMod(index + i, pool.size()));
            if (extra == chosen) continue;
            TestProcedure.schedule(sl, TestProcedure.currentTick(sl) + 6 * (i + 1),
                    () -> WeaponVfxServerListener.playSubtleDebug(player, extra,
                            player.getEyePosition(), aimPoint(player)));
        }

        src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "§b%s §7— accent §f#%d §7(%s, %s)",
                WeaponVfxSubtle.nameOf(chosen), chosen + 1,
                WeaponVfxSubtle.elementOf(chosen).displayName,
                WeaponVfxSubtle.detailOf(chosen))), false);
        return 1;
    }

    private static int variantsDemo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack src = ctx.getSource();
        ServerPlayer player = requirePlayer(ctx);
        src.sendSuccess(() -> Component.literal(String.format(Locale.ROOT,
                "§b§l" + WeaponVfxVariant.COUNT + " déclinaisons §7(une pleine par effet + 20 allégées, densité décroissante)")), false);
        for (DeepElement el : DeepElement.values()) {
            List<String> names = new ArrayList<>();
            for (WeaponVfxVariant.Entry e : WeaponVfxVariant.all()) {
                if (e.effect().element != el || !e.lite()) continue;
                names.add(String.format(Locale.ROOT, "§f#%02d§8(§7%.0f%%§8)", e.index() + 1, e.density() * 100));
            }
            if (names.isEmpty()) continue;
            final String joined = String.join(" §8· ", names);
            final String fam = el.displayName;
            src.sendSuccess(() -> Component.literal("  §d" + fam + " §8: " + joined), false);
        }
        DemoState st = new DemoState();
        st.cursor = 0;
        st.cooldown = 2;
        st.remaining = WeaponVfxVariant.COUNT;
        st.loop = true;
        st.kind = WeaponVfxServerListener.KIND_IMPACT;
        st.mode = 2;
        st.ringCenter = player.position();
        DEMOS.put(player.getUUID(), st);
        src.sendSuccess(() -> Component.literal("§b[Variantes] §fdéfilement des 50 déclinaisons §7— §f/dlbvfx stop §7pour arrêter."), false);
        return 1;
    }

    // =====================================================================
    // /dlbvfx crit — CHEMIN CRITIQUE (action joueur normalement requise)
    // =====================================================================

    private static int crit(CommandContext<CommandSourceStack> ctx, WeaponVisualEffectId fx, int level)
            throws CommandSyntaxException {
        if (fx == null) {
            ctx.getSource().sendFailure(Component.literal("§cEffet inconnu (1.." + WeaponVisualEffectId.values().length + ")."));
            return 0;
        }
        ServerPlayer player = requirePlayer(ctx);
        LivingEntity target = ensureDummy(player);
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal("§cImpossible de créer la cible d'entraînement."));
            return 0;
        }
        WeaponVfxServerListener.notifyMeleeCrit(player, target, fx, level);
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§b[Crit] §f#%02d %s §7(niv.%d) sur la cible d'entraînement.".formatted(fx.id, fx.name(), level)), false);
        return 1;
    }

    // =====================================================================
    // /dlbvfx hit — VRAI pipeline de dégâts (impact + crit + enchantements)
    // =====================================================================

    private static int hit(CommandContext<CommandSourceStack> ctx, int times) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(ctx);
        LivingEntity dummy = ensureDummy(player);
        if (dummy == null) {
            ctx.getSource().sendFailure(Component.literal("§cImpossible de créer la cible d'entraînement."));
            return 0;
        }
        ServerLevel sl = player.serverLevel();
        if (player.getMainHandItem().isEmpty()) {
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§7Main vide : les gantelets de plastron (mains nues) sont testés. §f/dlbvfx give <id> §7pour une arme."), false);
        }
        for (int i = 0; i < times; i++) {
            int delay = i * 3; // > 2 ticks pour ne pas être avalé par la garde anti-spam des VFX
            if (delay == 0) {
                strike(player, dummy);
            } else {
                TestProcedure.schedule(sl, TestProcedure.currentTick(sl) + delay, () -> {
                    if (dummy.isAlive()) strike(player, dummy);
                });
            }
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§b[Hit] §f" + times + " frappe(s) §7avec §f" + player.getMainHandItem().getHoverName().getString()
                        + " §7sur la cible d'entraînement."), false);
        return 1;
    }

    /**
     * Frappe identique à un vrai clic gauche : dégâts {@code playerAttack} sur la cible, ce qui
     * traverse tout le pipeline (i-frames, critiques, enchantements, VFX 3D).
     */
    private static void strike(ServerPlayer player, LivingEntity dummy) {
        ServerLevel sl = player.serverLevel();
        float prevFall = player.fallDistance;
        boolean prevGround = player.onGround();
        boolean prevSneak = player.isShiftKeyDown();
        try {
            // Conditions de critique vanilla : en chute, pas au sol, attaque chargée, accroupi (focused_crit)
            player.fallDistance = 1.0f;
            player.setOnGround(false);
            player.setShiftKeyDown(true);
            dummy.invulnerableTime = 0; // ignore les i-frames pour pouvoir enchaîner
            dummy.hurt(sl.damageSources().playerAttack(player), 6.0f);
        } finally {
            player.fallDistance = prevFall;
            player.setOnGround(prevGround);
            player.setShiftKeyDown(prevSneak);
        }
    }

    // =====================================================================
    // CIBLE D'ENTRAÎNEMENT
    // =====================================================================

    private static int dummyToggle(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(ctx);
        List<LivingEntity> existing = findDummies(player, 24.0);
        if (!existing.isEmpty()) {
            for (LivingEntity le : existing) {
                deepluckyblock.util.TrainingDummyTracker.forget(le);
                le.discard();
            }
            ctx.getSource().sendSuccess(() -> Component.literal("§7[Cible] supprimée."), false);
            return 1;
        }
        LivingEntity dummy = ensureDummy(player);
        if (dummy == null) {
            ctx.getSource().sendFailure(Component.literal("§cImpossible de créer la cible d'entraînement."));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§b[Cible] §finvoquée devant toi — §f/dlbvfx hit §7pour frapper."), false);
        return 1;
    }

    private static List<LivingEntity> findDummies(ServerPlayer player, double radius) {
        List<LivingEntity> out = new ArrayList<>();
        if (!(player.level() instanceof ServerLevel sl)) return out;
        for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class, player.getBoundingBox().inflate(radius))) {
            if (le.getPersistentData().getBoolean(DUMMY_TAG)) out.add(le);
        }
        return out;
    }

    /** Récupère la cible d'entraînement la plus proche, sinon en invoque une devant le joueur. */
    private static LivingEntity ensureDummy(ServerPlayer player) {
        ServerLevel sl = player.serverLevel();
        List<LivingEntity> existing = findDummies(player, 32.0);
        if (!existing.isEmpty()) {
            existing.sort((a, b) -> Double.compare(a.distanceToSqr(player), b.distanceToSqr(player)));
            return existing.get(0);
        }

        // T350-DUMMY-PEACEFUL : en mode Paisible, tout monstre hostile est automatiquement retiré
        // par le jeu (Mob#shouldDespawnInPeaceful) -> la cible disparaissait aussitôt. On utilise donc
        // un Villageois inoffensif (jamais supprimé en Paisible) dans ce mode, et on garde le Husk
        // (insensible au feu du soleil) le reste du temps.
        boolean peaceful = sl.getDifficulty() == net.minecraft.world.Difficulty.PEACEFUL;
        Mob mob;
        if (peaceful) {
            mob = EntityType.VILLAGER.create(player.level());
        } else {
            mob = EntityType.HUSK.create(player.level());
        }
        if (mob == null) return null;
        Vec3 look = player.getLookAngle();
        double dist = 2.5;
        HitResult ahead = player.pick(3.2, 0.0f, false);
        if (ahead.getType() == HitResult.Type.BLOCK) {
            dist = Math.max(1.0, Math.sqrt(ahead.getLocation().distanceToSqr(player.getEyePosition())) - 0.9);
        }
        double x = player.getX() + look.x * dist;
        double z = player.getZ() + look.z * dist;
        mob.moveTo(x, player.getY(), z, player.getYRot() + 180.0f, 0.0f);
        mob.setNoAi(true);
        mob.setSilent(true);
        mob.setPersistenceRequired();
        mob.setCustomName(Component.literal("§bDLB §7· Cible VFX"));
        mob.setCustomNameVisible(true);
        mob.setItemSlot(EquipmentSlot.HEAD, ItemStack.EMPTY);
        mob.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        AttributeInstance hp = mob.getAttribute(Attributes.MAX_HEALTH);
        if (hp != null) {
            hp.setBaseValue(100_000.0);
        }
        mob.setHealth(100_000.0f);
        mob.getPersistentData().putBoolean(DUMMY_TAG, true);
        if (peaceful) {
            mob.setInvulnerable(false);
            mob.setRemainingFireTicks(0);
        }
        if (!sl.addFreshEntity(mob)) return null;
        // T350-DUMMY-DPS : affichage du DPS (dégâts encaissés sur 1 s) au-dessus de la tête
        deepluckyblock.util.TrainingDummyTracker.track(mob);
        return mob;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = requirePlayer(ctx);
        int removed = 0;
        for (ServerLevel sl : ctx.getSource().getServer().getAllLevels()) {
            for (LivingEntity le : sl.getEntitiesOfClass(LivingEntity.class,
                    player.getBoundingBox().inflate(128.0))) {
                if (le.getPersistentData().getBoolean(DUMMY_TAG)) {
                    deepluckyblock.util.TrainingDummyTracker.forget(le);
                    le.discard();
                    removed++;
                }
            }
        }
        DEMOS.remove(player.getUUID());
        final int n = removed;
        ctx.getSource().sendSuccess(() -> Component.literal("§7[Cible] " + n + " cible(s) supprimée(s)."), false);
        return 1;
    }

    // =====================================================================
    // /dlbvfx give — les ITEMS qui exigent une action joueur
    // =====================================================================

    private record EnchantPick(String key, Holder<Enchantment> holder, boolean gauntlet) {}

    private static EnchantPick resolveEnchant(ServerPlayer player, WeaponVisualEffectId fx) {
        RegistryAccess access = player.serverLevel().registryAccess();

        for (String key : WeaponVisualEffectId.enchantKeysFor(fx)) {
            Enchantment ench = DeepEnchant.byId(access, key);
            Holder<Enchantment> holder = ench == null ? null : DeepEnchant.holder(access, ench);
            if (holder != null) return new EnchantPick(key, holder, false);
        }
        for (String key : WeaponVisualEffectId.gauntletKeysFor(fx)) {
            Enchantment ench = DeepEnchant.byId(access, key);
            Holder<Enchantment> holder = ench == null ? null : DeepEnchant.holder(access, ench);
            if (holder != null) return new EnchantPick(key, holder, true);
        }
        return null;
    }

    private static int give(CommandContext<CommandSourceStack> ctx, WeaponVisualEffectId fx, int level)
            throws CommandSyntaxException {
        if (fx == null) {
            ctx.getSource().sendFailure(Component.literal("§cEffet inconnu (1.." + WeaponVisualEffectId.values().length + ")."));
            return 0;
        }
        ServerPlayer player = requirePlayer(ctx);
        EnchantPick pick = resolveEnchant(player, fx);
        if (pick == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "§cAucun enchantement enregistré pour l'effet #" + fx.id + " (" + fx.name() + ")."));
            return 0;
        }

        List<ItemStack> gifts = new ArrayList<>(3);
        if (pick.gauntlet()) {
            gifts.add(enchanted(Items.NETHERITE_CHESTPLATE, pick.holder(), level));
            ctx.getSource().sendSuccess(() -> Component.literal(
                    "§7Gantelet : équipe le plastron et frappe §fmains nues§7."), false);
        } else if (fx.category == WeaponVisualEffectId.Category.RANGED) {
            gifts.add(enchanted(Items.BOW, pick.holder(), level));
            gifts.add(enchanted(Items.CROSSBOW, pick.holder(), level));
        } else {
            gifts.add(enchanted(Items.NETHERITE_SWORD, pick.holder(), level));
            gifts.add(enchanted(Items.TRIDENT, pick.holder(), level));
        }

        for (ItemStack stack : gifts) {
            if (!player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }

        int colon = pick.key().indexOf(':');
        String keyPath = colon >= 0 ? pick.key().substring(colon + 1) : pick.key();
        String enchantName = keyPath.replace('_', ' ');
        String recipe = switch (fx) {
            case WARLORD_RALLY_SIGIL -> "clic droit avec l'arme en main";
            case GOLDEN_CRIT_STAR, FOCUSED_BULLSEYE, OVERCLOCK_CYBER_X -> "frapper en crit (saute + frappe chargée, ou /dlbvfx hit)";
            case ASTRAL_STAR_LANCE, VOID_WARP_SPHERE, DRAGON_CHAOS_PRISM -> "tirer à l'arc / arbalète, ou /dlbvfx play " + fx.id + " proj";
            default -> "frapper une cible, ou /dlbvfx play " + fx.id;
        };
        ctx.getSource().sendSuccess(() -> Component.literal(
                "§aDonné §f" + gifts.size() + " objet(s) §7avec §f" + enchantName + " " + level
                        + " §8(" + pick.key() + ")"), false);
        ctx.getSource().sendSuccess(() -> Component.literal("§7Déclenchement : §f" + recipe), false);
        return 1;
    }

    private static ItemStack enchanted(Item item, Holder<Enchantment> holder, int level) {
        ItemStack stack = new ItemStack(item);
        DeepEnchant.setLevel(stack, holder, Math.max(1, level));
        return stack;
    }
}
