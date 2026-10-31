package deepluckyblock.item;

import deepluckyblock.procedures.FirarainPotionDrinkProcedure;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * ═══════════════════════════════════════════════════════════════
 * 🌀 Firarain Potion — item buvable.
 *    • Nom affiché en BLANC dans l'inventaire
 *    • Tooltip unique en gris clair :
 *      "Use to teleport to the player of your choice"
 *    • Animation de boisson (DRINK) — durée 29 ticks
 *    • Rend une GLASS_BOTTLE vide après usage
 *    • À la fin de l'utilisation → FirarainPotionDrinkProcedure.execute(world, x, y, z, entity)
 * ═══════════════════════════════════════════════════════════════
 *
 * ⚠️ IMPORTANT — ce fichier écrase la version GÉNÉRÉE par MCreator :
 *    clic droit sur l'élément Item "Firarain Potion" dans la liste
 *    workspace → "Lock code" 🔒, sinon il sera régénéré/écrasé
 *    au prochain build !
 *    (chemin : src/main/java/deepluckyblock/item/FirarainPotionItem.java)
 */
public class FirarainPotionItem extends Item {

    public FirarainPotionItem() {
        // Pas de .rarity(Rarity.EPIC) volontairement : la rareté EPIC
        // colorerait le nom en violet. Défaut (COMMON) = nom en BLANC.
        super(new Item.Properties()
                .stacksTo(16)
                .fireResistant()
                .food((new FoodProperties.Builder())
                        .nutrition(0)
                        .saturationModifier(0f)
                        .alwaysEdible()
                        .build()));
    }

    /** Animation "boire" comme une vraie potion (retire cette méthode si tu préfères EAT). */
    @Override
    public UseAnim getUseAnimation(ItemStack itemstack) {
        return UseAnim.DRINK;
    }

    @Override
    public int getUseDuration(ItemStack itemstack, net.minecraft.world.entity.LivingEntity entity) {
        return 29;
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.literal("Firarain Potion").withStyle(ChatFormatting.WHITE);
    }

    /** Tooltip : description unique en gris clair, sous le nom (blanc). */
    @Override
    public void appendHoverText(ItemStack itemstack, Item.TooltipContext context, List<Component> list, TooltipFlag flag) {
        super.appendHoverText(itemstack, context, list, flag);
        list.add(Component.literal("Use to teleport to the player of your choice")
                .withStyle(ChatFormatting.GRAY));
    }

    @Override
    public ItemStack finishUsingItem(ItemStack itemstack, Level world, LivingEntity entity) {
        ItemStack retval = new ItemStack(Items.GLASS_BOTTLE);
        super.finishUsingItem(itemstack, world, entity);
        double x = entity.getX();
        double y = entity.getY();
        double z = entity.getZ();

        // Dépendances transmises à la procédure (joueur + monde + position)
        if (!world.isClientSide()) {
            FirarainPotionDrinkProcedure.execute(world, x, y, z, entity);
        }

        if (itemstack.isEmpty()) {
            return retval;
        } else {
            if (entity instanceof Player player && !player.getAbilities().instabuild) {
                if (!player.getInventory().add(retval))
                    player.drop(retval, false);
            }
            return itemstack;
        }
    }
}