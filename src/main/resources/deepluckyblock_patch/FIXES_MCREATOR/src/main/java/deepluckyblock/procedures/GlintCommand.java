package deepluckyblock.procedures;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import deepluckyblock.util.DeepNbt;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * /glint — pose ou retire une aura d'enchantement COLORÉE sur l'item tenu en
 * main principale, sans dépendre du RNG du Lucky Block.
 *
 * <pre>
 *   /glint &lt;couleur&gt;         pose la couleur sur l'item en main
 *   /glint clear              retire le halo, même sur un item DÉJÀ enchanté
 * </pre>
 *
 * <p><b>Couleurs acceptées</b> :
 * <ul>
 *   <li>les 16 noms de couleur Minecraft (blanc, orange, magenta, bleu_clair,
 *       jaune, vert_clair/lime, rose, gris, gris_clair, cyan, violet, bleu,
 *       marron, vert, rouge, noir — alias anglais acceptés aussi : white,
 *       orange, magenta, light_blue, yellow, lime, pink, gray/grey,
 *       light_gray/light_grey, cyan, purple, blue, brown, green, red, black) ;</li>
 *   <li>un mélange de deux couleurs nommées, séparées par {@code +}
 *       (ex. {@code /glint red+blue}) : moyenne RGB des deux teintes ;</li>
 *   <li>un code hexadécimal précis, avec ou sans {@code #}
 *       (ex. {@code /glint #ff00aa} ou {@code /glint ff00aa}).</li>
 * </ul>
 *
 * <p><b>Fonctionnement technique</b> : la couleur est écrite dans le tag NBT
 * {@link deepluckyblock.client.EnchantAura#GLINT_FORCE_NBT_KEY}, lu en
 * PRIORITÉ ABSOLUE par {@code EnchantAura.auraColor} (avant même la couleur de
 * rang du Lucky Block). Le rendu réel du halo teinté passe ensuite par le
 * pipeline déjà en place ({@code EnchantGlintTyped} + les mixins de rendu),
 * inchangé par cette commande.
 *
 * <p>Pour qu'un item SANS AUCUN enchantement affiche quand même un halo, on
 * pose en plus le composant vanilla officiel
 * {@code DataComponents.ENCHANTMENT_GLINT_OVERRIDE = true}
 * (voir {@code ItemStack.hasFoil()}, vérifié par désassemblage du jar
 * NeoForge 1.21.1 : il court-circuite {@code isEnchanted()}). {@code /glint
 * clear} retire ce composant ET force
 * {@code ENCHANTMENT_GLINT_OVERRIDE = false} pour masquer le halo même sur un
 * item réellement enchanté (qui aurait sinon gardé son glint violet vanilla).
 */
@EventBusSubscriber(modid = "deep_lucky_block")
public final class GlintCommand {

    private GlintCommand() {}

    private static final SimpleCommandExceptionType ERR_NO_ITEM =
            new SimpleCommandExceptionType(Component.literal("Vous devez tenir un item en main principale."));
    private static final SimpleCommandExceptionType ERR_BAD_COLOR =
            new SimpleCommandExceptionType(Component.literal(
                    "Couleur inconnue. Utilise un nom (rouge, bleu, cyan...), un mélange (rouge+bleu) ou un hex (#ff00aa)."));

    // =======================================================================
    // Table des 16 couleurs Minecraft (noms FR + alias EN), valeurs alignées
    // sur ChatFormatting.getColor() (mêmes RVB que le rendu du chat/glint
    // teinté du mod, pour rester cohérent visuellement).
    // =======================================================================
    private static final Map<String, Integer> NAMED = buildNamed();

    private static Map<String, Integer> buildNamed() {
        Map<String, Integer> m = new HashMap<>();
        put(m, 0xFFFFFF, "blanc", "white");
        put(m, 0xFF8000, "orange");
        put(m, 0xC800C8, "magenta");
        put(m, 0x6699D8, "bleu_clair", "light_blue");
        put(m, 0xFFFF00, "jaune", "yellow");
        put(m, 0x80FF00, "vert_clair", "lime");
        put(m, 0xFFC0CB, "rose", "pink");
        put(m, 0x808080, "gris", "gray", "grey");
        put(m, 0xD3D3D3, "gris_clair", "light_gray", "light_grey");
        put(m, 0x00FFFF, "cyan");
        put(m, 0x8000FF, "violet", "purple");
        put(m, 0x0000FF, "bleu", "blue");
        put(m, 0x664C33, "marron", "brown");
        put(m, 0x00AA00, "vert", "green");
        put(m, 0xFF0000, "rouge", "red");
        put(m, 0x000000, "noir", "black");
        // Alias supplémentaires pratiques (pas des couleurs vanilla à part
        // entière, mais fréquemment demandés).
        put(m, 0xFFD700, "or", "gold");
        put(m, 0x00AAAA, "aqua");
        return m;
    }

    private static void put(Map<String, Integer> m, int rgb, String... names) {
        for (String n : names) m.put(n, rgb);
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("glint")
                .requires(src -> src.hasPermission(2))
                .then(Commands.argument("couleur", StringArgumentType.greedyString())
                    .suggests((ctx, builder) -> {
                        builder.suggest("+");
                        builder.suggest("-");
                        builder.suggest("clear");
                        for (String name : NAMED.keySet()) {
                            builder.suggest(name);
                        }
                        return builder.buildFuture();
                    })
                    .executes(ctx -> run(ctx.getSource(), StringArgumentType.getString(ctx, "couleur"))))
        );
    }

    private static int run(CommandSourceStack source, String rawArg) throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Cette commande doit être exécutée par un joueur."));
            return 0;
        }

        ItemStack held = player.getItemInHand(InteractionHand.MAIN_HAND);
        if (held == null || held.isEmpty()) {
            throw ERR_NO_ITEM.create();
        }

        deepluckyblock.util.DeepEnchant.rememberServerAccess(player.serverLevel().registryAccess());
        String arg = rawArg.trim().toLowerCase(Locale.ROOT);

        if (arg.equals("clear") || arg.equals("clr") || arg.equals("retirer") || arg.equals("effacer")) {
            clearGlint(held);
            deepluckyblock.client.EnchantAura.invalidateCache(held);
            source.sendSuccess(() -> Component.literal("§7Halo d'enchantement retiré de ")
                    .append(held.getHoverName().copy().withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("§7.")), true);
            player.inventoryMenu.broadcastChanges();
            return 1;
        }

        // T324-GLINT-TIERS : /glint + et /glint - (1 à 20 paliers de visibilité sur le glint coloré actuel)
        Integer stepDelta = parseVisibilityDelta(arg);
        if (stepDelta != null) {
            return adjustGlintVisibility(source, player, held, stepDelta);
        }

        Integer rgb = resolveColor(arg);
        if (rgb == null) {
            throw ERR_BAD_COLOR.create();
        }

        applyGlint(held, rgb);
        deepluckyblock.client.EnchantAura.invalidateCache(held);
        int finalRgb = rgb;
        int curTier = deepluckyblock.client.EnchantAura.glintTier(held);
        source.sendSuccess(() -> Component.literal("§7Halo d'enchantement ")
                .append(Component.literal("■").withStyle(net.minecraft.network.chat.Style.EMPTY
                        .withColor(net.minecraft.network.chat.TextColor.fromRgb(finalRgb))))
                .append(Component.literal(" §7appliqué à "))
                .append(held.getHoverName().copy().withStyle(ChatFormatting.GRAY))
                .append(Component.literal(" §8(#" + String.format("%06X", finalRgb)
                        + " · Palier " + curTier + "/" + deepluckyblock.client.EnchantAura.MAX_GLINT_TIER + ")")), true);
        player.inventoryMenu.broadcastChanges();
        return 1;
    }

    /**
     * Analyse "/glint +", "/glint -", "/glint +N" ou "/glint -N" sans interférer
     * avec les mélanges de couleurs nommées du type "red+blue".
     */
    private static Integer parseVisibilityDelta(String arg) {
        if (arg.equals("+") || arg.equals("plus") || arg.equals("up")) return 1;
        if (arg.equals("-") || arg.equals("minus") || arg.equals("moins") || arg.equals("down")) return -1;
        if (arg.startsWith("+") || arg.startsWith("-")) {
            int sign = arg.startsWith("+") ? 1 : -1;
            String rest = arg.substring(1).trim();
            if (rest.matches("\\d+")) {
                try {
                    int n = Math.max(1, Math.min(20, Integer.parseInt(rest)));
                    return sign * n;
                } catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    private static int adjustGlintVisibility(CommandSourceStack source, ServerPlayer player,
                                             ItemStack held, int delta) {
        // Réactive l'affichage du halo s'il avait été masqué par /glint clear
        if (Boolean.FALSE.equals(held.get(DataComponents.ENCHANTMENT_GLINT_OVERRIDE))) {
            if (held.isEnchanted()) {
                held.remove(DataComponents.ENCHANTMENT_GLINT_OVERRIDE);
            } else {
                held.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
            }
        } else if (!held.hasFoil()) {
            held.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
        }

        // Conserve la couleur actuelle du glint ; si l'item n'avait qu'un glint violet vanilla (0),
        // pose le violet arcanique par défaut pour que les 20 paliers s'appliquent immédiatement.
        deepluckyblock.client.EnchantAura.invalidateCache(held);
        int activeColor = deepluckyblock.client.EnchantAura.auraColor(held) & 0xFFFFFF;
        if (activeColor == 0) {
            CompoundTag checkTag = DeepNbt.tag(held);
            boolean forcedBlack = checkTag != null
                    && checkTag.contains(deepluckyblock.client.EnchantAura.GLINT_FORCE_NBT_KEY, 99)
                    && (checkTag.getInt(deepluckyblock.client.EnchantAura.GLINT_FORCE_NBT_KEY) & 0xFFFFFF) == 0;
            if (!forcedBlack) {
                activeColor = 0x9945FF;
                CompoundTag tag = DeepNbt.getOrCreateTag(held);
                tag.putInt(deepluckyblock.client.EnchantAura.GLINT_FORCE_NBT_KEY, 0xFF000000 | activeColor);
                held.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
            }
        }

        int oldTier = deepluckyblock.client.EnchantAura.glintTier(held);
        int newTier = Math.max(deepluckyblock.client.EnchantAura.MIN_GLINT_TIER,
                Math.min(deepluckyblock.client.EnchantAura.MAX_GLINT_TIER, oldTier + delta));

        CompoundTag tag = DeepNbt.getOrCreateTag(held);
        tag.putInt(deepluckyblock.client.EnchantAura.GLINT_TIER_NBT_KEY, newTier);
        deepluckyblock.client.EnchantAura.invalidateCache(held);
        player.inventoryMenu.broadcastChanges();

        final int displayRgb = activeColor == 0 ? 0x555555 : activeColor;
        final int shownTier = newTier;
        final boolean atBound = (newTier == oldTier);
        final String bar = buildTierBar(shownTier, deepluckyblock.client.EnchantAura.MAX_GLINT_TIER);
        final String boundNote = atBound
                ? (delta > 0 ? " §6(MAX)" : " §8(MIN)")
                : (delta > 0 ? " §a(+" + (newTier - oldTier) + ")" : " §e(" + (newTier - oldTier) + ")");

        source.sendSuccess(() -> Component.literal("§b✦ §7Visibilité du glint ")
                .append(Component.literal("■").withStyle(net.minecraft.network.chat.Style.EMPTY
                        .withColor(net.minecraft.network.chat.TextColor.fromRgb(displayRgb))))
                .append(Component.literal(" §7: " + bar + " §fPalier §b" + shownTier + "§7/§b"
                        + deepluckyblock.client.EnchantAura.MAX_GLINT_TIER + boundNote)), false);
        return 1;
    }

    private static String buildTierBar(int tier, int max) {
        StringBuilder sb = new StringBuilder("§8[§b");
        for (int i = 1; i <= max; i++) {
            if (i == tier + 1) sb.append("§8");
            sb.append('|');
        }
        sb.append("§8]");
        return sb.toString();
    }

    /**
     * Résout une couleur nommée, un mélange "a+b" ou un hex "#rrggbb"/"rrggbb".
     * Renvoie {@code null} si l'entrée n'est reconnaissable d'aucune façon.
     */
    private static Integer resolveColor(String arg) {
        // Hex : avec ou sans '#', 6 chiffres hexadécimaux.
        String hexCandidate = arg.startsWith("#") ? arg.substring(1) : arg;
        if (hexCandidate.matches("[0-9a-f]{6}")) {
            try {
                return Integer.parseInt(hexCandidate, 16) & 0xFFFFFF;
            } catch (NumberFormatException ignored) {
                // continue vers les autres formats
            }
        }
        // Mélange "couleur1+couleur2" (moyenne RGB composante par composante).
        if (arg.contains("+")) {
            String[] parts = arg.split("\\+", 2);
            Integer a = resolveSingleNamedOrHex(parts[0].trim());
            Integer b = parts.length > 1 ? resolveSingleNamedOrHex(parts[1].trim()) : null;
            if (a != null && b != null) {
                return mix(a, b);
            }
            return null;
        }
        // Nom simple.
        return NAMED.get(arg);
    }

    private static Integer resolveSingleNamedOrHex(String s) {
        if (s.isEmpty()) return null;
        String hex = s.startsWith("#") ? s.substring(1) : s;
        if (hex.matches("[0-9a-f]{6}")) {
            try {
                return Integer.parseInt(hex, 16) & 0xFFFFFF;
            } catch (NumberFormatException ignored) {}
        }
        return NAMED.get(s);
    }

    private static int mix(int a, int b) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (ar + br) / 2, g = (ag + bg) / 2, bl = (ab + bb) / 2;
        return (r << 16) | (g << 8) | bl;
    }

    /** Pose la couleur forcée + force le halo visible même sans enchantement. */
    private static void applyGlint(ItemStack stack, int rgb) {
        CompoundTag tag = DeepNbt.getOrCreateTag(stack);
        tag.putInt(deepluckyblock.client.EnchantAura.GLINT_FORCE_NBT_KEY, 0xFF000000 | (rgb & 0xFFFFFF));
        // Composant VANILLA officiel (vérifié 1.21.1) : force ItemStack.hasFoil()
        // à true indépendamment de isEnchanted(), donc le halo est visible même
        // sur un bâton en bois tout nu.
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.TRUE);
    }

    /** Retire la couleur forcée ET masque le halo, y compris sur un item réellement enchanté. */
    private static void clearGlint(ItemStack stack) {
        CompoundTag tag = DeepNbt.tag(stack);
        if (tag != null) {
            tag.remove(deepluckyblock.client.EnchantAura.GLINT_FORCE_NBT_KEY);
            tag.remove(deepluckyblock.client.EnchantAura.GLINT_TIER_NBT_KEY);
        }
        // false (et non simplement "remove") : un item réellement enchanté
        // reste enchanté (les effets restent actifs), mais son halo visuel
        // vanilla est explicitement masqué, comme demandé ("même si déjà
        // enchanté").
        stack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, Boolean.FALSE);
    }
}
