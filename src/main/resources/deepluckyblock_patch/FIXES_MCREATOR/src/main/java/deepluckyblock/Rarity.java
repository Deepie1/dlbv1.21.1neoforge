package deepluckyblock;

/**
 * Rarity des enchantements custom — PORT 1.21.1.
 *
 * En 1.20.1 c'était Enchantment.Rarity (supprimée en 1.20.5+). Le champ
 * "weight" (poids de tirage en table d'enchante vanilla) remplace la notion
 * de rareté : les mêmes valeurs que l'ancien enum (COMMON=9, UNCOMMON=5,
 * RARE=2, VERY_RARE=1). La table d'enchante vanilla est désactivée pour ce
 * mod (voir CustomEnchantment.ENABLE_VANILLA_INTEGRATION), le poids ne sert
 * qu'aux calculs de coût / affichage.
 *
 * NB : n'a RIEN à voir avec net.minecraft.world.item.Rarity (rareté d'item).
 */
public enum Rarity {
    COMMON(9),
    UNCOMMON(5),
    RARE(2),
    VERY_RARE(1);

    private final int weight;

    Rarity(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return this.weight;
    }
}
