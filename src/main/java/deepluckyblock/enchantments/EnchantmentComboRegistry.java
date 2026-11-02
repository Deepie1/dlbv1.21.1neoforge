package deepluckyblock.enchantments;

import deepluckyblock.util.DeepEnchant;
import deepluckyblock.util.TranslationFallback;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * T309-COMBOS : Registre central des 210 Combos & Synergies Uniques (Mono-Pièce & Multi-Pièces),
 * échelonnés sur 6 paliers de difficulté : NORMAL -> UNCOMMON -> RARE -> EPIC -> MYTHIC -> ULTRA_RARE.
 *
 * Utilise un masque 256 bits sans allocation ({@link ComboMask}) couvrant les 210 synergies en O(1).
 */
public final class EnchantmentComboRegistry {

    private EnchantmentComboRegistry() {}

    public enum ComboTier {
        NORMAL(0x94A3B8, "Normal", "Normal"),
        UNCOMMON(0x4ADE80, "Uncommon", "Avanc\u00e9"),
        RARE(0x38BDF8, "Rare", "Rare"),
        EPIC(0xC084FC, "Epic", "\u00c9pique"),
        MYTHIC(0xF472B6, "Mythic", "Mythique"),
        ULTRA_RARE(0xFFAA00, "Ultra-Rare", "Ultra-Rare");

        private final int rgb;
        private final String fallbackEn;
        private final String fallbackFr;

        ComboTier(int rgb, String fallbackEn, String fallbackFr) {
            this.rgb = rgb;
            this.fallbackEn = fallbackEn;
            this.fallbackFr = fallbackFr;
        }

        public int rgb() { return rgb; }

        public MutableComponent badge() {
            String key = "tooltip.deep_lucky_block.combo_tier." + name().toLowerCase(Locale.ROOT);
            String fb = TranslationFallback.isFrench() ? fallbackFr : fallbackEn;
            return Component.literal("[")
                    .append(TranslationFallback.translatableOrFallback(key, fb))
                    .append("]")
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withBold(this == ULTRA_RARE));
        }
    }

    /** T332-COMBOS-210 : Masque binaire 256 bits immuable couvrant jusqu'à 256 combos sans aucune allocation de tableau. */
    public record ComboMask(long w0, long w1, long w2, long w3) {
        public static final ComboMask EMPTY = new ComboMask(0L, 0L, 0L, 0L);

        public boolean isEmpty() { return w0 == 0L && w1 == 0L && w2 == 0L && w3 == 0L; }

        public boolean has(int index) {
            return switch (index >> 6) {
                case 0 -> (w0 & (1L << index)) != 0L;
                case 1 -> (w1 & (1L << index)) != 0L;
                case 2 -> (w2 & (1L << index)) != 0L;
                case 3 -> (w3 & (1L << index)) != 0L;
                default -> false;
            };
        }

        public ComboMask withBit(int index) {
            return switch (index >> 6) {
                case 0 -> new ComboMask(w0 | (1L << index), w1, w2, w3);
                case 1 -> new ComboMask(w0, w1 | (1L << index), w2, w3);
                case 2 -> new ComboMask(w0, w1, w2 | (1L << index), w3);
                case 3 -> new ComboMask(w0, w1, w2, w3 | (1L << index));
                default -> this;
            };
        }

        public ComboMask andNot(ComboMask other) {
            return new ComboMask(w0 & ~other.w0, w1 & ~other.w1, w2 & ~other.w2, w3 & ~other.w3);
        }

        public int bitCount() {
            return Long.bitCount(w0) + Long.bitCount(w1) + Long.bitCount(w2) + Long.bitCount(w3);
        }
    }

    public record PieceRequirement(EquipmentSlot slot, Item sampleItem, int minLevel, List<String> acceptedPaths) {
        public String primaryPath() { return acceptedPaths.get(0); }

        public boolean matches(ItemStack stack) {
            if (stack == null || stack.isEmpty() || !stack.isEnchanted()) return false;
            ItemEnchantments ie = stack.get(DataComponents.ENCHANTMENTS);
            if (ie == null || ie.isEmpty()) return false;
            for (var entry : ie.entrySet()) {
                if (entry.getIntValue() < minLevel) continue;
                var keyOpt = entry.getKey().unwrapKey();
                if (keyOpt.isPresent()) {
                    ResourceLocation loc = keyOpt.get().location();
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

    public record ComboDef(int index, String id, ComboTier tier, int rgb, List<PieceRequirement> pieces) {
        public String nameKey() { return "combo.deep_lucky_block." + id + ".name"; }
        public String descKey() { return "combo.deep_lucky_block." + id + ".desc"; }

        public MutableComponent translatedName() {
            return TranslationFallback.translatableOrFallback(nameKey(), id.replace('_', ' '))
                    .withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb)).withBold(true));
        }

        public MutableComponent translatedDesc() {
            return TranslationFallback.translatableOrFallback(descKey(), "");
        }

        public boolean matchesEntity(LivingEntity entity) {
            if (entity == null) return false;
            if (index == IDX_POING_DIVIN_SAITAMA && !entity.getMainHandItem().isEmpty()) return false;
            for (PieceRequirement req : pieces) {
                if (!req.matches(entity.getItemBySlot(req.slot()))) return false;
            }
            return true;
        }
    }

    private static PieceRequirement req(EquipmentSlot slot, Item sample, int minLevel, String... paths) {
        return new PieceRequirement(slot, sample, minLevel, List.of(paths));
    }

    // Constantes d'index nommées pour les 110 combos
    public static final int C01_ACROBAT_ALCHIMIQUE = 40;
    public static final int C02_ASCENSION_CELESTE = 62;
    public static final int C04_PATINEUR_AZOTE = 41;
    public static final int IDX_ECLAIREUR_AGILE = 0;
    public static final int IDX_GARDE_FANTASSIN = 1;
    public static final int IDX_BOXEUR_RUE = 2;
    public static final int IDX_COLLECTEUR_AIMANTE = 3;
    public static final int IDX_CAMPEUR_ENDURCI = 4;
    public static final int IDX_MINEUR_ETINCELLE = 5;
    public static final int IDX_JARDINIER_MATINAL = 6;
    public static final int IDX_PECHEUR_CUISINIER = 7;
    public static final int IDX_ARCHER_RONCE = 8;
    public static final int IDX_ARBALETRIER_ECLAIREUR = 9;
    public static final int IDX_TERRASSIER_NEIGE = 10;
    public static final int IDX_PORTEUR_INFATIGABLE = 11;
    public static final int IDX_APICULTEUR_SEREIN = 12;
    public static final int IDX_BRETTEUR_BALAYANT = 13;
    public static final int IDX_PLONGEUR_APNEE = 14;
    public static final int IDX_BUCHERON_ROBUSTE = 15;
    public static final int IDX_CHASSEUR_OPPORTUNISTE = 16;
    public static final int IDX_RECYCLEUR_PRUDENT = 17;
    public static final int IDX_FERMIER_COMPOST = 18;
    public static final int IDX_SENTINELLE_REMPART = 19;
    public static final int IDX_CAVALIER_ROYAL = 20;
    public static final int IDX_GLOUTON_CARNASSIER = 21;
    public static final int IDX_TRAQUEUR_SANGLANT = 22;
    public static final int IDX_FONDEUR_VOLCANIQUE = 23;
    public static final int IDX_SAPEUR_TRANCHEE = 24;
    public static final int IDX_FAUCHEUR_CHAMPETRE = 25;
    public static final int IDX_ARCHER_REBOND = 26;
    public static final int IDX_PLONGEUR_ABYSSAL = 27;
    public static final int IDX_ALPINISTE_STRATOSPHERE = 28;
    public static final int IDX_DUELLISTE_SOLITAIRE = 29;
    public static final int IDX_MOINE_RYTHMIQUE = 30;
    public static final int IDX_PROSPECTEUR_CRISTAL = 31;
    public static final int IDX_PECHEUR_GRAPPIN = 32;
    public static final int IDX_PYROMANE_BRAISE = 33;
    public static final int IDX_ACROBATE_ROULADE = 34;
    public static final int IDX_ARCHER_RECUL = 35;
    public static final int IDX_GARDE_IGNIFUGE = 36;
    public static final int IDX_CHASSEUR_VENIN = 37;
    public static final int IDX_ARBALETRIER_DISPERSION = 38;
    public static final int IDX_PELLE_HALLEBARDIER = 39;
    public static final int IDX_ACROBAT_ALCHIMIQUE = 40;
    public static final int IDX_PATINEUR_AZOTE = 41;
    public static final int IDX_COEUR_FORGE_NAINE = 42;
    public static final int IDX_BUCHERON_SYLVESTRE = 43;
    public static final int IDX_MOISSONNEUR_ABONDANCE = 44;
    public static final int IDX_CITADELLE_VIVANTE = 45;
    public static final int IDX_MAITRE_WING_CHUN = 46;
    public static final int IDX_BRISE_OS_COLOSSE = 47;
    public static final int IDX_INQUISITEUR_SACRE = 48;
    public static final int IDX_BOURREAU_DECAPITEUR = 49;
    public static final int IDX_CORSAIRE_PILLARD = 50;
    public static final int IDX_TRIDENT_POGO_MARIN = 51;
    public static final int IDX_ARCHER_GRAVITE = 52;
    public static final int IDX_ARCHER_RETARDEMENT = 53;
    public static final int IDX_HALLEBARDIER_IMPERIAL = 54;
    public static final int IDX_PILOTE_RASE_MOTTES = 55;
    public static final int IDX_TOURELLE_AEROFREIN = 56;
    public static final int IDX_PECHEUR_MONSTRES = 57;
    public static final int IDX_ECHO_DUELLISTE = 58;
    public static final int IDX_EMBUSCADE_CHIRURGICALE = 59;
    public static final int IDX_FORGERON_SOUTERRAIN = 60;
    public static final int IDX_PESTE_AMBULANTE = 61;
    public static final int IDX_ASCENSION_CELESTE = 62;
    public static final int IDX_PONT_DIMENSIONNEL = 63;
    public static final int IDX_SEIGNEUR_VAMPIRE = 64;
    public static final int IDX_DUELLISTE_ECLAIR = 65;
    public static final int IDX_BASTION_MIROIR = 66;
    public static final int IDX_OEIL_FAUCON_ASTRAL = 67;
    public static final int IDX_COMMANDO_GRAPPIN = 68;
    public static final int IDX_ARTILLEUR_INQUISITEUR = 69;
    public static final int IDX_SPECTRE_DIMENSIONNEL = 70;
    public static final int IDX_AMIRAL_HAUTURIER = 71;
    public static final int IDX_BELIER_AILES_FER = 72;
    public static final int IDX_FAUCHEUSE_AMES = 73;
    public static final int IDX_ARCHER_ORAGE_CHAINE = 74;
    public static final int IDX_TRIDENT_RETOUR_EXPLOSIF = 75;
    public static final int IDX_EXCAVATEUR_SISMIQUE = 76;
    public static final int IDX_CHASSEUR_PROIE_MARQUEE = 77;
    public static final int IDX_PECHEUR_DIMENSION_END = 78;
    public static final int IDX_BERSERKER_SISMIQUE = 79;
    public static final int IDX_COLOSSE_FRANCHISSEUR = 80;
    public static final int IDX_ANGE_PLUME_CELESTE = 81;
    public static final int IDX_ASSASSIN_NOCTURNE = 82;
    public static final int IDX_EMPEREUR_LEGION = 83;
    public static final int IDX_FAUCHEUR_TEMPETE = 84;
    public static final int IDX_TITAN_TELLURIQUE = 85;
    public static final int IDX_BERSERKER_IMMORTEL = 86;
    public static final int IDX_VENGEANCE_WARDEN = 87;
    public static final int IDX_AVATAR_INFERNAL = 88;
    public static final int IDX_SEIGNEUR_ABYSSES = 89;
    public static final int IDX_FAUCON_OURAGAN = 90;
    public static final int IDX_RENAISSANCE_SOLAIRE = 91;
    public static final int IDX_PLUIE_CATACLYSMIQUE = 92;
    public static final int IDX_AVATAR_ZEPHYR = 93;
    public static final int IDX_PACTE_MAUDIT_VERRE = 94;
    public static final int IDX_DRAGONNIER_END = 95;
    public static final int IDX_ARMURE_ETERNELLE_TRANSCENDANTE = 96;
    public static final int IDX_DIEU_LAME_SUPREME = 97;
    public static final int IDX_EXTERMINATEUR_TITANS_ABSOLU = 98;
    public static final int IDX_SEIGNEUR_SANG_IMMORTEL = 99;
    public static final int IDX_ARCHANGE_JUGEMENT_SOLAIRE = 100;
    public static final int IDX_POSEIDON_CATACLYSME = 101;
    public static final int IDX_VALKYRIE_SUPERSONIQUE = 102;
    public static final int IDX_FORGE_CREATEUR_ETERNEL = 103;
    public static final int IDX_FANTOME_ECLIPSE_TOTALE = 104;
    public static final int IDX_SEIGNEUR_GUERRE_APOCALYPSE = 105;
    public static final int IDX_MAITRE_TEMPS_IMMUABLE = 106;
    public static final int IDX_POING_DIVIN_SAITAMA = 107;
    public static final int IDX_INQUISITEUR_ARBALETE_SOUFRE = 108;
    public static final int IDX_AVATAR_SUPREME_DEEP_LUCKY = 109;
    // T332-COMBOS-210 : 100 nouveaux combos (#111 .. #210)
    public static final int IDX_LAME_NEUVE_ETINCELANTE = 110;
    public static final int IDX_BUCHERON_INFATIGABLE = 111;
    public static final int IDX_MINEUR_STABILISATEUR = 112;
    public static final int IDX_ECLAIREUR_FLAMBEAU = 113;
    public static final int IDX_PECHEUR_EXPERIMENTE = 114;
    public static final int IDX_MOISSONNEUR_MATINAL = 115;
    public static final int IDX_TERRASSIER_ORPAILLEUR = 116;
    public static final int IDX_ARCHER_RECUPERATEUR = 117;
    public static final int IDX_GARDE_ENDURCI = 118;
    public static final int IDX_COUREUR_PLUIE = 119;
    public static final int IDX_DUELLISTE_DESARMEUR = 120;
    public static final int IDX_BOXEUR_SPRINT = 121;
    public static final int IDX_ARBALETRIER_RODEUR = 122;
    public static final int IDX_VEILLEUR_CLAIRVOYANT = 123;
    public static final int IDX_CHASSEUR_PILLARDS = 124;
    public static final int IDX_PLONGEUR_CORAIL = 125;
    public static final int IDX_GRIMPEUR_PENTE = 126;
    public static final int IDX_ABRI_TORTUE = 127;
    public static final int IDX_FENDEUR_BRUTAL = 128;
    public static final int IDX_VEINE_PRUDENTE = 129;
    public static final int IDX_CHOC_THERMIQUE_LAME = 130;
    public static final int IDX_ARCHER_OASIS = 131;
    public static final int IDX_ARCHER_DESERTIQUE = 132;
    public static final int IDX_PECHEUR_MAGMA = 133;
    public static final int IDX_FAUCHEUR_XP = 134;
    public static final int IDX_TAUPE_FOREUSE = 135;
    public static final int IDX_PROVOCATEUR_EPINEUX = 136;
    public static final int IDX_SAUTEUR_RESSORT = 137;
    public static final int IDX_ESCRIMEUR_ALLONGE = 138;
    public static final int IDX_CHASSEUR_CREPUSCULE = 139;
    public static final int IDX_ARBALETE_FAUCON = 140;
    public static final int IDX_GANTELET_CHIRURGIEN = 141;
    public static final int IDX_VETERAN_ENDURCI = 142;
    public static final int IDX_PURIFICATEUR_PLAIES = 143;
    public static final int IDX_MARCHEUR_END_PERLE = 144;
    public static final int IDX_PLANEUR_THERMIQUE = 145;
    public static final int IDX_HARPON_TONNERRE = 146;
    public static final int IDX_BASTION_MURAL = 147;
    public static final int IDX_TIR_BOUT_PORTANT_LOURD = 148;
    public static final int IDX_PROSPECTEUR_RADAR = 149;
    public static final int IDX_TOURBILLON_AME_ARDENTE = 150;
    public static final int IDX_RICOCHET_TRACANT = 151;
    public static final int IDX_SALVE_SUPPRESSION = 152;
    public static final int IDX_VORTEX_ELECTRIQUE_MARIN = 153;
    public static final int IDX_FORGE_FILON_ARDENT = 154;
    public static final int IDX_ABATTEUR_OURAGAN = 155;
    public static final int IDX_FAUCHEUSE_SANGLANTE = 156;
    public static final int IDX_PIQUE_FAUCON_PLONGEANT = 157;
    public static final int IDX_SUMO_SISMIQUE = 158;
    public static final int IDX_ASSASSIN_ECHANGE = 159;
    public static final int IDX_BERSERKER_FLAMMES = 160;
    public static final int IDX_COMMANDANT_MEUTE_FER = 161;
    public static final int IDX_BRISE_GARDE_TONNERRE = 162;
    public static final int IDX_DRAGUEUR_TRESORS_ABYSSES = 163;
    public static final int IDX_TIREUR_FANTOME_MURMURE = 164;
    public static final int IDX_JUGEMENT_AGONIE = 165;
    public static final int IDX_CARAPACE_CINETIQUE = 166;
    public static final int IDX_TRIDENT_EMPALEMENT_SOLAIRE = 167;
    public static final int IDX_PACTE_SANG_ECARLATE = 168;
    public static final int IDX_AILES_ORAGEUSES_MAITRISEES = 169;
    public static final int IDX_LAME_SURCHARGE_CRITIQUE = 170;
    public static final int IDX_SNIPER_SPECTRAL_ABSOLU = 171;
    public static final int IDX_INQUISITEUR_PURIFICATEUR = 172;
    public static final int IDX_PACTE_SALAMANDRE_ARDENT = 173;
    public static final int IDX_KRAKEN_RESSAC_MAUDIT = 174;
    public static final int IDX_PREDATEUR_VOIDLING_NOCTURNE = 175;
    public static final int IDX_DANSEUR_MIRAGE_ZEPHYR = 176;
    public static final int IDX_COLOSSE_RACINES_PIERRE = 177;
    public static final int IDX_ARTILLEUR_CHAOS_ALCHIMIQUE = 178;
    public static final int IDX_MAITRE_LAMES_COMBO = 179;
    public static final int IDX_FOREUR_COEUR_MONDE = 180;
    public static final int IDX_VOLTIGEUR_PLONGEON_METEORE = 181;
    public static final int IDX_MARECHAL_SOUVERAIN = 182;
    public static final int IDX_TERREUR_ABYSSE_WARDEN = 183;
    public static final int IDX_FAUCHEUR_SACRIFICE_IMMORTEL = 184;
    public static final int IDX_CAVALIER_TONNERRE_ROYAL = 185;
    public static final int IDX_ARCHER_DISTORSION_VIDE = 186;
    public static final int IDX_BOXEUR_DIAMANT_TITAN = 187;
    public static final int IDX_CHIMERE_SEPT_COEURS = 188;
    public static final int IDX_EXECUTEUR_APOCALYPSE_HACHE = 189;
    public static final int IDX_TEMPETE_ASTRALE_INFINIE = 190;
    public static final int IDX_CITADELLE_MIROIR_DIVIN = 191;
    public static final int IDX_FAUCHEUSE_NECROPOLE_ROYALE = 192;
    public static final int IDX_SEIGNEUR_FORGES_STELLAIRES = 193;
    public static final int IDX_FANTOME_LAME_DIMENSIONNELLE = 194;
    public static final int IDX_ARCHONTE_MAREES_CELESTES = 195;
    public static final int IDX_JUGGERNAUT_GOURMAND_COLOSSAL = 196;
    public static final int IDX_ARTILLEUR_METEOR_ECARLATE = 197;
    public static final int IDX_CHAMPION_MAUDIT_TRANSCENDANT = 198;
    public static final int IDX_AERONAUTE_SUPERSONIQUE_ROYAL = 199;
    public static final int IDX_DIEU_HACHE_CATACLYSME = 200;
    public static final int IDX_GRAND_AMIRAL_SEPT_MERS = 201;
    public static final int IDX_EMPEREUR_FAUX_NEANT = 202;
    public static final int IDX_FORGERON_DIVIN_RELIQUE = 203;
    public static final int IDX_SNIPER_CELESTE_OMNISCIENT = 204;
    public static final int IDX_CITADELLE_IMMORTELLE_EGIDE = 205;
    public static final int IDX_AVATAR_CHIMERE_PRIMORDIALE = 206;
    public static final int IDX_SEIGNEUR_FLEAU_TOXIQUE = 207;
    public static final int IDX_EMPEREUR_COMMANDANT_SUPREME = 208;
    public static final int IDX_TRANSCENDANCE_CHANCE_INFINIE = 209;

    public static final List<ComboDef> COMBOS;
    private static final Map<String, List<ComboDef>> BY_ENCHANT_PATH;
    private static final int[] MIN_DISTINCT_SLOTS;

    static {
        List<ComboDef> list = new ArrayList<>(210);
        list.add(new ComboDef(0, "eclaireur_agile", ComboTier.NORMAL, 0x94A3B8, List.of(
                req(EquipmentSlot.MAINHAND, Items.IRON_SWORD, 1, "sharp_edge"),
                req(EquipmentSlot.FEET, Items.IRON_BOOTS, 1, "basic_swiftness"))));
        list.add(new ComboDef(1, "garde_fantassin", ComboTier.NORMAL, 0xCBD5E1, List.of(
                req(EquipmentSlot.OFFHAND, Items.SHIELD, 1, "shield_efficiency"),
                req(EquipmentSlot.LEGS, Items.IRON_LEGGINGS, 1, "anchoring"))));
        list.add(new ComboDef(2, "boxeur_rue", ComboTier.NORMAL, 0xF87171, List.of(
                req(EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, 1, "straight_punch_gauntlet"),
                req(EquipmentSlot.FEET, Items.IRON_BOOTS, 1, "surefooted"))));
        list.add(new ComboDef(3, "collecteur_aimante", ComboTier.NORMAL, 0x60A5FA, List.of(
                req(EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, 1, "item_attraction"),
                req(EquipmentSlot.HEAD, Items.IRON_HELMET, 1, "fast_eating"))));
        list.add(new ComboDef(4, "campeur_endurci", ComboTier.NORMAL, 0xFBBF24, List.of(
                req(EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, 1, "campfire_heal"),
                req(EquipmentSlot.HEAD, Items.IRON_HELMET, 1, "breath_heal"))));
        list.add(new ComboDef(5, "mineur_etincelle", ComboTier.NORMAL, 0xF59E0B, List.of(
                req(EquipmentSlot.MAINHAND, Items.IRON_PICKAXE, 1, "flint_strike"),
                req(EquipmentSlot.FEET, Items.IRON_BOOTS, 1, "light_walker"))));
        list.add(new ComboDef(6, "jardinier_matinal", ComboTier.NORMAL, 0x4ADE80, List.of(
                req(EquipmentSlot.MAINHAND, Items.IRON_HOE, 1, "weeding_hoe", "replanting"),
                req(EquipmentSlot.FEET, Items.IRON_BOOTS, 1, "trailblazer"))));
        list.add(new ComboDef(7, "pecheur_cuisinier", ComboTier.NORMAL, 0xFB923C, List.of(
                req(EquipmentSlot.MAINHAND, Items.FISHING_ROD, 1, "cooked_catch"),
                req(EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, 1, "stomach_pouch", "big_stomach"))));
        list.add(new ComboDef(8, "archer_ronce", ComboTier.NORMAL, 0x84CC16, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "poison_arrow"),
                req(EquipmentSlot.FEET, Items.IRON_BOOTS, 1, "stinky_feet"))));
        list.add(new ComboDef(9, "arbaletrier_eclaireur", ComboTier.NORMAL, 0xFDE047, List.of(
                req(EquipmentSlot.MAINHAND, Items.CROSSBOW, 1, "torch"),
                req(EquipmentSlot.HEAD, Items.IRON_HELMET, 1, "mental_clarity"))));
        list.add(new ComboDef(10, "terrassier_neige", ComboTier.NORMAL, 0xBAE6FD, List.of(
                req(EquipmentSlot.MAINHAND, Items.IRON_SHOVEL, 1, "snow_remover_shovel", "scooping"),
                req(EquipmentSlot.FEET, Items.IRON_BOOTS, 1, "high_step"))));
        list.add(new ComboDef(11, "porteur_infatigable", ComboTier.NORMAL, 0xA3E635, List.of(
                req(EquipmentSlot.LEGS, Items.IRON_LEGGINGS, 1, "carriers_stride"),
                req(EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, 1, "exercise"))));
        list.add(new ComboDef(12, "apiculteur_serein", ComboTier.NORMAL, 0xFEF08A, List.of(
                req(EquipmentSlot.HEAD, Items.IRON_HELMET, 1, "phantom_bee_vex_aversion"),
                req(EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, 1, "shelter"))));
        list.add(new ComboDef(13, "bretteur_balayant", ComboTier.NORMAL, 0xE2E8F0, List.of(
                req(EquipmentSlot.MAINHAND, Items.IRON_SWORD, 1, "sweeping_enhancement", "sweeping_slash"),
                req(EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, 1, "strafe"))));
        list.add(new ComboDef(14, "plongeur_apnee", ComboTier.NORMAL, 0x38BDF8, List.of(
                req(EquipmentSlot.HEAD, Items.IRON_HELMET, 1, "breath_holding"),
                req(EquipmentSlot.FEET, Items.IRON_BOOTS, 1, "tidal_step"))));
        list.add(new ComboDef(15, "bucheron_robuste", ComboTier.NORMAL, 0x86EFAC, List.of(
                req(EquipmentSlot.MAINHAND, Items.IRON_PICKAXE, 1, "haste_surge"),
                req(EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, 1, "pebblekin"))));
        list.add(new ComboDef(16, "chasseur_opportuniste", ComboTier.NORMAL, 0xFCA5A5, List.of(
                req(EquipmentSlot.MAINHAND, Items.IRON_SWORD, 1, "promising_blade"),
                req(EquipmentSlot.LEGS, Items.IRON_LEGGINGS, 1, "basic_leap_power"))));
        list.add(new ComboDef(17, "recycleur_prudent", ComboTier.NORMAL, 0xA7F3D0, List.of(
                req(EquipmentSlot.MAINHAND, Items.IRON_SWORD, 1, "recycle", "basic_mending"),
                req(EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, 1, "magnetic"))));
        list.add(new ComboDef(18, "fermier_compost", ComboTier.NORMAL, 0x65A30D, List.of(
                req(EquipmentSlot.MAINHAND, Items.IRON_HOE, 1, "composting"),
                req(EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, 1, "burning_heart"))));
        list.add(new ComboDef(19, "sentinelle_rempart", ComboTier.NORMAL, 0x93C5FD, List.of(
                req(EquipmentSlot.OFFHAND, Items.SHIELD, 1, "bash_power", "bash_damage"),
                req(EquipmentSlot.FEET, Items.IRON_BOOTS, 1, "sticky"))));
        list.add(new ComboDef(20, "cavalier_royal", ComboTier.UNCOMMON, 0xFACC15, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SWORD, 1, "cavalier"),
                req(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, 1, "horse_boots"))));
        list.add(new ComboDef(21, "glouton_carnassier", ComboTier.UNCOMMON, 0xFB7185, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SWORD, 1, "food_power_strike", "devour"),
                req(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, 1, "meat_armor", "gluttony"))));
        list.add(new ComboDef(22, "traqueur_sanglant", ComboTier.UNCOMMON, 0xEF4444, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SWORD, 1, "killing_sense", "predator"),
                req(EquipmentSlot.LEGS, Items.DIAMOND_LEGGINGS, 1, "hunters_pace"))));
        list.add(new ComboDef(23, "fondeur_volcanique", ComboTier.UNCOMMON, 0xF97316, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_PICKAXE, 1, "molten"),
                req(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, 1, "salamanders_vow"))));
        list.add(new ComboDef(24, "sapeur_tranchee", ComboTier.UNCOMMON, 0xD97706, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SHOVEL, 1, "military_shovel", "shovel_heavy_hit"),
                req(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, 1, "bastion_heart"))));
        list.add(new ComboDef(25, "faucheur_champetre", ComboTier.UNCOMMON, 0x22C55E, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_HOE, 1, "sickle_slash", "sickle_damage"),
                req(EquipmentSlot.LEGS, Items.DIAMOND_LEGGINGS, 1, "pivot"))));
        list.add(new ComboDef(26, "archer_rebond", ComboTier.UNCOMMON, 0x34D399, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "bounce", "ricochet"),
                req(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, 1, "elastic_soles"))));
        list.add(new ComboDef(27, "plongeur_abyssal", ComboTier.UNCOMMON, 0x0EA5E9, List.of(
                req(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, 1, "deep_hypoxia"),
                req(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, 1, "oxygen_lack_haste"))));
        list.add(new ComboDef(28, "alpiniste_stratosphere", ComboTier.UNCOMMON, 0xBAE6FD, List.of(
                req(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, 1, "high_altitude_hypoxia"),
                req(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, 1, "gale_step"))));
        list.add(new ComboDef(29, "duelliste_solitaire", ComboTier.UNCOMMON, 0xA855F7, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SWORD, 1, "lone_wolf", "solo_combat"),
                req(EquipmentSlot.LEGS, Items.DIAMOND_LEGGINGS, 1, "focused_steadiness"))));
        list.add(new ComboDef(30, "moine_rythmique", ComboTier.UNCOMMON, 0xF472B6, List.of(
                req(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, 1, "martial_rhythm", "heartbeat_rhythm"),
                req(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, 1, "momentum"))));
        list.add(new ComboDef(31, "prospecteur_cristal", ComboTier.UNCOMMON, 0x38BDF8, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_PICKAXE, 1, "prospecting", "delicate_extraction"),
                req(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, 1, "mystic_mind"))));
        list.add(new ComboDef(32, "pecheur_grappin", ComboTier.UNCOMMON, 0x2DD4BF, List.of(
                req(EquipmentSlot.MAINHAND, Items.FISHING_ROD, 1, "pulling_power", "barbed_hook"),
                req(EquipmentSlot.LEGS, Items.DIAMOND_LEGGINGS, 1, "combat_roll"))));
        list.add(new ComboDef(33, "pyromane_braise", ComboTier.UNCOMMON, 0xFB923C, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SWORD, 1, "sweeping_fire", "sweeping_burn_slash"),
                req(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, 1, "ember_step"))));
        list.add(new ComboDef(34, "acrobate_roulade", ComboTier.UNCOMMON, 0x4ADE80, List.of(
                req(EquipmentSlot.LEGS, Items.DIAMOND_LEGGINGS, 1, "combat_roll"),
                req(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, 1, "rapid_drop_stop"))));
        list.add(new ComboDef(35, "archer_recul", ComboTier.UNCOMMON, 0x67E8F9, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "recoil_launch"),
                req(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, 1, "basic_lightness"))));
        list.add(new ComboDef(36, "garde_ignifuge", ComboTier.UNCOMMON, 0xF87171, List.of(
                req(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, 1, "fire_devour", "scorching_bath"),
                req(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, 1, "fire_protection_enhancement"))));
        list.add(new ComboDef(37, "chasseur_venin", ComboTier.UNCOMMON, 0x84CC16, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SWORD, 1, "poison_fang", "poison_scar"),
                req(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, 1, "foresight"))));
        list.add(new ComboDef(38, "arbaletrier_dispersion", ComboTier.UNCOMMON, 0xFBBF24, List.of(
                req(EquipmentSlot.MAINHAND, Items.CROSSBOW, 1, "scatter", "heavy_range_crossbow"),
                req(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, 1, "projectile_protection_enhancement"))));
        list.add(new ComboDef(39, "pelle_hallebardier", ComboTier.UNCOMMON, 0xCBD5E1, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SHOVEL, 1, "shovel_blade", "armor_pry"),
                req(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, 1, "swiftness"))));
        list.add(new ComboDef(40, "acrobat_alchimique", ComboTier.RARE, 0x76FF4D, List.of(
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 1, "wall_runner"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "bouncy"))));
        list.add(new ComboDef(41, "patineur_azote", ComboTier.RARE, 0x55E8FF, List.of(
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 1, "slide"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "frost_trail"))));
        list.add(new ComboDef(42, "coeur_forge_naine", ComboTier.RARE, 0x38BDF8, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_PICKAXE, 1, "extracting", "vein_miner"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "miners_guidance"))));
        list.add(new ComboDef(43, "bucheron_sylvestre", ComboTier.RARE, 0x4ADE80, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_AXE, 1, "lumberjack", "tree_felling"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "trailblazer"))));
        list.add(new ComboDef(44, "moissonneur_abondance", ComboTier.RARE, 0x86EFAC, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_HOE, 1, "bountiful_harvest", "farmers_hoe"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "food_is_heaven", "rain_nourishment"))));
        list.add(new ComboDef(45, "citadelle_vivante", ComboTier.RARE, 0x94A3B8, List.of(
                req(EquipmentSlot.OFFHAND, Items.SHIELD, 1, "shield_quake"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "bastion_heart"))));
        list.add(new ComboDef(46, "maitre_wing_chun", ComboTier.RARE, 0xF43F5E, List.of(
                req(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, 1, "wing_chun_gauntlet", "empty_fist_gauntlet"),
                req(EquipmentSlot.LEGS, Items.DIAMOND_LEGGINGS, 1, "evasive_footwork"))));
        list.add(new ComboDef(47, "brise_os_colosse", ComboTier.RARE, 0xD97706, List.of(
                req(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, 1, "bone_break_gauntlet", "tendon_break_gauntlet"),
                req(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, 1, "e_speed"))));
        list.add(new ComboDef(48, "inquisiteur_sacre", ComboTier.RARE, 0xFDE047, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SWORD, 1, "seraphs_judgment"),
                req(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, 1, "priests_blessing"))));
        list.add(new ComboDef(49, "bourreau_decapiteur", ComboTier.RARE, 0xDC2626, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SWORD, 1, "beheading", "decapitation"),
                req(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, 1, "illager_commander", "undead_commander"))));
        list.add(new ComboDef(50, "corsaire_pillard", ComboTier.RARE, 0xF59E0B, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SWORD, 1, "plunder_wealth"),
                req(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, 1, "tidal_ward"))));
        list.add(new ComboDef(51, "trident_pogo_marin", ComboTier.RARE, 0x06B6D4, List.of(
                req(EquipmentSlot.MAINHAND, Items.TRIDENT, 1, "sea_pogo", "undertow"),
                req(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, 1, "bouncy"))));
        list.add(new ComboDef(52, "archer_gravite", ComboTier.RARE, 0x8B5CF6, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "enemy_attractor"),
                req(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, 1, "soul_repulsion_blast"))));
        list.add(new ComboDef(53, "archer_retardement", ComboTier.RARE, 0xEC4899, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "delay", "tracer_shot"),
                req(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, 1, "concentration"))));
        list.add(new ComboDef(54, "hallebardier_imperial", ComboTier.RARE, 0x94A3B8, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SHOVEL, 1, "shovel_halberd"),
                req(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, 1, "general"))));
        list.add(new ComboDef(55, "pilote_rase_mottes", ComboTier.RARE, 0x38BDF8, List.of(
                req(EquipmentSlot.CHEST, Items.ELYTRA, 1, "ground_skimmer", "aerodynamic"),
                req(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, 1, "windrunner", "gale_step"))));
        list.add(new ComboDef(56, "tourelle_aerofrein", ComboTier.RARE, 0x0284C7, List.of(
                req(EquipmentSlot.CHEST, Items.ELYTRA, 1, "air_brake"),
                req(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, 1, "perception", "echolocation"))));
        list.add(new ComboDef(57, "pecheur_monstres", ComboTier.RARE, 0x14B8A6, List.of(
                req(EquipmentSlot.MAINHAND, Items.FISHING_ROD, 1, "monster_fishing", "fishing_grapple"),
                req(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, 1, "punching_armor"))));
        list.add(new ComboDef(58, "echo_duelliste", ComboTier.RARE, 0x6366F1, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SWORD, 1, "echo_strike", "interrupting_strike"),
                req(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, 1, "kinetic_conversion"))));
        list.add(new ComboDef(59, "embuscade_chirurgicale", ComboTier.RARE, 0x10B981, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SWORD, 1, "focused_crit", "focused_ambush"),
                req(EquipmentSlot.LEGS, Items.DIAMOND_LEGGINGS, 1, "phantom_step"))));
        list.add(new ComboDef(60, "forgeron_souterrain", ComboTier.RARE, 0xF97316, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_PICKAXE, 1, "burrowing", "mining_stability"),
                req(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, 1, "iron_lung"))));
        list.add(new ComboDef(61, "peste_ambulante", ComboTier.RARE, 0x84CC16, List.of(
                req(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, 1, "super_stinky_feet"),
                req(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, 1, "dread_aura"))));
        list.add(new ComboDef(62, "ascension_celeste", ComboTier.EPIC, 0x99EEFF, List.of(
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "cloud_walker"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 1, "leap_power", "basic_leap_power"))));
        list.add(new ComboDef(63, "pont_dimensionnel", ComboTier.EPIC, 0xBBCCFF, List.of(
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "foot_placement"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 1, "dash"))));
        list.add(new ComboDef(64, "seigneur_vampire", ComboTier.EPIC, 0xE61938, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 1, "blood_drinker_edge", "bloodthirsty", "bloodthirst"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "assimilation"))));
        list.add(new ComboDef(65, "duelliste_eclair", ComboTier.EPIC, 0x40FFFF, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 1, "swift_strike"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 1, "evasive_footwork"))));
        list.add(new ComboDef(66, "bastion_miroir", ComboTier.EPIC, 0xE2E8F0, List.of(
                req(EquipmentSlot.OFFHAND, Items.SHIELD, 1, "perfect_parry"),
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 1, "guard_breaker"))));
        list.add(new ComboDef(67, "oeil_faucon_astral", ComboTier.EPIC, 0x7CF3A0, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "sniper"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "perception", "concentration"))));
        list.add(new ComboDef(68, "commando_grappin", ComboTier.EPIC, 0xA8E6CF, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "grapple"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 1, "wall_runner"))));
        list.add(new ComboDef(69, "artilleur_inquisiteur", ComboTier.EPIC, 0xFF884D, List.of(
                req(EquipmentSlot.MAINHAND, Items.CROSSBOW, 1, "brimstone", "peregrine"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 1, "hunters_pace"))));
        list.add(new ComboDef(70, "spectre_dimensionnel", ComboTier.EPIC, 0xC084FC, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "teleport_arrow", "swap_arrow"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "dimensional_step"))));
        list.add(new ComboDef(71, "amiral_hauturier", ComboTier.EPIC, 0x22D3EE, List.of(
                req(EquipmentSlot.MAINHAND, Items.FISHING_ROD, 1, "thunder_hook", "anglers_blessing", "dredge"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "oceans_blessing", "amphibious"))));
        list.add(new ComboDef(72, "belier_ailes_fer", ComboTier.EPIC, 0x94A3B8, List.of(
                req(EquipmentSlot.CHEST, Items.ELYTRA, 1, "iron_wings", "winging"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 1, "dash"))));
        list.add(new ComboDef(73, "faucheuse_ames", ComboTier.EPIC, 0xA855F7, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_HOE, 1, "soul_reaping_sickle", "soul_sacrifice_slash"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "vital_reserve", "pain_devour"))));
        list.add(new ComboDef(74, "archer_orage_chaine", ComboTier.EPIC, 0x38BDF8, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "elemental_chain", "thunderstruck"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "burst_step"))));
        list.add(new ComboDef(75, "trident_retour_explosif", ComboTier.EPIC, 0xF97316, List.of(
                req(EquipmentSlot.MAINHAND, Items.TRIDENT, 1, "explosive_return", "harpoon"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "explosion_protection"))));
        list.add(new ComboDef(76, "excavateur_sismique", ComboTier.EPIC, 0xF59E0B, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SHOVEL, 1, "universal_trench_shovel", "utility_trench_shovel"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "surefooted"))));
        list.add(new ComboDef(77, "chasseur_proie_marquee", ComboTier.EPIC, 0xEF4444, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 1, "marked_prey", "stealth_hunter"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "perception"))));
        list.add(new ComboDef(78, "pecheur_dimension_end", ComboTier.EPIC, 0xC084FC, List.of(
                req(EquipmentSlot.MAINHAND, Items.FISHING_ROD, 1, "ender_hook"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "dimensional_step"))));
        list.add(new ComboDef(79, "berserker_sismique", ComboTier.EPIC, 0xEA580C, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 1, "seismic_strike", "heavy_slow_hit"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "sprint_impact"))));
        list.add(new ComboDef(80, "colosse_franchisseur", ComboTier.MYTHIC, 0xFFAA22, List.of(
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 1, "giant_step", "big_step"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "meteor_stomp"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "sturdy_brute", "steel_body", "earth_heart"))));
        list.add(new ComboDef(81, "ange_plume_celeste", ComboTier.MYTHIC, 0xFFE680, List.of(
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "lightness", "basic_lightness"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "radiant_ward"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "light_heart", "divine_protection"))));
        list.add(new ComboDef(82, "assassin_nocturne", ComboTier.MYTHIC, 0x9B4DFF, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 1, "shadow_hunt"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 1, "phantom_step"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "night_veil"))));
        list.add(new ComboDef(83, "empereur_legion", ComboTier.MYTHIC, 0xFFD24D, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 1, "summon_army", "army_summon"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "commander"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "hundred_battles_veteran", "heart_resonance", "divine_protection"))));
        list.add(new ComboDef(84, "faucheur_tempete", ComboTier.MYTHIC, 0x33F0FF, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 1, "thundercall_pact"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "electric_heart"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "windrunner", "e_speed"))));
        list.add(new ComboDef(85, "titan_tellurique", ComboTier.MYTHIC, 0xC8965A, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 1, "titans_grasp"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "earth_heart"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 1, "stonebound_roots"))));
        list.add(new ComboDef(86, "berserker_immortel", ComboTier.MYTHIC, 0xFF3B30, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 1, "berserk"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "adrenaline"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "last_stand_escape"))));
        list.add(new ComboDef(87, "vengeance_warden", ComboTier.MYTHIC, 0x00C8B4, List.of(
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "wardenspine"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "perception", "echolocation"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 1, "umbral_pact", "focused_steadiness"))));
        list.add(new ComboDef(88, "avatar_infernal", ComboTier.MYTHIC, 0xFF6A1A, List.of(
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "infernal_armor", "fire_heart"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "magma_walker", "blaze_walker"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "ember_core"))));
        list.add(new ComboDef(89, "seigneur_abysses", ComboTier.MYTHIC, 0x1EA7FF, List.of(
                req(EquipmentSlot.MAINHAND, Items.TRIDENT, 1, "tidecaller", "ocean_current", "homing_trident"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "conduit_heart", "water_heart"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "buoy"))));
        list.add(new ComboDef(90, "faucon_ouragan", ComboTier.MYTHIC, 0x80D8FF, List.of(
                req(EquipmentSlot.CHEST, Items.ELYTRA, 1, "rocket_blast", "wind_rider"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "meteor_stomp"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "veil", "high_altitude_hypoxia"))));
        list.add(new ComboDef(91, "renaissance_solaire", ComboTier.MYTHIC, 0xFFCC33, List.of(
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "final_breath", "phoenix"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "priests_blessing"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "twenty_fourth_blessing", "ember_step"))));
        list.add(new ComboDef(92, "pluie_cataclysmique", ComboTier.MYTHIC, 0xFF5522, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "meteor"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "gale"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "fire_heart", "blaze_repulsion_heart"))));
        list.add(new ComboDef(93, "avatar_zephyr", ComboTier.MYTHIC, 0xA5F3FC, List.of(
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "wind_heart", "zephyrs_gambit"),
                req(EquipmentSlot.MAINHAND, Items.TRIDENT, 1, "wind_lance"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "cloud_stride", "windrunner"))));
        list.add(new ComboDef(94, "pacte_maudit_verre", ComboTier.MYTHIC, 0xDC2626, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 1, "cursed_blade"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "glass_bones_champion_curse"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 1, "umbral_pact"))));
        list.add(new ComboDef(95, "dragonnier_end", ComboTier.MYTHIC, 0xD946EF, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "dragons_breath_arrow", "wraith_arrow"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "veil"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "shadow_heart"))));
        list.add(new ComboDef(96, "armure_eternelle_transcendante", ComboTier.ULTRA_RARE, 0xFFD700, List.of(
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 3, "king"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 3, "divine_protection"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 3, "giant_step"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "cloud_walker"))));
        list.add(new ComboDef(97, "dieu_lame_supreme", ComboTier.ULTRA_RARE, 0xFFAA00, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 10, "supreme_sharpness"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 3, "king"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "heart_resonance"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 3, "leap_power"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "windrunner"))));
        list.add(new ComboDef(98, "exterminateur_titans_absolu", ComboTier.ULTRA_RARE, 0xF97316, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 5, "titan_slayer"),
                req(EquipmentSlot.OFFHAND, Items.SHIELD, 2, "perfect_parry"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "foresight"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 2, "earth_heart"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 4, "giant_step"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "meteor_stomp"))));
        list.add(new ComboDef(99, "seigneur_sang_immortel", ComboTier.ULTRA_RARE, 0xDC2626, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 3, "blood_drinker_edge"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "assimilation"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "final_breath"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 2, "umbral_pact"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "last_stand_escape"))));
        list.add(new ComboDef(100, "archange_jugement_solaire", ComboTier.ULTRA_RARE, 0xFDE047, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 3, "annihilating_sweep"),
                req(EquipmentSlot.OFFHAND, Items.SHIELD, 1, "shield_quake"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 2, "radiant_ward"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 2, "light_heart"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 3, "lightness"))));
        list.add(new ComboDef(101, "poseidon_cataclysme", ComboTier.ULTRA_RARE, 0x06B6D4, List.of(
                req(EquipmentSlot.MAINHAND, Items.TRIDENT, 1, "sincere_loyalty"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "breath_holding"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 2, "water_heart"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 2, "focused_steadiness"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 2, "tidal_ward"))));
        list.add(new ComboDef(102, "valkyrie_supersonique", ComboTier.ULTRA_RARE, 0x38BDF8, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "storm_strike"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "veil"),
                req(EquipmentSlot.CHEST, Items.ELYTRA, 1, "rocket_blast"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 2, "wall_runner"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "cloud_walker"))));
        list.add(new ComboDef(103, "forge_createur_eternel", ComboTier.ULTRA_RARE, 0xA855F7, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_PICKAXE, 1, "core_harvesting", "extracting"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 3, "miners_guidance"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "diamond_glow_gauntlet"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 3, "big_step", "giant_step"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "surefooted"))));
        list.add(new ComboDef(104, "fantome_eclipse_totale", ComboTier.ULTRA_RARE, 0x7C3AED, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "phasing", "wraith_arrow"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "night_veil"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 2, "shadow_heart"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 2, "phantom_step"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 2, "shadow_sprint"))));
        list.add(new ComboDef(105, "seigneur_guerre_apocalypse", ComboTier.ULTRA_RARE, 0xEF4444, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 1, "wither_blade", "peerless"),
                req(EquipmentSlot.OFFHAND, Items.SHIELD, 2, "perfect_parry"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "commander"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 2, "fire_heart"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 2, "dash"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "blaze_walker", "magma_walker"))));
        list.add(new ComboDef(106, "maitre_temps_immuable", ComboTier.ULTRA_RARE, 0xFBBF24, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 3, "chrono_mending", "overclocked_crit"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "mystic_mind"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 2, "adaptive_shell"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 2, "evasive_footwork"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 2, "dimensional_step"))));
        list.add(new ComboDef(107, "poing_divin_saitama", ComboTier.ULTRA_RARE, 0xFF4500, List.of(
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 2, "concentration"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "focused_punch_gauntlet", "heavy_wound_punch_gauntlet"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 3, "leap_power"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 2, "burst_step"))));
        list.add(new ComboDef(108, "inquisiteur_arbalete_soufre", ComboTier.ULTRA_RARE, 0xF59E0B, List.of(
                req(EquipmentSlot.MAINHAND, Items.CROSSBOW, 4, "brimstone"),
                req(EquipmentSlot.OFFHAND, Items.SHIELD, 1, "shield_efficiency"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "perception"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "infernal_armor"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 2, "hunters_pace"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "surefooted"))));
        list.add(new ComboDef(109, "avatar_supreme_deep_lucky", ComboTier.ULTRA_RARE, 0xFFFFFF, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 5, "peerless", "damage_critical"),
                req(EquipmentSlot.OFFHAND, Items.SHIELD, 3, "perfect_parry"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "food_is_heaven"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "heart_resonance"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 5, "giant_step"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "cloud_walker"))));

        // T332-COMBOS-210 : Combos #111 à #210 (index 110 .. 209)
        list.add(new ComboDef(110, "lame_neuve_etincelante", ComboTier.NORMAL, 0x93C5FD, List.of(
                req(EquipmentSlot.MAINHAND, Items.IRON_SWORD, 1, "new_edge"),
                req(EquipmentSlot.MAINHAND, Items.IRON_SWORD, 1, "basic_mending", "basic_chrono_mending"))));
        list.add(new ComboDef(111, "bucheron_infatigable", ComboTier.NORMAL, 0x86EFAC, List.of(
                req(EquipmentSlot.MAINHAND, Items.IRON_AXE, 1, "tree_felling", "lumberjack"),
                req(EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, 1, "exercise"))));
        list.add(new ComboDef(112, "mineur_stabilisateur", ComboTier.NORMAL, 0xCBD5E1, List.of(
                req(EquipmentSlot.MAINHAND, Items.IRON_PICKAXE, 1, "mining_stability"),
                req(EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, 1, "iron_lung"))));
        list.add(new ComboDef(113, "eclaireur_flambeau", ComboTier.NORMAL, 0xFCD34D, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "torch"),
                req(EquipmentSlot.HEAD, Items.IRON_HELMET, 1, "night_veil"))));
        list.add(new ComboDef(114, "pecheur_experimente", ComboTier.NORMAL, 0x7DD3FC, List.of(
                req(EquipmentSlot.MAINHAND, Items.FISHING_ROD, 1, "experienced_fishing", "reliability"),
                req(EquipmentSlot.HEAD, Items.IRON_HELMET, 1, "mystic_mind"))));
        list.add(new ComboDef(115, "moissonneur_matinal", ComboTier.NORMAL, 0xA3E635, List.of(
                req(EquipmentSlot.MAINHAND, Items.IRON_HOE, 1, "replanting", "farmers_hoe"),
                req(EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, 1, "stomach_pouch"))));
        list.add(new ComboDef(116, "terrassier_orpailleur", ComboTier.NORMAL, 0xFBBF24, List.of(
                req(EquipmentSlot.MAINHAND, Items.IRON_SHOVEL, 1, "scooping"),
                req(EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, 1, "item_attraction", "magnetic"))));
        list.add(new ComboDef(117, "archer_recuperateur", ComboTier.NORMAL, 0xFCA5A5, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "retrieval"),
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "flint_heart"))));
        list.add(new ComboDef(118, "garde_endurci", ComboTier.NORMAL, 0x94A3B8, List.of(
                req(EquipmentSlot.OFFHAND, Items.SHIELD, 1, "shield_efficiency"),
                req(EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, 1, "tempered_body"))));
        list.add(new ComboDef(119, "coureur_pluie", ComboTier.NORMAL, 0x38BDF8, List.of(
                req(EquipmentSlot.HEAD, Items.IRON_HELMET, 1, "rain_nourishment"),
                req(EquipmentSlot.FEET, Items.IRON_BOOTS, 1, "tidal_step"))));
        list.add(new ComboDef(120, "duelliste_desarmeur", ComboTier.NORMAL, 0xF9A8D4, List.of(
                req(EquipmentSlot.MAINHAND, Items.IRON_SWORD, 1, "disarm", "interrupting_strike"),
                req(EquipmentSlot.LEGS, Items.IRON_LEGGINGS, 1, "pivot"))));
        list.add(new ComboDef(121, "boxeur_sprint", ComboTier.NORMAL, 0xFB923C, List.of(
                req(EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, 1, "sprint_punch_gauntlet"),
                req(EquipmentSlot.FEET, Items.IRON_BOOTS, 1, "basic_swiftness", "swiftness"))));
        list.add(new ComboDef(122, "arbaletrier_rodeur", ComboTier.NORMAL, 0xA7F3D0, List.of(
                req(EquipmentSlot.MAINHAND, Items.CROSSBOW, 1, "ranger"),
                req(EquipmentSlot.LEGS, Items.IRON_LEGGINGS, 1, "carriers_stride"))));
        list.add(new ComboDef(123, "veilleur_clairvoyant", ComboTier.NORMAL, 0xFEF08A, List.of(
                req(EquipmentSlot.HEAD, Items.IRON_HELMET, 1, "mental_clarity"),
                req(EquipmentSlot.FEET, Items.IRON_BOOTS, 1, "light_walker"))));
        list.add(new ComboDef(124, "chasseur_pillards", ComboTier.NORMAL, 0x34D399, List.of(
                req(EquipmentSlot.MAINHAND, Items.IRON_SWORD, 1, "plunder_wealth"),
                req(EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, 1, "meat_armor"))));
        list.add(new ComboDef(125, "plongeur_corail", ComboTier.NORMAL, 0x22D3EE, List.of(
                req(EquipmentSlot.MAINHAND, Items.TRIDENT, 1, "tidecaller"),
                req(EquipmentSlot.FEET, Items.IRON_BOOTS, 1, "buoy"))));
        list.add(new ComboDef(126, "grimpeur_pente", ComboTier.NORMAL, 0x84CC16, List.of(
                req(EquipmentSlot.LEGS, Items.IRON_LEGGINGS, 1, "basic_leap_power", "leap_power"),
                req(EquipmentSlot.FEET, Items.IRON_BOOTS, 1, "high_step"))));
        list.add(new ComboDef(127, "abri_tortue", ComboTier.NORMAL, 0x64748B, List.of(
                req(EquipmentSlot.CHEST, Items.IRON_CHESTPLATE, 1, "shelter"),
                req(EquipmentSlot.LEGS, Items.IRON_LEGGINGS, 1, "anchoring"))));
        list.add(new ComboDef(128, "fendeur_brutal", ComboTier.NORMAL, 0xF87171, List.of(
                req(EquipmentSlot.MAINHAND, Items.IRON_AXE, 1, "brutal"),
                req(EquipmentSlot.MAINHAND, Items.IRON_AXE, 1, "haste_continuation"))));
        list.add(new ComboDef(129, "veine_prudente", ComboTier.NORMAL, 0x60A5FA, List.of(
                req(EquipmentSlot.MAINHAND, Items.IRON_PICKAXE, 1, "delicate_extraction"),
                req(EquipmentSlot.MAINHAND, Items.IRON_PICKAXE, 1, "mining_stability"))));
        list.add(new ComboDef(130, "choc_thermique_lame", ComboTier.UNCOMMON, 0x38BDF8, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SWORD, 1, "blazing_trauma", "sweeping_fire", "fiery_sweep"),
                req(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, 1, "frost_trail"))));
        list.add(new ComboDef(131, "archer_oasis", ComboTier.UNCOMMON, 0x2DD4BF, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "water_arrow"),
                req(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, 1, "breath_heal"))));
        list.add(new ComboDef(132, "archer_desertique", ComboTier.UNCOMMON, 0xF59E0B, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "dry_arrow"),
                req(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, 1, "thirsty_heart"))));
        list.add(new ComboDef(133, "pecheur_magma", ComboTier.UNCOMMON, 0xFB923C, List.of(
                req(EquipmentSlot.MAINHAND, Items.FISHING_ROD, 1, "infernal_line"),
                req(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, 1, "magma_walker", "blaze_walker"))));
        list.add(new ComboDef(134, "faucheur_xp", ComboTier.UNCOMMON, 0xA3E635, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_HOE, 1, "experience_harvest"),
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_HOE, 1, "loot_harvest"))));
        list.add(new ComboDef(135, "taupe_foreuse", ComboTier.UNCOMMON, 0xD97706, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SHOVEL, 1, "burrowing"),
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SHOVEL, 1, "military_shovel"))));
        list.add(new ComboDef(136, "provocateur_epineux", ComboTier.UNCOMMON, 0xEF4444, List.of(
                req(EquipmentSlot.OFFHAND, Items.SHIELD, 1, "hatred_draw"),
                req(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, 1, "powerful_thorns", "punching_armor"))));
        list.add(new ComboDef(137, "sauteur_ressort", ComboTier.UNCOMMON, 0x34D399, List.of(
                req(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, 1, "elastic_soles"),
                req(EquipmentSlot.LEGS, Items.DIAMOND_LEGGINGS, 1, "leap_power"))));
        list.add(new ComboDef(138, "escrimeur_allonge", ComboTier.UNCOMMON, 0x67E8F9, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SWORD, 1, "extension"),
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SWORD, 1, "swift_strike"))));
        list.add(new ComboDef(139, "chasseur_crepuscule", ComboTier.UNCOMMON, 0x818CF8, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SWORD, 1, "shadow_hunt"),
                req(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, 1, "echolocation"))));
        list.add(new ComboDef(140, "arbalete_faucon", ComboTier.UNCOMMON, 0xFACC15, List.of(
                req(EquipmentSlot.MAINHAND, Items.CROSSBOW, 1, "peregrine"),
                req(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, 1, "gale_step"))));
        list.add(new ComboDef(141, "gantelet_chirurgien", ComboTier.UNCOMMON, 0xF472B6, List.of(
                req(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, 1, "tendon_break_gauntlet"),
                req(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, 1, "concentration"))));
        list.add(new ComboDef(142, "veteran_endurci", ComboTier.UNCOMMON, 0x94A3B8, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SWORD, 1, "battle_hardened"),
                req(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, 1, "last_stand_toughness"))));
        list.add(new ComboDef(143, "purificateur_plaies", ComboTier.UNCOMMON, 0xFDE047, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SWORD, 1, "transfer_misfortune"),
                req(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, 1, "priests_blessing"))));
        list.add(new ComboDef(144, "marcheur_end_perle", ComboTier.UNCOMMON, 0xC084FC, List.of(
                req(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, 1, "pebblekin"),
                req(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, 1, "dimensional_step"))));
        list.add(new ComboDef(145, "planeur_thermique", ComboTier.UNCOMMON, 0xFB7185, List.of(
                req(EquipmentSlot.CHEST, Items.ELYTRA, 1, "aerodynamic"),
                req(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, 1, "ember_step"))));
        list.add(new ComboDef(146, "harpon_tonnerre", ComboTier.UNCOMMON, 0x38BDF8, List.of(
                req(EquipmentSlot.MAINHAND, Items.FISHING_ROD, 1, "thunder_hook"),
                req(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, 1, "kinetic_conversion", "electric_heart"))));
        list.add(new ComboDef(147, "bastion_mural", ComboTier.UNCOMMON, 0xA8A29E, List.of(
                req(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, 1, "bastion_heart"),
                req(EquipmentSlot.LEGS, Items.DIAMOND_LEGGINGS, 1, "wall_runner"))));
        list.add(new ComboDef(148, "tir_bout_portant_lourd", ComboTier.UNCOMMON, 0xF97316, List.of(
                req(EquipmentSlot.MAINHAND, Items.CROSSBOW, 1, "point_shoot"),
                req(EquipmentSlot.MAINHAND, Items.CROSSBOW, 1, "heavy_impact"))));
        list.add(new ComboDef(149, "prospecteur_radar", ComboTier.UNCOMMON, 0x22D3EE, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_PICKAXE, 1, "ore_detector"),
                req(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, 1, "miners_guidance"))));
        list.add(new ComboDef(150, "tourbillon_ame_ardente", ComboTier.RARE, 0x38BDF8, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SWORD, 1, "blazing_soul_sweep"),
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SWORD, 1, "wither_edge"))));
        list.add(new ComboDef(151, "ricochet_tracant", ComboTier.RARE, 0xFDE047, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "ricochet"),
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "tracer_shot"))));
        list.add(new ComboDef(152, "salve_suppression", ComboTier.RARE, 0x94A3B8, List.of(
                req(EquipmentSlot.MAINHAND, Items.CROSSBOW, 1, "multishot_bow"),
                req(EquipmentSlot.MAINHAND, Items.CROSSBOW, 1, "suppression_shot"))));
        list.add(new ComboDef(153, "vortex_electrique_marin", ComboTier.RARE, 0x06B6D4, List.of(
                req(EquipmentSlot.MAINHAND, Items.TRIDENT, 1, "undertow"),
                req(EquipmentSlot.MAINHAND, Items.TRIDENT, 1, "hydro_shock", "chain_lightning"))));
        list.add(new ComboDef(154, "forge_filon_ardent", ComboTier.RARE, 0xFB923C, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_PICKAXE, 1, "vein_miner"),
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_PICKAXE, 1, "molten"))));
        list.add(new ComboDef(155, "abatteur_ouragan", ComboTier.RARE, 0x60A5FA, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_AXE, 1, "lumberjack"),
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_AXE, 1, "overcharge", "thundercall_pact"))));
        list.add(new ComboDef(156, "faucheuse_sanglante", ComboTier.RARE, 0xEF4444, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_HOE, 1, "execute_power"),
                req(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, 1, "vital_reserve"))));
        list.add(new ComboDef(157, "pique_faucon_plongeant", ComboTier.RARE, 0x38BDF8, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SWORD, 1, "critical_strike", "damage_critical"),
                req(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, 1, "gale"))));
        list.add(new ComboDef(158, "sumo_sismique", ComboTier.RARE, 0xF59E0B, List.of(
                req(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, 1, "big_stomach", "corpulent"),
                req(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, 1, "bouncy"))));
        list.add(new ComboDef(159, "assassin_echange", ComboTier.RARE, 0xA855F7, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "swap_arrow"),
                req(EquipmentSlot.LEGS, Items.DIAMOND_LEGGINGS, 1, "phantom_step"))));
        list.add(new ComboDef(160, "berserker_flammes", ComboTier.RARE, 0xFF6B35, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SWORD, 1, "blazing_trauma", "fiery_sweep"),
                req(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, 1, "blazing_rage", "blaze_battle"),
                req(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, 1, "ember_core"))));
        list.add(new ComboDef(161, "commandant_meute_fer", ComboTier.RARE, 0x64748B, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SWORD, 1, "army_summon", "summon_army"),
                req(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, 1, "illager_commander", "undead_commander"))));
        list.add(new ComboDef(162, "brise_garde_tonnerre", ComboTier.RARE, 0xFBBF24, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_AXE, 1, "guard_breaker"),
                req(EquipmentSlot.OFFHAND, Items.SHIELD, 1, "bash_power"))));
        list.add(new ComboDef(163, "dragueur_tresors_abysses", ComboTier.RARE, 0x2DD4BF, List.of(
                req(EquipmentSlot.MAINHAND, Items.FISHING_ROD, 1, "dredge"),
                req(EquipmentSlot.MAINHAND, Items.FISHING_ROD, 1, "anglers_blessing"))));
        list.add(new ComboDef(164, "tireur_fantome_murmure", ComboTier.RARE, 0x818CF8, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "whisper"),
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "sniper"))));
        list.add(new ComboDef(165, "jugement_agonie", ComboTier.RARE, 0xDC2626, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SWORD, 1, "last_stand_slayer"),
                req(EquipmentSlot.LEGS, Items.DIAMOND_LEGGINGS, 1, "hunters_pace"))));
        list.add(new ComboDef(166, "carapace_cinetique", ComboTier.RARE, 0x60A5FA, List.of(
                req(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, 1, "adaptive_shell"),
                req(EquipmentSlot.FEET, Items.DIAMOND_BOOTS, 1, "panic_escape"))));
        list.add(new ComboDef(167, "trident_empalement_solaire", ComboTier.RARE, 0xF97316, List.of(
                req(EquipmentSlot.MAINHAND, Items.TRIDENT, 1, "scorching_prongs"),
                req(EquipmentSlot.MAINHAND, Items.TRIDENT, 1, "lingering_skewer"))));
        list.add(new ComboDef(168, "pacte_sang_ecarlate", ComboTier.RARE, 0xFB7185, List.of(
                req(EquipmentSlot.MAINHAND, Items.DIAMOND_SWORD, 1, "blood_pact"),
                req(EquipmentSlot.CHEST, Items.DIAMOND_CHESTPLATE, 1, "pain_devour"))));
        list.add(new ComboDef(169, "ailes_orageuses_maitrisees", ComboTier.RARE, 0x38BDF8, List.of(
                req(EquipmentSlot.CHEST, Items.ELYTRA, 1, "sky_conductor_curse", "turbulence_curse", "brittle_wings_curse"),
                req(EquipmentSlot.HEAD, Items.DIAMOND_HELMET, 1, "veil"))));
        list.add(new ComboDef(170, "lame_surcharge_critique", ComboTier.EPIC, 0x22D3EE, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 1, "overclocked_crit"),
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 1, "overcharge"))));
        list.add(new ComboDef(171, "sniper_spectral_absolu", ComboTier.EPIC, 0xA855F7, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "phasing"),
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "steady_shot", "instant_shot"))));
        list.add(new ComboDef(172, "inquisiteur_purificateur", ComboTier.EPIC, 0xFEF08A, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 1, "seraphs_judgment"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "light_heart"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "radiant_ward"))));
        list.add(new ComboDef(173, "pacte_salamandre_ardent", ComboTier.EPIC, 0xFF5722, List.of(
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "salamanders_vow"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "burning_heart", "fire_shell"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "blaze_walker"))));
        list.add(new ComboDef(174, "kraken_ressac_maudit", ComboTier.EPIC, 0x0EA5E9, List.of(
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 1, "riptide_curse"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "water_heart", "conduit_heart"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "tidal_ward"))));
        list.add(new ComboDef(175, "predateur_voidling_nocturne", ComboTier.EPIC, 0x7C3AED, List.of(
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "voidlings_hunger"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "shadow_heart"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 1, "umbral_pact"))));
        list.add(new ComboDef(176, "danseur_mirage_zephyr", ComboTier.EPIC, 0x67E8F9, List.of(
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "zephyrs_gambit"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 1, "evasive_footwork"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "burst_step"))));
        list.add(new ComboDef(177, "colosse_racines_pierre", ComboTier.EPIC, 0xD97706, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_AXE, 1, "titans_grasp"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 1, "stonebound_roots"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "sticky"))));
        list.add(new ComboDef(178, "artilleur_chaos_alchimique", ComboTier.EPIC, 0xEC4899, List.of(
                req(EquipmentSlot.MAINHAND, Items.CROSSBOW, 1, "chaos"),
                req(EquipmentSlot.MAINHAND, Items.CROSSBOW, 1, "delay"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "perception"))));
        list.add(new ComboDef(179, "maitre_lames_combo", ComboTier.EPIC, 0xF43F5E, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 1, "combo_slash"),
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 2, "crit_rate"))));
        list.add(new ComboDef(180, "foreur_coeur_monde", ComboTier.EPIC, 0x10B981, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_PICKAXE, 1, "vein_miner", "extracting"),
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_PICKAXE, 1, "prospecting"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "earth_heart"))));
        list.add(new ComboDef(181, "voltigeur_plongeon_meteore", ComboTier.EPIC, 0xFB923C, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "grapple"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "meteor_stomp"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 1, "slide"))));
        list.add(new ComboDef(182, "marechal_souverain", ComboTier.EPIC, 0xFBBF24, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 1, "leader"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "general"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "hundred_battles_veteran"))));
        list.add(new ComboDef(183, "terreur_abysse_warden", ComboTier.EPIC, 0x14B8A6, List.of(
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "dread_aura"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "inner_fear", "wardenspine"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "perception", "curse_of_whispers"))));
        list.add(new ComboDef(184, "faucheur_sacrifice_immortel", ComboTier.EPIC, 0xE11D48, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_HOE, 1, "soul_sacrifice_slash"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "life_block"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "twenty_fourth_blessing"))));
        list.add(new ComboDef(185, "cavalier_tonnerre_royal", ComboTier.EPIC, 0xFACC15, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_AXE, 1, "cavalier"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "king"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "horse_boots"))));
        list.add(new ComboDef(186, "archer_distorsion_vide", ComboTier.EPIC, 0xC084FC, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "warp", "teleport_arrow"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "pebblekin"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "cloud_stride"))));
        list.add(new ComboDef(187, "boxeur_diamant_titan", ComboTier.EPIC, 0x60A5FA, List.of(
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "diamond_glow_gauntlet"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "straight_reach_gauntlet", "bone_break_gauntlet"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 1, "focused_steadiness"))));
        list.add(new ComboDef(188, "chimere_sept_coeurs", ComboTier.MYTHIC, 0xF472B6, List.of(
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "heart_resonance"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "chimeras_whim"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "radiant_ward"))));
        list.add(new ComboDef(189, "executeur_apocalypse_hache", ComboTier.MYTHIC, 0xEF4444, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_AXE, 2, "titan_slayer", "giant_slayer"),
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_AXE, 2, "decapitation", "execute_chance"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "sturdy_brute"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "surefooted"))));
        list.add(new ComboDef(190, "tempete_astrale_infinie", ComboTier.MYTHIC, 0x38BDF8, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, 2, "storm_strike"),
                req(EquipmentSlot.MAINHAND, Items.BOW, 1, "multishot_bow"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "electric_heart"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "windrunner"))));
        list.add(new ComboDef(191, "citadelle_miroir_divin", ComboTier.MYTHIC, 0xFDE047, List.of(
                req(EquipmentSlot.OFFHAND, Items.SHIELD, 1, "shield_quake"),
                req(EquipmentSlot.OFFHAND, Items.SHIELD, 2, "perfect_parry"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 2, "divine_protection"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "foresight"))));
        list.add(new ComboDef(192, "faucheuse_necropole_royale", ComboTier.MYTHIC, 0xA855F7, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_HOE, 2, "soul_reaping_sickle"),
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_HOE, 2, "sickle_slash"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "undead_commander"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "shadow_heart"))));
        list.add(new ComboDef(193, "seigneur_forges_stellaires", ComboTier.MYTHIC, 0xFB923C, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_PICKAXE, 1, "core_harvesting"),
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_PICKAXE, 1, "molten"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "fire_heart"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 2, "miners_guidance"))));
        list.add(new ComboDef(194, "fantome_lame_dimensionnelle", ComboTier.MYTHIC, 0x818CF8, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 2, "predator", "stealth_hunter"),
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 2, "echo_strike"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 1, "phantom_step"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 2, "dimensional_step"))));
        list.add(new ComboDef(195, "archonte_marees_celestes", ComboTier.MYTHIC, 0x06B6D4, List.of(
                req(EquipmentSlot.MAINHAND, Items.TRIDENT, 2, "homing_trident"),
                req(EquipmentSlot.MAINHAND, Items.TRIDENT, 2, "ocean_current"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "conduit_heart"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "cloud_walker"))));
        list.add(new ComboDef(196, "juggernaut_gourmand_colossal", ComboTier.MYTHIC, 0xF97316, List.of(
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 2, "gluttony"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 2, "meat_armor"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "food_is_heaven"),
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 2, "devour"))));
        list.add(new ComboDef(197, "artilleur_meteor_ecarlate", ComboTier.MYTHIC, 0xFF4500, List.of(
                req(EquipmentSlot.MAINHAND, Items.CROSSBOW, 1, "meteor"),
                req(EquipmentSlot.MAINHAND, Items.CROSSBOW, 1, "instant_shot"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "blaze_repulsion_heart"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 2, "gale"))));
        list.add(new ComboDef(198, "champion_maudit_transcendant", ComboTier.MYTHIC, 0xDC2626, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 1, "undead_curse", "nether_curse", "overworld_curse"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "glass_bones_champion_curse"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "voidlings_hunger", "end_curse"))));
        list.add(new ComboDef(199, "aeronaute_supersonique_royal", ComboTier.MYTHIC, 0x7DD3FC, List.of(
                req(EquipmentSlot.CHEST, Items.ELYTRA, 2, "wind_rider"),
                req(EquipmentSlot.CHEST, Items.ELYTRA, 2, "iron_wings"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "foot_placement"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "high_altitude_hypoxia"))));
        list.add(new ComboDef(200, "dieu_hache_cataclysme", ComboTier.ULTRA_RARE, 0xFF6B00, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_AXE, 5, "supreme_sharpness", "ultra_edge", "extreme_sharpness"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 2, "general"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 2, "fire_heart"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 3, "giant_step"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "meteor_stomp"))));
        list.add(new ComboDef(201, "grand_amiral_sept_mers", ComboTier.ULTRA_RARE, 0x00E5FF, List.of(
                req(EquipmentSlot.MAINHAND, Items.FISHING_ROD, 2, "dredge", "anglers_blessing"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "breath_holding"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "conduit_heart"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 1, "riptide_curse"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 2, "tidal_ward"))));
        list.add(new ComboDef(202, "empereur_faux_neant", ComboTier.ULTRA_RARE, 0xA855F7, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_HOE, 3, "execute_power"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "night_veil"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 2, "shadow_heart"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 2, "umbral_pact"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 2, "shadow_sprint"))));
        list.add(new ComboDef(203, "forgeron_divin_relique", ComboTier.ULTRA_RARE, 0xFBBF24, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_PICKAXE, 2, "vein_miner"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 3, "miners_guidance"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "eternal", "chrono_mending"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 2, "big_step", "giant_step"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "blaze_walker"))));
        list.add(new ComboDef(204, "sniper_celeste_omniscient", ComboTier.ULTRA_RARE, 0x38BDF8, List.of(
                req(EquipmentSlot.MAINHAND, Items.BOW, 3, "sniper"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "perception"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 2, "wind_heart"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 2, "evasive_footwork"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "foot_placement"))));
        list.add(new ComboDef(205, "citadelle_immortelle_egide", ComboTier.ULTRA_RARE, 0xFDE047, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 2, "retaliation"),
                req(EquipmentSlot.OFFHAND, Items.SHIELD, 1, "shield_quake"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 2, "radiant_ward"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 3, "divine_protection"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 2, "stonebound_roots"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "twenty_fourth_blessing"))));
        list.add(new ComboDef(206, "avatar_chimere_primordiale", ComboTier.ULTRA_RARE, 0xEC4899, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 2, "thundercall_pact"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 2, "ember_core"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "heart_resonance"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 1, "riptide_curse"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 1, "chimeras_whim"))));
        list.add(new ComboDef(207, "seigneur_fleau_toxique", ComboTier.ULTRA_RARE, 0x84CC16, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 3, "wither_edge"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "priests_blessing"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 1, "dread_aura"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 2, "hunters_pace"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 2, "super_stinky_feet"))));
        list.add(new ComboDef(208, "empereur_commandant_supreme", ComboTier.ULTRA_RARE, 0xF59E0B, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 1, "summon_army"),
                req(EquipmentSlot.OFFHAND, Items.SHIELD, 1, "hatred_draw"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 1, "commander"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 3, "hundred_battles_veteran"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 2, "windrunner"))));
        list.add(new ComboDef(209, "transcendance_chance_infinie", ComboTier.ULTRA_RARE, 0xFFFBEB, List.of(
                req(EquipmentSlot.MAINHAND, Items.NETHERITE_SWORD, 5, "overclocked_crit"),
                req(EquipmentSlot.OFFHAND, Items.SHIELD, 2, "perfect_parry"),
                req(EquipmentSlot.HEAD, Items.NETHERITE_HELMET, 3, "king"),
                req(EquipmentSlot.CHEST, Items.NETHERITE_CHESTPLATE, 2, "light_heart"),
                req(EquipmentSlot.LEGS, Items.NETHERITE_LEGGINGS, 3, "leap_power"),
                req(EquipmentSlot.FEET, Items.NETHERITE_BOOTS, 2, "cloud_walker"))));

        COMBOS = Collections.unmodifiableList(list);
        int[] minSlots = new int[COMBOS.size()];
        Map<String, List<ComboDef>> map = new HashMap<>();
        for (ComboDef c : COMBOS) {
            int slotBits = 0;
            for (PieceRequirement p : c.pieces()) {
                slotBits |= (1 << p.slot().ordinal());
                for (String path : p.acceptedPaths()) {
                    map.computeIfAbsent(path.toLowerCase(Locale.ROOT), k -> new ArrayList<>()).add(c);
                }
            }
            minSlots[c.index()] = Integer.bitCount(slotBits);
        }
        MIN_DISTINCT_SLOTS = minSlots;
        BY_ENCHANT_PATH = Collections.unmodifiableMap(map);
    }

    private static final class MaskCache {
        ComboMask mask = ComboMask.EMPTY;
        int computedTick = -999;
        long lastGameTime;
    }

    private static final Map<Long, MaskCache> MASK_CACHE = new ConcurrentHashMap<>();

    private static long sideKey(LivingEntity entity) {
        long side = entity.level().isClientSide() ? 1L : 0L;
        return (side << 32) | (entity.getId() & 0xFFFFFFFFL);
    }

    public static ComboMask getActiveMask(LivingEntity entity) {
        if (entity == null || !entity.isAlive()) return ComboMask.EMPTY;
        long key = sideKey(entity);
        MaskCache mc = MASK_CACHE.computeIfAbsent(key, k -> new MaskCache());
        int tick = entity.tickCount;
        if (Math.abs(tick - mc.computedTick) < 5) return mc.mask;
        long now = entity.level().getGameTime();
        if ((tick & 255) == 0 && MASK_CACHE.size() > 256) {
            MASK_CACHE.entrySet().removeIf(e -> now - e.getValue().lastGameTime > 600L);
        }
        ComboMask m = computeRawMask(entity);
        mc.mask = m;
        mc.computedTick = tick;
        mc.lastGameTime = now;
        return m;
    }

    private static final EquipmentSlot[] ALL_COMBO_SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };

    public static ComboMask computeRawMask(LivingEntity entity) {
        if (entity == null) return ComboMask.EMPTY;
        // T332-ANTI-LAG : Comptage O(1) des slots enchantés sans allocation ; sortie immédiate
        // si 0 slot enchanté, et court-circuit par nombre minimal de slots distincts pour chaque combo.
        int enchantedSlots = 0;
        for (EquipmentSlot slot : ALL_COMBO_SLOTS) {
            ItemStack st = entity.getItemBySlot(slot);
            if (st != null && !st.isEmpty() && st.isEnchanted()) {
                enchantedSlots++;
            }
        }
        if (enchantedSlots == 0) {
            return ComboMask.EMPTY;
        }
        long w0 = 0L, w1 = 0L, w2 = 0L, w3 = 0L;
        for (ComboDef c : COMBOS) {
            int idx = c.index();
            if (enchantedSlots < MIN_DISTINCT_SLOTS[idx]) continue;
            if (c.matchesEntity(entity)) {
                switch (idx >> 6) {
                    case 0 -> w0 |= (1L << idx);
                    case 1 -> w1 |= (1L << idx);
                    case 2 -> w2 |= (1L << idx);
                    case 3 -> w3 |= (1L << idx);
                    default -> {}
                }
            }
        }
        return new ComboMask(w0, w1, w2, w3);
    }

    public static boolean hasCombo(LivingEntity entity, int comboIdx) {
        if (entity == null || comboIdx < 0 || comboIdx >= COMBOS.size()) return false;
        return getActiveMask(entity).has(comboIdx);
    }

    public static List<ComboDef> combosForEnchantPath(String path) {
        if (path == null || path.isEmpty()) return List.of();
        return BY_ENCHANT_PATH.getOrDefault(path.toLowerCase(Locale.ROOT), List.of());
    }

    public static MutableComponent slotComponent(EquipmentSlot slot) {
        String key = "tooltip.deep_lucky_block.slot." + slot.getName().toLowerCase(Locale.ROOT);
        boolean fr = TranslationFallback.isFrench();
        String fallback = switch (slot) {
            case HEAD -> fr ? "Casque" : "Helmet";
            case CHEST -> fr ? "Plastron/\u00c9lytres" : "Chestplate/Elytra";
            case LEGS -> fr ? "Jambi\u00e8res" : "Leggings";
            case FEET -> fr ? "Bottes" : "Boots";
            case MAINHAND -> fr ? "Arme/Outil" : "Weapon/Tool";
            case OFFHAND -> fr ? "Bouclier" : "Shield";
            default -> slot.getName();
        };
        return TranslationFallback.translatableOrFallback(key, fallback);
    }

    public static String slotLabelFrOrEn(EquipmentSlot slot) {
        return slotComponent(slot).getString();
    }

    private static String romanMin(int lvl) {
        return switch (lvl) {
            case 2 -> " II+";
            case 3 -> " III+";
            case 4 -> " IV+";
            case 5 -> " V+";
            case 6 -> " VI+";
            case 7 -> " VII+";
            case 8 -> " VIII+";
            case 9 -> " IX+";
            case 10 -> " X+";
            default -> lvl > 1 ? " " + lvl + "+" : "";
        };
    }

    public static Component buildRequirementsSummary(ComboDef combo, LivingEntity viewer) {
        MutableComponent out = Component.empty();
        List<PieceRequirement> pcs = combo.pieces();
        for (int i = 0; i < pcs.size(); i++) {
            PieceRequirement req = pcs.get(i);
            boolean satisfied = viewer != null && req.matches(viewer.getItemBySlot(req.slot()));
            String enchKey = "enchantment.deep_lucky_block." + req.primaryPath();
            MutableComponent enchName = TranslationFallback.translatableOrFallback(enchKey, req.primaryPath().replace('_', ' '));
            if (req.minLevel() > 1) {
                enchName = enchName.append(Component.literal(romanMin(req.minLevel())));
            }
            MutableComponent part = Component.empty()
                    .append(slotComponent(req.slot()).withStyle(ChatFormatting.GRAY))
                    .append(Component.literal(": ").withStyle(ChatFormatting.GRAY))
                    .append(enchName.withStyle(satisfied ? ChatFormatting.GREEN : ChatFormatting.YELLOW));
            if (i > 0) out.append(Component.literal(" + ").withStyle(ChatFormatting.DARK_GRAY));
            out.append(part);
        }
        return out;
    }

    private static boolean isStackContributingToActiveCombo(ComboDef combo, ItemStack stack, Player viewer) {
        if (combo == null || stack == null || stack.isEmpty() || viewer == null) return false;
        for (PieceRequirement req : combo.pieces()) {
            if (!req.matches(stack)) continue;
            ItemStack worn = viewer.getItemBySlot(req.slot());
            if (!worn.isEmpty() && (worn == stack || ItemStack.matches(worn, stack))) {
                return true;
            }
        }
        return false;
    }

    private static List<String> wrapPlainText(String text, int maxWidth) {
        if (text == null || text.isBlank()) return List.of();
        String trimmed = text.trim();
        if (trimmed.length() <= maxWidth) return List.of(trimmed);
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (String word : trimmed.split("\\s+")) {
            if (word.isEmpty()) continue;
            if (current.length() > 0 && current.length() + 1 + word.length() > maxWidth) {
                out.add(current.toString());
                current.setLength(0);
            } else if (current.length() > 0) {
                current.append(' ');
            }
            current.append(word);
        }
        if (current.length() > 0) out.add(current.toString());
        return out;
    }

    /**
     * T316-NO-SPOIL-SHIFT-DESC :
     * - Ne spoile AUCUN combo potentiel/inactif (ni normalement, ni avec Shift) : la surprise reste totale !
     * - Si un ou plusieurs combos sont ACTIFS sur le joueur et que l'item survolé en fait partie :
     *   - Sans Shift : affiche la ligne dorée du combo actif + son badge de palier.
     *   - Avec Shift : affiche juste SOUS chaque combo actif sa description complète (ce que ça fait et dans quelle circonstance)
     *     proprement découpée à la ligne, suivie du rappel des pièces actives qui le composent.
     */
    public static List<ComboDef> getActiveCombosOnStack(ItemStack stack, Player viewer) {
        if (viewer == null || stack == null || stack.isEmpty() || !stack.isEnchanted()) return List.of();
        ComboMask activeMask = computeRawMask(viewer);
        if (activeMask.isEmpty()) return List.of();

        Map<Enchantment, Integer> enchMap = DeepEnchant.map(stack);
        if (enchMap.isEmpty()) return List.of();

        List<ComboDef> activeOnThisStack = new ArrayList<>();
        for (Enchantment en : enchMap.keySet()) {
            String path = CustomEnchantment.pathOf(en);
            for (ComboDef c : combosForEnchantPath(path)) {
                if (activeMask.has(c.index()) && !activeOnThisStack.contains(c)
                        && isStackContributingToActiveCombo(c, stack, viewer)) {
                    activeOnThisStack.add(c);
                }
            }
        }
        return activeOnThisStack;
    }

    public static Component buildComboPiecesSummary(ComboDef c, Player viewer) {
        return buildRequirementsSummary(c, viewer);
    }

    public static List<Component> buildTooltipComboLines(ItemStack stack, Player viewer, boolean shiftHeld) {
        List<ComboDef> activeOnThisStack = getActiveCombosOnStack(stack, viewer);
        if (activeOnThisStack.isEmpty()) return List.of();

        boolean fr = TranslationFallback.isFrench();
        List<Component> lines = new ArrayList<>();
        for (ComboDef c : activeOnThisStack) {
            MutableComponent activePrefix = TranslationFallback.translatableOrFallback(
                    "tooltip.deep_lucky_block.combo_active",
                    fr ? "\u2726 Synergie Active : " : "\u2726 Active Synergy: ")
                    .withStyle(ChatFormatting.GOLD);
            lines.add(Component.literal("  ")
                    .append(activePrefix)
                    .append(c.translatedName())
                    .append(" ")
                    .append(c.tier().badge()));

            if (shiftHeld) {
                String resolvedDesc = TranslationFallback.resolve(c.descKey());
                if (resolvedDesc == null || resolvedDesc.isBlank()) {
                    resolvedDesc = c.translatedDesc().getString();
                }
                List<String> wrapped = wrapPlainText(resolvedDesc, 52);
                for (int i = 0; i < wrapped.size(); i++) {
                    if (i == 0) {
                        lines.add(Component.literal("    \u25b8 ")
                                .withStyle(ChatFormatting.YELLOW)
                                .append(Component.literal(wrapped.get(i)).withStyle(ChatFormatting.WHITE)));
                    } else {
                        lines.add(Component.literal("      " + wrapped.get(i)).withStyle(ChatFormatting.WHITE));
                    }
                }
                MutableComponent piecesLabel = TranslationFallback.translatableOrFallback(
                        "tooltip.deep_lucky_block.combo_pieces_label",
                        fr ? "Pi\u00e8ces : " : "Pieces: ")
                        .withStyle(ChatFormatting.DARK_AQUA);
                lines.add(Component.literal("    \u2022 ")
                        .withStyle(ChatFormatting.DARK_GRAY)
                        .append(piecesLabel)
                        .append(buildRequirementsSummary(c, viewer)));
            }
        }
        return lines;
    }
}
