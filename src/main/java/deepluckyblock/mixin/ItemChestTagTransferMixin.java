package deepluckyblock.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FIX v7 — COFFRES LUCKY (onglet créatif).
 *
 * Le tag du coffre dans l'INVENTAIRE (drapeaux slb_auto_chest / slb_chest_rank
 * écrits par CustomCreativeTabs.makeLuckyChest) doit rejoindre le BlockEntity
 * une fois le coffre POSÉ. Deux problemes 1.21.1/NeoForge :
 *
 *  1) En 1.20.1 la copie item->BE lisait la "tag" legacy (composant CUSTOM_DATA,
 *     sous-cle "BlockEntityTag"). En 1.21.1 le mecanisme vanilla est le composant
 *     DataComponents.BLOCK_ENTITY_DATA (type CustomData), applique via
 *     BlockEntityData.applyTo (be.save -> merge -> be.load -> setChanged).
 *
 *  2) NeoForge stocke getPersistentData() sous la cle "NeoForgeData" (et non
 *     "ForgeData" comme l'ancien Forge) : un tag place sous "ForgeData" est
 *     invisible pour getPersistentData() -> LuckyChestAutofillProcedure ne
 *     trouvait jamais le drapeau -> coffre jamais rempli.
 *
 * Cet inject TAIL sur Item.useOn(UseOnContext) est le filet de securite : apres
 * la pose, si le BE coffre n'a toujours pas "slb_auto_chest", on le copie depuis
 * le stack utilise (composant BLOCK_ENTITY_DATA ou legacy CUSTOM_DATA/
 * BlockEntityTag, cles NeoForgeData OU ForgeData) vers getPersistentData().
 * Si le mecanisme vanilla a deja fait le travail (BLOCK_ENTITY_DATA bien posé
 * par makeLuckyChest), le getBoolean("slb_auto_chest") est vrai et on sort.
 */
@Mixin(Item.class)
public abstract class ItemChestTagTransferMixin {

    @Inject(method = "useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;",
            at = @At("TAIL"))
    private void slb_transferChestFlags(UseOnContext ctx, CallbackInfoReturnable<InteractionResult> cir) {
        try {
            Level level = ctx.getLevel();
            if (level.isClientSide()) return;

            BlockPos pos = ctx.getClickedPos().relative(ctx.getClickedFace());
            BlockEntity be = level.getBlockEntity(pos);
            if (!(be instanceof ChestBlockEntity)) return;

            CompoundTag data = be.getPersistentData();
            if (data.getBoolean("slb_auto_chest")) return; // deja present (mechanisme vanilla ou autre)

            ItemStack stack = ctx.getItemInHand();
            CompoundTag flags = slbFlagsFrom(stack);
            if (flags == null) return;

            for (String key : flags.getAllKeys()) {
                data.put(key, flags.get(key));
            }
            be.setChanged();
            System.out.println("[SLB-CHEST] drapeaux transfere vers " + pos
                    + " : slb_chest_rank=" + data.getInt("slb_chest_rank"));
        } catch (Throwable t) {
            System.out.println("[SLB-CHEST] erreur de transfert de drapeaux : " + t);
        }
    }

    /**
     * Renvoie le compound de drapeaux slb_* porte par le stack (composant
     * BLOCK_ENTITY_DATA d'abord, puis legacy CUSTOM_DATA/BlockEntityTag),
     * sous-cle NeoForgeData (NeoForge) ou ForgeData (anciens tags), ou null.
     */
    private static CompoundTag slbFlagsFrom(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;

        CustomData bed = stack.get(DataComponents.BLOCK_ENTITY_DATA);
        if (bed != null && !bed.isEmpty()) {
            CompoundTag t = slbSub(bed.getUnsafe());
            if (t != null) return t;
        }

        CustomData cd = stack.get(DataComponents.CUSTOM_DATA);
        if (cd != null && !cd.isEmpty()) {
            CompoundTag root = cd.getUnsafe();
            if (root.contains("BlockEntityTag", Tag.TAG_COMPOUND)) {
                CompoundTag t = slbSub(root.getCompound("BlockEntityTag"));
                if (t != null) return t;
            }
        }
        return null;
    }

    private static CompoundTag slbSub(CompoundTag beTag) {
        if (beTag == null) return null;
        if (beTag.contains("NeoForgeData", Tag.TAG_COMPOUND)) {
            return beTag.getCompound("NeoForgeData");
        }
        if (beTag.contains("ForgeData", Tag.TAG_COMPOUND)) {
            return beTag.getCompound("ForgeData");
        }
        return null;
    }
}
