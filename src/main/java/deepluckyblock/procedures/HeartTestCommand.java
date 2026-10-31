package deepluckyblock.procedures;

import deepluckyblock.util.DeepEnchant;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.enchantment.Enchantment;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

import java.util.ArrayList;
import java.util.List;

/**
 * /testhearts — donne une shulker box remplie de pièces d'équipement
 * précablées avec CHACUN des 17 enchantements liés aux cœurs du mod, pour
 * tester rapidement sans farmer le Lucky Block.
 *
 * <p>Les 17 enchantements "cœur" (voir table ci-dessous) sont répartis sur
 * 3 pièces d'armure selon leur {@code slots} déclaré en JSON (voir
 * {@code data/deep_lucky_block/enchantment/*.json}), chaque pièce recevant
 * tous ses niveaux au max déclaré :
 * <ul>
 *   <li>Plastron (chest, 14 enchants) : fire_heart, water_heart, earth_heart,
 *       wind_heart, electric_heart, shadow_heart, light_heart,
 *       heart_resonance, heartbeat_rhythm, dread_aura, thirsty_heart,
 *       conduit_heart, blaze_repulsion_heart, burning_heart ;</li>
 *   <li>Casque (head, 2 enchants) : radiant_ward, curse_of_whispers ;</li>
 *   <li>Jambières (legs, 1 enchant) : umbral_pact.</li>
 * </ul>
 *
 * <p>Remarque : cumuler tous les enchants "cœur" sur UNE SEULE pièce n'est
 * qu'un raccourci de test — en jeu normal, ces enchants passent par la
 * table d'enchantement / le Lucky Block un par un et respectent les
 * incompatibilités habituelles ({@code CustomEnchantment.Cat}), non
 * vérifiées ici puisque c'est un outil de debug volontairement permissif.
 */
@EventBusSubscriber(modid = "deep_lucky_block")
public final class HeartTestCommand {

    private HeartTestCommand() {}

    // (id enchant, niveau à appliquer = max_level déclaré en JSON)
    private static final Object[][] CHEST_ENCHANTS = {
        {"fire_heart", 3}, {"water_heart", 3}, {"earth_heart", 3}, {"wind_heart", 3},
        {"electric_heart", 3}, {"shadow_heart", 3}, {"light_heart", 3},
        {"heart_resonance", 1}, {"heartbeat_rhythm", 10}, {"dread_aura", 1},
        {"thirsty_heart", 1}, {"conduit_heart", 1}, {"blaze_repulsion_heart", 1},
        {"burning_heart", 3},
    };
    private static final Object[][] HEAD_ENCHANTS = {
        {"radiant_ward", 3}, {"curse_of_whispers", 1},
    };
    private static final Object[][] LEGS_ENCHANTS = {
        {"umbral_pact", 3},
    };

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        event.getDispatcher().register(
            Commands.literal("testhearts")
                .requires(src -> src.hasPermission(2))
                .executes(HeartTestCommand::run)
        );
    }

    private static int run(com.mojang.brigadier.context.CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack source = ctx.getSource();
        if (!(source.getEntity() instanceof ServerPlayer player)) {
            source.sendFailure(Component.literal("Cette commande doit être exécutée par un joueur."));
            return 0;
        }

        ItemStack chest = enchantedPiece(Items.NETHERITE_CHESTPLATE, "§6§lPlastron des Cœurs", CHEST_ENCHANTS);
        ItemStack head = enchantedPiece(Items.NETHERITE_HELMET, "§b§lCasque des Pactes", HEAD_ENCHANTS);
        ItemStack legs = enchantedPiece(Items.NETHERITE_LEGGINGS, "§5§lJambières du Pacte Umbral", LEGS_ENCHANTS);

        List<ItemStack> contents = new ArrayList<>();
        contents.add(chest);
        contents.add(head);
        contents.add(legs);

        ItemStack shulker = new ItemStack(Items.SHULKER_BOX);
        shulker.set(DataComponents.CUSTOM_NAME, Component.literal("§e§lTest — Enchantements de Cœur"));
        shulker.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(contents));

        boolean added = player.getInventory().add(shulker);
        if (!added) {
            player.drop(shulker, false);
        }
        player.inventoryMenu.broadcastChanges();

        source.sendSuccess(() -> Component.literal(
                "§aShulker box de test reçue : §f3 pièces, §a17 enchantements « cœur » §fau total."), true);
        return 1;
    }

    private static ItemStack enchantedPiece(net.minecraft.world.item.Item item, String name, Object[][] enchants) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        for (Object[] pair : enchants) {
            String id = (String) pair[0];
            int level = (Integer) pair[1];
            // FIX (rapporté en jeu : "/testhearts donne un shulker sans aucun
            // enchant visible") : DeepEnchant.byId() délègue à
            // ResourceLocation.tryParse(id), qui, en l'ABSENCE de ':' dans la
            // chaîne, retombe SILENCIEUSEMENT sur le namespace "minecraft"
            // (ex. "light_heart" -> "minecraft:light_heart", qui n'existe
            // pas). byId() renvoyait donc TOUJOURS null pour ces 17
            // enchantements "cœur" (déclarés uniquement sous le namespace
            // deep_lucky_block:...), si bien qu'AUCUN enchantement n'était
            // JAMAIS réellement posé sur les 3 pièces générées par cette
            // commande -- symptôme exact rapporté ("aucune info d'enchant
            // dans le tooltip"). On préfixe désormais explicitement le
            // namespace du mod, comme le fait déjà tout le reste du code
            // (RealAttributeModifierHandler, EquipmentExpansionHandler...).
            Enchantment en = DeepEnchant.byId("deep_lucky_block:" + id);
            if (en != null) {
                DeepEnchant.setLevel(stack, en, level);
            } else {
                org.slf4j.LoggerFactory.getLogger("deep_lucky_block").warn(
                        "[deep_lucky_block] /testhearts : enchant introuvable pour id=deep_lucky_block:{}", id);
            }
        }
        return stack;
    }
}
