package deepluckyblock.recipe;

import deepluckyblock.DeepLuckyBlockMod;
import deepluckyblock.block.DeepLuckyBlockBlock;
import deepluckyblock.item.DeepLuckyBlockItem;
import deepluckyblock.init.DeepLuckyBlockModBlocks;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.Locale;
import java.util.Map;

/**
 * Recette "infusion de chance" — PORT 1.20.1 -> 1.21.1.
 *
 * Un Deep Lucky Block + des objets de chance (ou de malchance) dans la grille
 * => un Deep Lucky Block dont le bonus est modifié (rendement dégressif au-delà
 * de 25 depuis T350, chaque item consommé -1 unité).
 *
 * ⚠️ API 1.21.1 :
 *  - CustomRecipe(CraftingBookCategory) — plus de ResourceLocation en param
 *    (l'id vient du nom d'enregistrement du sérialiseur).
 *  - matches/assemble/getRemainingItems passent de CraftingContainer ->
 *    CraftingInput (interface : getContainerSize()/getItem(i)).
 *  - getResultItem(HolderLookup.Provider) — plus RegistryAccess.
 *  - Le sérialiseur reste SimpleCraftingRecipeSerializer (Factory =
 *    create(CraftingBookCategory)) mais s'enregistre dans la registry
 *    Registries.RECIPE_SERIALIZER (DeferredRegister, pas ForgeRegistries).
 *
 * JSON (data/deep_lucky_block/recipe/deep_lucky_block_infusion.json) :
 *   { "type": "deep_lucky_block:deep_lucky_block_crafting" }
 */
public class DeepLuckyBlockCraftingRecipe extends CustomRecipe {

    public DeepLuckyBlockCraftingRecipe(CraftingBookCategory category) {
        super(category);
    }

    /**
     * L'infusion est possible dès que le bloc est présent n'importe où dans la
     * grille (au moins 2x2 occupés par bloc + >=1 objet, voir canCraftInDimensions).
     */
    @Override
    // PORT 1.21.1 : Recipe.matches(CraftingInput, Level).
    public boolean matches(CraftingInput inv, net.minecraft.world.level.Level level) {
        int luckyCount = 0;
        int luckItems = 0;

        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty()) continue;

            if (isDeepLuckyBlock(s)) {
                luckyCount++;
            } else if (getLuckCenti(s) != 0) {
                luckItems++;
            } else {
                // Un item non reconnu dans la grille -> pas une infusion valide.
                return false;
            }
        }

        return luckyCount == 1 && luckItems >= 1;
    }

    @Override
    // PORT 1.21.1 : Recipe.assemble(CraftingInput, HolderLookup.Provider).
    public ItemStack assemble(CraftingInput inv, net.minecraft.core.HolderLookup.Provider registries) {
        ItemStack sourceBlock = ItemStack.EMPTY;
        int currentLuck = 0;

        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getItem(i);
            if (!s.isEmpty() && isDeepLuckyBlock(s)) {
                sourceBlock = s;
                currentLuck = DeepLuckyBlockItem.getLuckBonus(s);
                break;
            }
        }

        if (sourceBlock.isEmpty()) return ItemStack.EMPTY;

        // T458 : tout le calcul se fait en CENTIEMES de luck, puis un seul arrondi.
        int simulatedCenti = currentLuck * 100;
        int toAddCenti = 0;

        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || isDeepLuckyBlock(s)) continue;

            int val = applyLuckMultipliers(s, getLuckCenti(s));
            if (val == 0) continue;

            int add = val;
            // Rendement dégressif : au-delà de 25 de luck, un matériau positif ne rend
            // plus que la moitié. Le plancher est désormais 1 CENTIEME et non 1 luck :
            // avec des valeurs fractionnaires, un plancher à 100 rendrait l'émeraude
            // (75) PLUS rentable après le seuil qu'avant, ce qui est absurde.
            if (simulatedCenti >= DEGRESSIVE_THRESHOLD_CENTI && val > 0) {
                add = Math.max(1, val / 2);
            }

            simulatedCenti += add;
            toAddCenti += add;
        }

        // Arrondi au plus proche, symétrique pour les malus (calcul entier : aucune
        // dérive flottante possible).
        int toAdd = toAddCenti >= 0
                ? (toAddCenti + 50) / 100
                : -((-toAddCenti + 50) / 100);

        int finalLuck = Math.max(DeepLuckyBlockBlock.MIN_LUCK_BONUS,
                Math.min(DeepLuckyBlockBlock.MAX_LUCK_BONUS, currentLuck + toAdd));

        ItemStack result = new ItemStack(DeepLuckyBlockModBlocks.DEEP_LUCKY_BLOCK.get(), 1);
        DeepLuckyBlockItem.setLuckBonus(result, finalLuck);
        updateDisplayName(result);

        // FIX v7 (resolu) : trace de diagnostic retiree (se declenchait a
        // chaque assemblage de la recette -> spam en session de craft).

        return result;
    }

    /**
     * Utilisé uniquement par le livre de recettes / JEI (pas par la preview de la grille).
     * On y met un aperçu générique (luck = 0).
     */
    @Override
    public ItemStack getResultItem(HolderLookup.Provider registries) {
        ItemStack preview = new ItemStack(DeepLuckyBlockModBlocks.DEEP_LUCKY_BLOCK.get());
        DeepLuckyBlockItem.setLuckBonus(preview, 0);
        updateDisplayName(preview);
        return preview;
    }

    /**
     * Volontairement tout en EMPTY.
     * Le vanilla ResultSlot.onTake fait `removeItem(i, 1)` sur chaque slot occupé,
     * puis rajoute l'item retourné ici. En retournant EMPTY partout :
     *   - chaque item de luck perd 1 unité,
     *   - le lucky block (count 1) est entièrement consommé.
     * C'est exactement le comportement "-1 par item de luck" souhaité.
     */
    @Override
    public NonNullList<ItemStack> getRemainingItems(CraftingInput inv) {
        return NonNullList.withSize(inv.size(), ItemStack.EMPTY);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width >= 2 && height >= 2;
    }

    private static boolean isDeepLuckyBlock(ItemStack stack) {
        if (stack.isEmpty()) return false;
        if (stack.getItem() instanceof BlockItem bi) {
            return bi.getBlock() instanceof DeepLuckyBlockBlock;
        }
        return false;
    }

    /** Seuil de rendement dégressif, en centièmes : 25 de luck. */
    private static final int DEGRESSIVE_THRESHOLD_CENTI = 2500;

    private static int getLuckCenti(ItemStack stack) {
        if (stack.isEmpty()) return 0;

        // T458 : la table est exprimée en CENTIEMES DE LUCK (100 = +1). Le luck d'un
        // bloc reste un entier, mais les matériaux à l'unité valent désormais des
        // fractions (émeraude +0,75 ; lingot d'or +0,50) : il faut donc accumuler en
        // centièmes et n'arrondir QU'UNE FOIS, à la fin du craft. Arrondir chaque
        // matériau séparément ramènerait 0,75 à 1 et annulerait le rééquilibrage.
        // Les blocs compressés ne sont PAS 9 x l'unité : ils reçoivent une prime
        // d'assemblage (or 9x0,50=4,5 -> +5 ; émeraude 9x0,75=6,75 -> +7 ; diamant
        // 9x1=9 -> +10).
        // T350-LUCK-RESCALE : nouvelle base « plus chère » demandée en jeu.
        // Ancres explicites : Lingot de Netherite = +5 (avant +20), Diamant = +1 (avant +3),
        // Emeraude = +1 (avant +2). TOUT le reste est recalcule de façon cohérente à partir de
        // ces trois ancres (échelle ~4x plus stricte qu'avant), en gardant les mêmes rapports
        // de valeur entre matériaux :
        //   1 diamant = +1 | bloc de diamant = +10 | lingot netherite = +5 | bloc = +45
        //   1 emeraude = +0,75 | bloc d'emeraude = +7 | lingot d'or = +0,50 | bloc d'or = +5

        // 1. Objets Positifs Vanilla (du plus commun au plus rare)
        if (stack.is(Items.GOLD_INGOT))              return 50;    // T458 : +0,50 / unite
        if (stack.is(Items.GOLD_BLOCK))              return 500;   // T458 : +5
        if (stack.is(Items.EMERALD))                 return 75;    // T458 : +0,75 / unite
        if (stack.is(Items.EMERALD_BLOCK))           return 700;   // T458 : +7
        if (stack.is(Items.DIAMOND))                 return 100;   // +1 (inchange)
        if (stack.is(Items.DIAMOND_BLOCK))           return 1000;  // T458 : +10
        if (stack.is(Items.GOLDEN_CARROT))           return 200;   // +2
        if (stack.is(Items.GOLDEN_APPLE))            return 600;   // +6
        if (stack.is(Items.ENCHANTED_GOLDEN_APPLE))  return 1500;  // +15

        // T457 : la nourriture simple / les récoltes NE SONT PLUS des matériaux d'infusion.
        // Retirés : pomme, carotte, pain, pomme de terre (crue et cuite), bœuf / porc /
        // poulet cuits, baies sucrées, baies lumineuses. Ils rendaient l'infusion triviale
        // (+8 par craft avec des objets de ferme renouvelables à l'infini).
        // ATTENTION si l'on souhaite un jour les réintégrer : ici une valeur 0 ne signifie
        // pas « n'ajoute rien » mais « objet non reconnu », et matches() REFUSE alors la
        // grille entière. Un aliment posé dans la table annule donc tout le craft.
        // Les formes dorées restent valides : carotte dorée +2, pomme d'or +6, pomme d'or
        // enchantée +15.

        // Toutes les formes de Netherite (ancre : lingot = 5)
        if (stack.is(Items.NETHERITE_SCRAP))         return 200;
        if (stack.is(Items.NETHERITE_INGOT))         return 500;   // T350 : +5 (au lieu de +20)
        if (stack.is(Items.NETHERITE_BLOCK))         return 4500;  // 9 lingots

        // Trophées et Boss Vanilla
        if (stack.is(Items.NETHER_STAR))             return 2500;
        if (stack.is(Items.DRAGON_HEAD))             return 2000;
        if (stack.is(Items.DRAGON_EGG))              return 2500;

        // 2. Objets Malchanceux (Malédictions / Luck Négative — recalibrés sur la même échelle)
        if (stack.is(Items.ROTTEN_FLESH))            return -100;
        if (stack.is(Items.SLIME_BALL))              return -100;
        if (stack.is(Items.BONE))                    return -100;
        if (stack.is(Items.SPIDER_EYE))              return -200;
        if (stack.is(Items.POISONOUS_POTATO))        return -300;
        if (stack.is(Items.FERMENTED_SPIDER_EYE))    return -600;
        if (stack.is(Items.PUFFERFISH))              return -600;
        if (stack.is(Items.WITHER_SKELETON_SKULL))   return -1200;

        // 3. Objets Suprêmes du Mod (Paliers Rare & Signature)
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) {
            String path = id.getPath().toLowerCase(Locale.ROOT);
            if ("firarain_potion".equals(path))      return 800;  // Palier Rare
            if ("shadow_essence".equals(path))       return 2000; // Palier Signature
            // Œufs / trophées de dragon custom du mod
            if (path.contains("dragon_egg"))         return 2500;
            if (path.endsWith("_dragon") || path.endsWith("dragon_egg")) return 2500;
        }

        return 0;
    }

    // FIX (demande en jeu : "je veux que tu m'ajoute une option dans un
    // fichier [...] qui me permette de recalculer le prix des infusions
    // luck de tous mes items [...] individuellement dans ce fichier
    // modifier le taux de luck ajoute, je peux aussi sinon de maniere
    // generale reduire ou augmenter les gains") : applique
    // LuckInfusionConfig.globalLuckMultiplier (tous les items a la fois) ET
    // le multiplicateur individuel de CET item precis (perItemLuckMultiplier,
    // s'il en a un), sur la valeur de base codee dans getLuckCenti. Le
    // resultat est arrondi au plus proche, mais jamais ramene a 0 si la
    // valeur de base et les deux multiplicateurs sont non nuls (un
    // multiplicateur tres faible reste au moins +/-1, pour ne jamais rendre
    // un item "de luck" totalement neutre par erreur d'arrondi).
    private static int applyLuckMultipliers(ItemStack stack, int baseValue) {
        if (baseValue == 0) return 0;
        double global = deepluckyblock.config.LuckInfusionConfig.getGlobalMultiplier();
        double individual = 1.0;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) {
            Map<String, Double> overrides = deepluckyblock.config.LuckInfusionConfig.parsePerItemMultipliers();
            String key = id.toString().toLowerCase(Locale.ROOT);
            if (overrides.containsKey(key)) individual = overrides.get(key);
        }
        double result = baseValue * global * individual;
        int rounded = (int) Math.round(result);
        if (rounded == 0 && global > 0 && individual > 0) {
            rounded = result > 0 ? 1 : (result < 0 ? -1 : 0);
        }
        return rounded;
    }

    private static void updateDisplayName(ItemStack stack) {
        int luck = DeepLuckyBlockItem.getLuckBonus(stack);
        if (luck >= 100) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§6§l✦ DEEP'S LUCKY BLOCK [MAX] ✦"));
        } else if (luck <= -100) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§4§l✦ DEEP'S LUCKY BLOCK [MAUDIT MAX] ✦"));
        } else if (luck <= -50) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§c§lDeep's Lucky Block §4[" + luck + "]"));
        } else if (luck < 0) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§6§lDeep's Lucky Block §c[" + luck + "]"));
        } else if (luck >= 50) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§6§lDeep's Lucky Block §b[+" + luck + "]"));
        } else if (luck > 0) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§6§lDeep's Lucky Block §7[+" + luck + "]"));
        } else {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§6§lDeep's Lucky Block"));
        }
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return SERIALIZER.get();
    }

    // PORT 1.21.1 : ForgeRegistries.RECIPE_SERIALIZERS -> Registries.RECIPE_SERIALIZER.
    // SimpleCraftingRecipeSerializer 1.21.1 : Factory = create(CraftingBookCategory).
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
        DeferredRegister.create(Registries.RECIPE_SERIALIZER, DeepLuckyBlockMod.MODID);
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<DeepLuckyBlockCraftingRecipe>> SERIALIZER =
        RECIPE_SERIALIZERS.register("deep_lucky_block_crafting",
            () -> new SimpleCraftingRecipeSerializer<>(category -> new DeepLuckyBlockCraftingRecipe(category)));
}
