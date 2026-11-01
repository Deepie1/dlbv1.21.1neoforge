package deepluckyblock;

/**
 * T307-RARITY : Échelle complète de rareté des enchantements custom (7 paliers jusqu'à LEGENDARY),
 * alignée sur les seuils de QualityTier (TestProcedure) :
 *  - COMMON    (poids 32, luck min  0)
 *  - UNCOMMON  (poids 18, luck min 25)
 *  - RARE      (poids 10, luck min 46)
 *  - VERY_RARE (poids  5, luck min 64)
 *  - EPIC      (poids  3, luck min 78)
 *  - MYTHIC    (poids  2, luck min 87)
 *  - LEGENDARY (poids  1, luck min 91)
 *
 * NB : distinct de {@code net.minecraft.world.item.Rarity} (rareté vanilla d'item).
 */
public enum Rarity {
    COMMON(32, 0),
    UNCOMMON(18, 25),
    RARE(10, 46),
    VERY_RARE(5, 64),
    EPIC(3, 78),
    MYTHIC(2, 87),
    LEGENDARY(1, 91);

    private final int weight;
    private final int minLuck;

    Rarity(int weight, int minLuck) {
        this.weight = weight;
        this.minLuck = minLuck;
    }

    public int weight() {
        return this.weight;
    }

    public int minLuck() {
        return this.minLuck;
    }
}
