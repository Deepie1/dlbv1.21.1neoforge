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
 * de 50, chaque item consommé -1 unité).
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
            } else if (getLuckValue(s) != 0) {
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

        int simulated = currentLuck;
        int toAdd = 0;

        for (int i = 0; i < inv.size(); i++) {
            ItemStack s = inv.getItem(i);
            if (s.isEmpty() || isDeepLuckyBlock(s)) continue;

            int val = applyLuckMultipliers(s, getLuckValue(s));
            if (val == 0) continue;

            int add = val;
            // Rendement dégressif au-delà de 50 de luck cumulé (uniquement pour les ajouts positifs).
            if (simulated >= 50 && val > 0) {
                add = Math.max(1, val / 2);
            }

            simulated += add;
            toAdd += add;
        }

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

    private static int getLuckValue(ItemStack stack) {
        if (stack.isEmpty()) return 0;

        // 1. Objets Positifs Vanilla (Ordre logique : Or < Émeraude < Diamant < Netherite < Boss)
        if (stack.is(Items.GOLD_INGOT))              return 1;
        if (stack.is(Items.GOLD_BLOCK))              return 9;
        if (stack.is(Items.EMERALD))                 return 2;
        if (stack.is(Items.EMERALD_BLOCK))           return 18;
        if (stack.is(Items.DIAMOND))                 return 3;
        if (stack.is(Items.DIAMOND_BLOCK))           return 27;
        if (stack.is(Items.GOLDEN_CARROT))           return 10;
        if (stack.is(Items.GOLDEN_APPLE))            return 20;
        if (stack.is(Items.ENCHANTED_GOLDEN_APPLE))  return 45;

        // Nourriture simple / récoltes : +1 à +2 (pour que les craft "avec mon bloc
        // pour ajouter la luck" s'affichent aussi avec les pommes et aliments basiques).
        if (stack.is(Items.APPLE))                   return 1;
        if (stack.is(Items.CARROT))                   return 1;
        if (stack.is(Items.BREAD))                   return 1;
        if (stack.is(Items.POTATO))                   return 1;
        if (stack.is(Items.BAKED_POTATO))            return 2;
        if (stack.is(Items.COOKED_BEEF))             return 2;
        if (stack.is(Items.COOKED_PORKCHOP))         return 2;
        if (stack.is(Items.COOKED_CHICKEN))          return 2;
        if (stack.is(Items.SWEET_BERRIES))           return 1;
        if (stack.is(Items.GLOW_BERRIES))            return 1;

        // Toutes les formes de Netherite
        if (stack.is(Items.NETHERITE_SCRAP))         return 5;
        if (stack.is(Items.NETHERITE_INGOT))         return 20;
        if (stack.is(Items.NETHERITE_BLOCK))         return 60;

        // Trophées et Boss Vanilla
        if (stack.is(Items.NETHER_STAR))             return 50;
        if (stack.is(Items.DRAGON_HEAD))             return 50;
        if (stack.is(Items.DRAGON_EGG))              return 50; // +50 (affiche bien "+50")

        // 2. Objets Malchanceux (Malédictions / Luck Négative - Ordre logique croissant en malus)
        if (stack.is(Items.ROTTEN_FLESH))            return -2;
        if (stack.is(Items.SLIME_BALL))              return -3;
        if (stack.is(Items.BONE))                    return -3;
        if (stack.is(Items.SPIDER_EYE))              return -5;
        if (stack.is(Items.POISONOUS_POTATO))        return -10;
        if (stack.is(Items.FERMENTED_SPIDER_EYE))    return -20;
        if (stack.is(Items.PUFFERFISH))              return -20;
        if (stack.is(Items.WITHER_SKELETON_SKULL))   return -35;

        // 3. Objets Suprêmes du Mod (Paliers Rare & Signature)
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id != null) {
            String path = id.getPath().toLowerCase(Locale.ROOT);
            if ("firarain_potion".equals(path))      return 25; // Palier Rare (+25)
            if ("shadow_essence".equals(path))       return 75; // Palier Signature (+75)
            // Œufs / trophées de dragon custom du mod -> +50 (comme le dragon_head vanilla)
            if (path.contains("dragon_egg"))         return 50;
            if (path.endsWith("_dragon") || path.endsWith("dragon_egg")) return 50;
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
    // s'il en a un), sur la valeur de base codee dans getLuckValue. Le
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
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§c§lDeep's Lucky Block §4[" + luck + "%]"));
        } else if (luck < 0) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§6§lDeep's Lucky Block §c[" + luck + "%]"));
        } else if (luck >= 50) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§6§lDeep's Lucky Block §b[+" + luck + "%]"));
        } else if (luck > 0) {
            stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§6§lDeep's Lucky Block §7[+" + luck + "%]"));
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
