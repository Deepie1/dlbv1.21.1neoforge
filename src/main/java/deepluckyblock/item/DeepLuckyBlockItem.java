package deepluckyblock.item;

import deepluckyblock.block.DeepLuckyBlockBlock;
import deepluckyblock.init.DeepLuckyBlockModBlocks;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;

import java.util.List;

public class DeepLuckyBlockItem extends BlockItem {

    public DeepLuckyBlockItem() {
        super(DeepLuckyBlockModBlocks.DEEP_LUCKY_BLOCK.get(), new Item.Properties().stacksTo(64));
    }

    public DeepLuckyBlockItem(Block block, Item.Properties properties) {
        super(block, properties);
    }

    /**
     * T460 : chance en CENTIEMES. Repli sur l'ancienne cle entiere (x100) pour
     * tous les blocs crees avant le passage au fractionnaire.
     */
    public static int getLuckCenti(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !deepluckyblock.util.DeepNbt.hasTag(stack)) return 0;
        CompoundTag tag = deepluckyblock.util.DeepNbt.tag(stack);
        if (tag == null) return 0;
        if (tag.contains(DeepLuckyBlockBlock.NBT_LUCK_CENTI_KEY))
            return tag.getInt(DeepLuckyBlockBlock.NBT_LUCK_CENTI_KEY);
        if (tag.contains(DeepLuckyBlockBlock.NBT_LUCK_KEY))
            return tag.getInt(DeepLuckyBlockBlock.NBT_LUCK_KEY) * 100;
        return 0;
    }

    /**
     * Arrondit au pas de 0,25 le plus proche et borne a [MIN, MAX].
     * Une valeur non nulle ne peut jamais retomber a zero : le plus petit
     * apport reste 0,25, jamais 0.
     */
    public static int snapLuckCenti(int centi) {
        int pas = DeepLuckyBlockBlock.LUCK_STEP_CENTI;
        int arrondi = centi >= 0
                ? ((centi + pas / 2) / pas) * pas
                : -((((-centi) + pas / 2) / pas) * pas);
        if (arrondi == 0 && centi != 0) arrondi = centi > 0 ? pas : -pas;
        return Math.max(DeepLuckyBlockBlock.MIN_LUCK_CENTI,
                Math.min(DeepLuckyBlockBlock.MAX_LUCK_CENTI, arrondi));
    }

    public static void setLuckCenti(ItemStack stack, int centi) {
        if (stack == null || stack.isEmpty()) return;
        int v = snapLuckCenti(centi);
        CompoundTag tag = deepluckyblock.util.DeepNbt.getOrCreateTag(stack);
        tag.putInt(DeepLuckyBlockBlock.NBT_LUCK_CENTI_KEY, v);
        // Ancienne cle maintenue, arrondie : wands, scripts et mondes anciens
        // continuent de lire une chance coherente.
        tag.putInt(DeepLuckyBlockBlock.NBT_LUCK_KEY, centiToLuck(v));
        deepluckyblock.util.DeepNbt.setTag(stack, tag);
    }

    /** Arrondi au plus proche, symetrique pour les maledictions. */
    public static int centiToLuck(int centi) {
        return centi >= 0 ? (centi + 50) / 100 : -(((-centi) + 50) / 100);
    }

    /** « 10.75 », « 10.5 », « 10 » -- jamais « 10.00 ». */
    public static String formatLuck(int centi) {
        int abs = Math.abs(centi);
        String signe = centi < 0 ? "-" : "";
        int entier = abs / 100, reste = abs % 100;
        if (reste == 0) return signe + entier;
        if (reste % 10 == 0) return signe + entier + "." + (reste / 10);
        return signe + entier + "." + (reste < 10 ? "0" + reste : String.valueOf(reste));
    }

    public static int getLuckBonus(ItemStack stack) {
        return centiToLuck(getLuckCenti(stack));
    }

    public static void setLuckBonus(ItemStack stack, int luck) {
        setLuckCenti(stack, luck * 100);
    }

    @Override
    public Component getName(ItemStack stack) {
        int centi = getLuckCenti(stack);
        String v = formatLuck(centi);
        // T460 : le signe « % » a ete retire. Ce n'est pas un pourcentage mais
        // une valeur ajoutee ; l'afficher en % induisait en erreur.
        if (centi >= DeepLuckyBlockBlock.MAX_LUCK_CENTI) {
            return Component.literal("§6§l✦ DEEP'S LUCKY BLOCK [MAX] ✦");
        } else if (centi <= DeepLuckyBlockBlock.MIN_LUCK_CENTI) {
            return Component.literal("§4§l✦ DEEP'S LUCKY BLOCK [MAUDIT MAX] ✦");
        } else if (centi <= -5000) {
            return Component.literal("§c§lDeep's Lucky Block §4[" + v + "]");
        } else if (centi < 0) {
            return Component.literal("§6§lDeep's Lucky Block §c[" + v + "]");
        } else if (centi >= 5000) {
            return Component.literal("§6§lDeep's Lucky Block §b[+" + v + "]");
        } else if (centi > 0) {
            return Component.literal("§6§lDeep's Lucky Block §7[+" + v + "]");
        }
        return Component.literal("§6§lDeep's Lucky Block");
    }

    @Override
    public void appendHoverText(ItemStack stack, Item.TooltipContext context, List<Component> list, TooltipFlag flag) {
        super.appendHoverText(stack, context, list, flag);
    }
}