package deepluckyblock.enchantments;

import deepluckyblock.util.DeepEnchant;
import deepluckyblock.util.TranslationFallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * T308-COMBOS : Registre central des 30 Combos & Synergies Uniques Multi-Pièces.
 *
 * Chaque combo exige simultanément des enchantements portés sur des PIÈCES DISTINCTES
 * (Casque, Plastron/Élytres, Jambières, Bottes, Arme/Outil en main principale, Bouclier en main secondaire)
 * et débloque un pouvoir unique dédié géré dans {@link EnchantmentComboHandler}.
 */
public final class EnchantmentComboRegistry {

    private EnchantmentComboRegistry() {}

    public record PieceRequirement(EquipmentSlot slot, Item sampleItem, List<String> acceptedPaths) {
        public String primaryPath() {
            return acceptedPaths.get(0);
        }

        public boolean matches(ItemStack stack) {
            if (stack == null || stack.isEmpty() || !stack.isEnchanted()) return false;
            net.minecraft.world.item.enchantment.ItemEnchantments ie =
                    stack.get(net.minecraft.core.component.DataComponents.ENCHANTMENTS);
            if (ie == null || ie.isEmpty()) return false;
            for (var entry : ie.entrySet()) {
                if (entry.getIntValue() <= 0) continue;
                var keyOpt = entry.getKey().unwrapKey();
                if (keyOpt.isPresent()) {
                    net.minecraft.resources.ResourceLocation loc = keyOpt.get().location();
                    if ("deep_lucky_block".equals(loc.getNamespace()) || "supreme_lucky_block".equals(loc.getNamespace())) {
                        String path = loc.getPath();
                        for (String accepted : acceptedPaths) {
                            if (accepted.equals(path)) return true;
                        }
                    }
                }
            }
            return false;
        }
    }

    public record ComboDef(int index, String id, int rgb, List<PieceRequirement> pieces) {
        public long bit() {
            return 1L << index;
        }

        public String nameKey() {
            return "combo.deep_lucky_block." + id + ".name";
        }

        public String descKey() {
            return "combo.deep_lucky_block." + id + ".desc";
        }

        public MutableComponent translatedName() {
            return TranslationFallback.translatableOrFallback(nameKey(), id.replace('_', ' '))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withBold(true));
        }

        public MutableComponent translatedDesc() {
            return TranslationFallback.translatableOrFallback(descKey(), "");
        }

        public boolean matchesEntity(LivingEntity entity) {
            if (entity == null) return false;
            for (PieceRequirement req : pieces) {
                if (!req.matches(entity.getItemBySlot(req.slot()))) {
                    return false;
                }
            }
            return true;
        }
    }

    private static PieceRequirement req(EquipmentSlot slot, Item sample, String... paths) {
        return new PieceRequirement(slot, sample, List.of(paths));
    }

    // =========================================================================
    // LES 30 COMBOS UNIQUES MULTI-PIÈCES (Index 0 .. 29)
    // =========================================================================
    public static final int C01_ACROBAT_ALCHIMIQUE       = 0;
    public static final int C02_ASCENSION_CELESTE        = 1;
    public static final int C03_COLOSSE_FRANCHISSEUR     = 2;
    public static final int C04_PATINEUR_AZOTE           = 3;
    public static final int C05_ANGE_PLUME_CELESTE       = 4;
    public static final int C06_PONT_DIMENSIONNEL        = 5;
    public static final int C07_SEIGNEUR_VAMPIRE         = 6;
    public static final int C08_ASSASSIN_NOCTURNE        = 7;
    public static final int C09_EMPEREUR_LEGION          = 8;
    public static final int C10_FAUCHEUR_TEMPETE         = 9;
    public static final int C11_TITAN_TELLURIQUE         = 10;
    public static final int C12_BERSERKER_IMMORTEL       = 11;
    public static final int C13_DUELLISTE_ECLAIR         = 12;
    public static final int C14_BASTION_MIROIR           = 13;
    public static final int C15_CITADELLE_VIVANTE        = 14;
    public static final int C16_VENGEANCE_WARDEN         = 15;
    public static final int C17_AVATAR_INFERNAL          = 16;
    public static final int C18_SEIGNEUR_ABYSSES         = 17;
    public static final int C19_FAUCON_OURAGAN           = 18;
    public static final int C20_RENAISSANCE_SOLAIRE      = 19;
    public static final int C21_OEIL_FAUCON_ASTRAL       = 20;
    public static final int C22_PLUIE_CATACLYSMIQUE      = 21;
    public static final int C23_COMMANDO_GRAPPIN         = 22;
    public static final int C24_ARTILLEUR_INQUISITEUR    = 23;
    public static final int C25_SPECTRE_DIMENSIONNEL     = 24;
    public static final int C26_COEUR_FORGE_NAINE        = 25;
    public static final int C27_BUCHERON_SYLVESTRE       = 26;
    public static final int C28_MOISSONNEUR_ABONDANCE    = 27;
    public static final int C29_AMIRAL_HAUTURIER         = 28;
    public static final int C30_RELIQUE_ROI_TRANSCENDANT = 29;

    public static final List<ComboDef> COMBOS;
    private static final Map<String, List<ComboDef>> BY_ENCHANT_PATH;

    static {
        List<ComboDef> list = new ArrayList<>(30);

        // --- I. Mobilité, Parkour & Physique Aérienne (1..6) ---
        list.add(new ComboDef(C01_ACROBAT_ALCHIMIQUE, "acrobat_alchimique", 0x76FF4D, List.of(
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, "wall_runner"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, "bouncy"))));

        list.add(new ComboDef(C02_ASCENSION_CELESTE, "ascension_celeste", 0x99EEFF, List.of(
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, "cloud_walker"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, "leap_power", "basic_leap_power"))));

        list.add(new ComboDef(C03_COLOSSE_FRANCHISSEUR, "colosse_franchisseur", 0xFFAA22, List.of(
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, "giant_step", "big_step"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, "meteor_stomp"))));

        list.add(new ComboDef(C04_PATINEUR_AZOTE, "patineur_azote", 0x55E8FF, List.of(
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, "slide"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, "frost_trail"))));

        list.add(new ComboDef(C05_ANGE_PLUME_CELESTE, "ange_plume_celeste", 0xFFE680, List.of(
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, "lightness", "basic_lightness"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, "radiant_ward"))));

        list.add(new ComboDef(C06_PONT_DIMENSIONNEL, "pont_dimensionnel", 0xBBCCFF, List.of(
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, "foot_placement"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, "dash"))));

        // --- II. Épée & Mêlée Multi-Pièces (7..13) ---
        list.add(new ComboDef(C07_SEIGNEUR_VAMPIRE, "seigneur_vampire", 0xE61938, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, "blood_drinker_edge", "bloodthirsty", "bloodthirst"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, "assimilation"))));

        list.add(new ComboDef(C08_ASSASSIN_NOCTURNE, "assassin_nocturne", 0x9B4DFF, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, "shadow_hunt"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, "phantom_step"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, "night_veil"))));

        list.add(new ComboDef(C09_EMPEREUR_LEGION, "empereur_legion", 0xFFD24D, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, "summon_army", "army_summon"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, "commander"))));

        list.add(new ComboDef(C10_FAUCHEUR_TEMPETE, "faucheur_tempete", 0x33F0FF, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, "thundercall_pact"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, "electric_heart"))));

        list.add(new ComboDef(C11_TITAN_TELLURIQUE, "titan_tellurique", 0xC8965A, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, "titans_grasp"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, "earth_heart"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, "stonebound_roots"))));

        list.add(new ComboDef(C12_BERSERKER_IMMORTEL, "berserker_immortel", 0xFF3B30, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, "berserk"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, "adrenaline"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, "last_stand_escape"))));

        list.add(new ComboDef(C13_DUELLISTE_ECLAIR, "duelliste_eclair", 0x40FFFF, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, "swift_strike"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, "evasive_footwork"))));

        // --- III. Bouclier & Défense Active (14..16) ---
        list.add(new ComboDef(C14_BASTION_MIROIR, "bastion_miroir", 0xE2E8F0, List.of(
                req(EquipmentSlot.OFFHAND, Items.SHIELD, "perfect_parry"),
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, "guard_breaker"))));

        list.add(new ComboDef(C15_CITADELLE_VIVANTE, "citadelle_vivante", 0x94A3B8, List.of(
                req(EquipmentSlot.OFFHAND, Items.SHIELD, "shield_quake"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, "bastion_heart"))));

        list.add(new ComboDef(C16_VENGEANCE_WARDEN, "vengeance_warden", 0x00C8B4, List.of(
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, "wardenspine"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, "perception", "echolocation"))));

        // --- IV. Élémentaires Feu, Océan, Zéphyr & Phénix (17..20) ---
        list.add(new ComboDef(C17_AVATAR_INFERNAL, "avatar_infernal", 0xFF6A1A, List.of(
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, "infernal_armor"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, "magma_walker", "blaze_walker"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, "ember_core"))));

        list.add(new ComboDef(C18_SEIGNEUR_ABYSSES, "seigneur_abysses", 0x1EA7FF, List.of(
                req(EquipmentSlot.MAINHAND, Items.TRIDENT, "tidecaller", "ocean_current"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, "conduit_heart"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, "buoy"))));

        list.add(new ComboDef(C19_FAUCON_OURAGAN, "faucon_ouragan", 0x80D8FF, List.of(
                req(EquipmentSlot.CHEST, Items.ELYTRA, "rocket_blast", "wind_rider"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, "meteor_stomp"))));

        list.add(new ComboDef(C20_RENAISSANCE_SOLAIRE, "renaissance_solaire", 0xFFCC33, List.of(
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, "final_breath"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, "priests_blessing"))));

        // --- V. Arcs & Arbalètes de Précision (21..25) ---
        list.add(new ComboDef(C21_OEIL_FAUCON_ASTRAL, "oeil_faucon_astral", 0x7CF3A0, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, "sniper"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, "perception", "concentration"))));

        list.add(new ComboDef(C22_PLUIE_CATACLYSMIQUE, "pluie_cataclysmique", 0xFF5522, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, "meteor"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, "gale"))));

        list.add(new ComboDef(C23_COMMANDO_GRAPPIN, "commando_grappin", 0xA8E6CF, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, "grapple"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, "wall_runner"))));

        list.add(new ComboDef(C24_ARTILLEUR_INQUISITEUR, "artilleur_inquisiteur", 0xFF884D, List.of(
                req(EquipmentSlot.MAINHAND, Items.CROSSBOW, "brimstone", "peregrine"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, "hunters_pace"))));

        list.add(new ComboDef(C25_SPECTRE_DIMENSIONNEL, "spectre_dimensionnel", 0xC084FC, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, "teleport_arrow", "swap_arrow"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, "dimensional_step"))));

        // --- VI. Outils, Récolte, Pêche & Souveraineté Royale (26..30) ---
        list.add(new ComboDef(C26_COEUR_FORGE_NAINE, "coeur_forge_naine", 0x38BDF8, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_PICKAXE, "extracting", "vein_miner"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, "miners_guidance"))));

        list.add(new ComboDef(C27_BUCHERON_SYLVESTRE, "bucheron_sylvestre", 0x4ADE80, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_AXE, "lumberjack", "tree_felling"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, "trailblazer"))));

        list.add(new ComboDef(C28_MOISSONNEUR_ABONDANCE, "moissonneur_abondance", 0x86EFAC, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_HOE, "bountiful_harvest", "farmers_hoe"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, "food_is_heaven", "rain_nourishment"))));

        list.add(new ComboDef(C29_AMIRAL_HAUTURIER, "amiral_hauturier", 0x22D3EE, List.of(
                req(EquipmentSlot.MAINHAND, Items.FISHING_ROD, "thunder_hook", "anglers_blessing", "dredge"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, "oceans_blessing", "amphibious"))));

        list.add(new ComboDef(C30_RELIQUE_ROI_TRANSCENDANT, "armure_eternelle_transcendante", 0xFFD700, List.of(
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, "king"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, "divine_protection"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, "giant_step"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, "cloud_walker"))));

        COMBOS = Collections.unmodifiableList(list);

        Map<String, List<ComboDef>> map = new HashMap<>();
        for (ComboDef c : COMBOS) {
            for (PieceRequirement p : c.pieces()) {
                for (String path : p.acceptedPaths()) {
                    map.computeIfAbsent(path.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(c);
                }
            }
        }
        BY_ENCHANT_PATH = Collections.unmodifiableMap(map);
    }

    // Cache ultra-léger de masque binaire 30 bits par entité et par côté (Client/Serveur)
    private static final class MaskCache {
        long mask;
        int computedTick = -999;
        long lastGameTime;
    }

    private static final Map<Long, MaskCache> MASK_CACHE = new ConcurrentHashMap<>();

    private static long sideKey(LivingEntity entity) {
        long side = entity.level().isClientSide() ? 1L : 0L;
        return (side << 32) | (entity.getId() & 0xFFFFFFFFL);
    }

    public static long getActiveMask(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) return 0L;
        long key = sideKey(entity);
        MaskCache mc = MASK_CACHE.computeIfAbsent(key, k -> new MaskCache());
        int tick = entity.tickCount;
        if (Math.abs(tick - mc.computedTick) < 5) {
            return mc.mask;
        }
        long now = entity.level().getGameTime();
        if ((tick & 255) == 0 && MASK_CACHE.size() > 256) {
            MASK_CACHE.entrySet().removeIf(e -> now - e.getValue().lastGameTime > 600L);
        }
        long m = computeRawMask(entity);
        mc.mask = m;
        mc.computedTick = tick;
        mc.lastGameTime = now;
        return m;
    }

    public static long computeRawMask(LivingEntity entity) {
        if (entity == null) return 0L;
        long m = 0L;
        for (ComboDef c : COMBOS) {
            if (c.matchesEntity(entity)) {
                m |= c.bit();
            }
        }
        return m;
    }

    public static boolean hasCombo(LivingEntity entity, int comboIdx) {
        if (entity == null || comboIdx < 0 || comboIdx >= COMBOS.size()) return false;
        return (getActiveMask(entity) & (1L << comboIdx)) != 0L;
    }

    public static List<ComboDef> combosForEnchantPath(String path) {
        if (path == null || path.isEmpty()) return List.of();
        return BY_ENCHANT_PATH.getOrDefault(path.toLowerCase(Locale.ROOT), List.of());
    }

    public static String slotLabelFrOrEn(EquipmentSlot slot) {
        String key = "tooltip.deep_lucky_block.slot." + slot.getName().toLowerCase(Locale.ROOT);
        String fallback = switch (slot) {
            case HEAD -> "Casque";
            case CHEST -> "Plastron/\u00c9lytres";
            case LEGS -> "Jambi\u00e8res";
            case FEET -> "Bottes";
            case MAINHAND -> "Main principale";
            case OFFHAND -> "Main secondaire";
            default -> slot.getName();
        };
        return TranslationFallback.resolve(key) != null ? TranslationFallback.resolve(key) : fallback;
    }

    public static Component buildRequirementsSummary(ComboDef combo, LivingEntity viewer) {
        MutableComponent out = Component.empty();
        List<PieceRequirement> pcs = combo.pieces();
        for (int i = 0; i < pcs.size(); i++) {
            PieceRequirement req = pcs.get(i);
            boolean satisfied = viewer != null && req.matches(viewer.getItemBySlot(req.slot()));
            String enchKey = "enchantment.deep_lucky_block." + req.primaryPath();
            MutableComponent enchName = TranslationFallback.translatableOrFallback(enchKey, req.primaryPath().replace('_', ' '));
            MutableComponent part = Component.literal(slotLabelFrOrEn(req.slot()) + ": ")
                    .withStyle(ChatFormatting.GRAY)
                    .append(enchName.withStyle(satisfied ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
            if (i > 0) out.append(Component.literal(" + ").withStyle(ChatFormatting.DARK_GRAY));
            out.append(part);
        }
        return out;
    }

    /**
     * Génère les lignes de synergie à afficher dans l'infobulle d'un objet enchanté.
     */
    public static List<Component> buildTooltipComboLines(ItemStack stack, Player viewer, boolean shiftHeld) {
        if (stack == null || stack.isEmpty() || !stack.isEnchanted()) return List.of();
        Map<Enchantment, Integer> enchMap = DeepEnchant.map(stack);
        if (enchMap.isEmpty()) return List.of();

        List<ComboDef> matchedCombos = new ArrayList<>();
        for (Enchantment en : enchMap.keySet()) {
            String path = CustomEnchantment.pathOf(en);
            for (ComboDef c : combosForEnchantPath(path)) {
                if (!matchedCombos.contains(c)) {
                    matchedCombos.add(c);
                }
            }
        }
        if (matchedCombos.isEmpty()) return List.of();

        long activeMask = viewer != null ? computeRawMask(viewer) : 0L;
        List<Component> lines = new ArrayList<>();
        for (ComboDef c : matchedCombos) {
            boolean active = (activeMask & c.bit()) != 0L;
            if (active) {
                MutableComponent activePrefix = TranslationFallback.translatableOrFallback(
                        "tooltip.deep_lucky_block.combo_active", "\u2726 Synergie Active : ")
                        .withStyle(ChatFormatting.GOLD);
                lines.add(Component.literal("  ").append(activePrefix).append(c.translatedName()));
                if (shiftHeld) {
                    lines.add(Component.literal("    \u25b8 ")
                            .withStyle(ChatFormatting.YELLOW)
                            .append(c.translatedDesc().copy().withStyle(ChatFormatting.WHITE)));
                }
            } else if (shiftHeld) {
                MutableComponent hintPrefix = TranslationFallback.translatableOrFallback(
                        "tooltip.deep_lucky_block.combo_hint", "\u2727 Combo possible : ")
                        .withStyle(ChatFormatting.DARK_AQUA);
                lines.add(Component.literal("  ").append(hintPrefix).append(c.translatedName())
                        .append(Component.literal(" (").withStyle(ChatFormatting.DARK_GRAY))
                        .append(buildRequirementsSummary(c, viewer))
                        .append(Component.literal(")").withStyle(ChatFormatting.DARK_GRAY)));
            }
        }
        return lines;
    }
}
