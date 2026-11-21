package deepluckyblock.mixin;

import org.spongepowered.asm.mixin.Mixins;
import org.spongepowered.asm.mixin.connect.IMixinConnector;

/**
 * Charge la configuration avant le chargement des classes clientes de Lucky XP.
 * L'attribut MixinConnector du manifeste est lu par Mixin pendant son bootstrap,
 * bien avant les constructeurs Forge des mods.
 */
@SuppressWarnings("deprecation")
public final class DeepMixinConnector implements IMixinConnector {

    @Override
    public void connect() {
        Mixins.addConfiguration("deep_lucky_block.mixins.json");
        // T456 : deja muet pour la publication. System.out est volontaire ici (le
        // connecteur tourne avant tout logger du mod) et la garde l'est aussi :
        // la propriete systeme, pas DebugLog, qui n'est pas encore chargeable.
        if (Boolean.getBoolean("dlb.debug")) {
            System.out.println("[DeepLuckyBlock/Compat] EARLY mixin connector loaded");
        }
    }
}
