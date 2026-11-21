package deepluckyblock.blockentity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import deepluckyblock.init.DeepLuckyBlockModBlockEntities;

/**
 * DeepLuckyBlockBlockEntity — Persistance NBT native de la chance du bloc.
 *
 * Version nettoyee : PLUS d'auto-enregistrement. Le BlockEntityType est fourni
 * par DeepLuckyBlockModBlockEntities (DeferredRegister canonique enregistre dans
 * le constructeur du mod). Un seul systeme d'enregistrement, zero code mort.
 *
 * Cles NBT :
 *   - "Luck"        : standard natif (Lucky World Invasion, wands, scripts).
 *   - "SlbLuckBonus": bonus / malus etendu Supreme Lucky Block.
 */
public class DeepLuckyBlockBlockEntity extends BlockEntity {

    private int luck = 0;
    private int slbLuckBonus = 0;
    /** T460 : chance fractionnaire, en centiemes (100 = +1). */
    private int luckCenti = 0;

    public DeepLuckyBlockBlockEntity(BlockPos pos, BlockState state) {
        super(DeepLuckyBlockModBlockEntities.DEEP_LUCKY_BLOCK_ENTITY.get(), pos, state);
    }

    public int getLuck() {
        return luck;
    }

    public void setLuck(int luck) {
        this.luck = luck;
        this.luckCenti = luck * 100;
        setChanged();
    }

    /** T460 : chance en centiemes ; tient aussi l'entier a jour pour les lecteurs existants. */
    public int getLuckCenti() {
        return luckCenti;
    }

    public void setLuckCenti(int centi) {
        this.luckCenti = centi;
        this.luck = centi >= 0 ? (centi + 50) / 100 : -(((-centi) + 50) / 100);
        setChanged();
    }

    public int getSlbLuckBonus() {
        return slbLuckBonus;
    }

    public void setSlbLuckBonus(int slbLuckBonus) {
        this.slbLuckBonus = slbLuckBonus;
        setChanged();
    }

    @Override
    public void saveAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Luck", this.luck);
        tag.putInt("SlbLuckBonus", this.slbLuckBonus);
        tag.putInt("LuckCenti", this.luckCenti);
    }

    // PORT 1.21.1 : BlockEntity.load supprimé -> loadAdditional(tag, provider).
    @Override
    protected void loadAdditional(CompoundTag tag, net.minecraft.core.HolderLookup.Provider registries) {
        if (tag.contains("Luck")) {
            this.luck = tag.getInt("Luck");
            // Repli : un bloc pose avant T460 n'a pas de centiemes.
            this.luckCenti = this.luck * 100;
        }
        if (tag.contains("SlbLuckBonus")) {
            this.slbLuckBonus = tag.getInt("SlbLuckBonus");
        }
        // Lu APRES "Luck" : la valeur fractionnaire fait foi quand elle existe.
        if (tag.contains("LuckCenti")) {
            this.luckCenti = tag.getInt("LuckCenti");
        }
    }
}
