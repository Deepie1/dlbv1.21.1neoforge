package deepluckyblock;

import deepluckyblock.init.DeepLuckyBlockModItems;
import deepluckyblock.item.BobSpawnEggItem;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.HashSet;
import java.util.Set;

/**
 * CustomCreativeTabs : onglet creatif + items custom, NON regenere par MCreator.
 * Survit aux rebuilds (enregistre dans le bloc "user code" de DeepLuckyBlockMod).
 *
 * AJOUT AUTOMATIQUE : tous les items portant le namespace "deep_lucky_block"
 * (armures, armes, spawn eggs, blocs, oeuf de Bob, futurs items...) sont ajoutes
 * a l'onglet automatiquement. Plus besoin de les lister un par un.
 */
public class CustomCreativeTabs {

    // Onglet creatif
    public static final DeferredRegister<CreativeModeTab> REGISTRY =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, DeepLuckyBlockMod.MODID);

    // Items custom (oeuf de Bob)
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(BuiltInRegistries.ITEM, DeepLuckyBlockMod.MODID);

    public static final DeferredHolder<Item, Item> BOB_SPAWN_EGG = ITEMS.register("bob_spawn_egg",
            () -> new BobSpawnEggItem(new Item.Properties().stacksTo(64)));

    /*
     * Items a EXCLURE de l'ajout automatique (optionnel).
     * Ajoute ici le registry name d'un item que tu ne veux pas voir dans l'onglet,
     * ex. : "deep_lucky_block:item_cheat" ou "deep_lucky_block:debug_item".
     * Laisser vide si tout doit apparaitre.
     */
    private static final Set<String> EXCLUDED_ITEMS = new HashSet<>();

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> DEEP_LUCKY_BLOCK_TAB = REGISTRY.register("deep_lucky_block",
            () -> CreativeModeTab.builder()
                    .title(Component.literal("Deep's Lucky Block"))
                    .icon(() -> new ItemStack(DeepLuckyBlockModItems.DEEP_LUCKY_BLOCK.get()))
                    .displayItems((parameters, output) -> {

                        // ============================================================
                        // AJOUT AUTOMATIQUE : TOUS les items du mod (namespace deep_lucky_block)
                        // Y compris les futurs items ajoutes dans MCreator : rien a modifier ici.
                        // ============================================================
                        for (Item item : BuiltInRegistries.ITEM) {
                            net.minecraft.resources.ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
                            if (key == null || item == Items.AIR) {
                                continue;
                            }
                            if (!DeepLuckyBlockMod.MODID.equals(key.getNamespace())) {
                                continue;
                            }
                            if (EXCLUDED_ITEMS.contains(key.toString())) {
                                continue;
                            }
                            output.accept(new ItemStack(item));
                        }

                        // ============================================================
                        // VARIANTES SPECIALES (avec NBT) : ajoutees a la main car elles
                        // necessitent des tags / noms custom.
                        // ============================================================

                        // === BLOC luck +100 ===
                        ItemStack luck100 = new ItemStack(DeepLuckyBlockModItems.DEEP_LUCKY_BLOCK.get());
                        deepluckyblock.util.DeepNbt.getOrCreateTag(luck100).putInt("SlbLuckBonus", 100);
                        luck100.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("\u00a76\u00a7l\u2726 Deep's Lucky Block [+100 LUCK] \u2726"));
                        output.accept(luck100);

                        // === BLOC luck -100 ===
                        ItemStack luckM100 = new ItemStack(DeepLuckyBlockModItems.DEEP_LUCKY_BLOCK.get());
                        deepluckyblock.util.DeepNbt.getOrCreateTag(luckM100).putInt("SlbLuckBonus", -100);
                        luckM100.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("\u00a74\u00a7l\u2726 Deep's Lucky Block [-100 LUCK] \u2726"));
                        output.accept(luckM100);

                        // === COFFRES (5 rangs, tags dans ForgeData pour LuckyChestAutofillProcedure) ===
                        output.accept(makeLuckyChest(1, "\u00a7aLucky Chest (Normal)"));
                        output.accept(makeLuckyChest(2, "\u00a7eLucky Chest (Good)"));
                        output.accept(makeLuckyChest(3, "\u00a79Lucky Chest (Rare)"));
                        output.accept(makeLuckyChest(4, "\u00a75Lucky Chest (Epic)"));
                        output.accept(makeLuckyChest(5, "\u00a76\u00a7l\u2726 Lucky Chest (Ultimate) \u2726"));

                        // NOTE : les oeufs de spawn (Slime King, Shadow Mirror, Crimson
                        // Butterfly, End Knight) et l'oeuf de Bob sont deja ajoutes
                        // automatiquement par la boucle ci-dessus.
                    })
                    .build());

    private static ItemStack makeLuckyChest(int rank, String displayName) {
        ItemStack chest = new ItemStack(Items.CHEST);
        // FIX v7 (1.21.1/NeoForge) :
        //  - le transfert item->BlockEntity a la pose se fait via le composant
        //    DataComponents.BLOCK_ENTITY_DATA (le legacy CUSTOM_DATA/
        //    "BlockEntityTag" n'est plus lu par vanilla depuis 1.20.5+).
        //  - NeoForge stocke getPersistentData() sous la cle "NeoForgeData"
        //    (l'ancienne cle Forge "ForgeData" est invisible pour
        //    getPersistentData() -> LuckyChestAutofillProcedure ne trouvait
        //    jamais le drapeau -> coffre jamais rempli).
        // Filet de securite : ItemChestTagTransferMixin recopie aussi les
        // drapeaux a la pose si le mecanisme vanilla n'a pas suffi.
        CompoundTag beTag = new CompoundTag();
        // OBLIGATOIRE en 1.21.1 : le composant minecraft:block_entity_data exige
        // un champ racine "id" identifiant le type de block entity (IdDispatchCodec).
        // Son absence provoque un DecoderException "Missing id for entity" des que
        // le stack est (de)serialise sur le reseau -> crash/deconnexion au clic.
        beTag.putString("id", "minecraft:chest");
        CompoundTag neoForgeData = new CompoundTag();
        neoForgeData.putBoolean("slb_auto_chest", true);
        neoForgeData.putBoolean("slb_filled", false);
        neoForgeData.putInt("slb_chest_rank", rank);
        beTag.put("NeoForgeData", neoForgeData);
        chest.set(net.minecraft.core.component.DataComponents.BLOCK_ENTITY_DATA,
                net.minecraft.world.item.component.CustomData.of(beTag));
        chest.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(displayName));
        return chest;
    }
}
