package deepluckyblock.procedures;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * ═══════════════════════════════════════════════════════════════
 * 🌀 FirarainProcedure — Interface de sélection de téléportation
 * ═══════════════════════════════════════════════════════════════
 *
 * ⚙️ FONCTIONNEMENT :
 * • Ouvre un coffre virtuel de 6 lignes (54 slots) côté serveur.
 * • Affiche la tête (PLAYER_HEAD avec skin et NBT) de chaque joueur en ligne.
 * • Place un bloc Barrière dans le slot 53 (en bas à droite) pour annuler.
 * • SÉCURITÉ INTÉGRALE :
 *   - Aucun item (têtes, barrière, etc.) ne peut être pris, déplacé ou dupliqué.
 *   - Les clics et Shift-clics dans l'interface sont interceptés et bloqués.
 *   - Si le coffre est fermé sans téléportation (Bouton Barrière ou touche ESC),
 *     1 exemplaire de "deep_lucky_block:firarain_potion" est remboursé
 *     ET 1 bouteille vide (GLASS_BOTTLE) est retirée de l'inventaire du joueur
 *     (sans aucun message dans le tchat).
 *   - Si une téléportation réussit, le coffre se ferme et la potion est bien consommée.
 *   - Le message "Téléportation à X" ne s'affiche qu'une seule fois au maximum
 *     toutes les 10 secondes dans le tchat.
 */
public class FirarainProcedure {

    private static long lastTeleportMessageTimeMs = 0L;

    public static void execute(LevelAccessor world, double x, double y, double z, Entity entity) {
        if (world.isClientSide() || !(entity instanceof ServerPlayer serverPlayer))
            return;

        ServerLevel serverLevel = (ServerLevel) world;
        SimpleContainer container = new SimpleContainer(54);

        int slotIndex = 0;
        for (ServerPlayer p : serverLevel.players()) {
            if (p.getUUID().equals(serverPlayer.getUUID()))
                continue; // On s'exclut soi-même de la sélection
            if (slotIndex >= 53)
                break; // Garder le dernier slot (53) pour le bloc Barrière

            ItemStack head = new ItemStack(Items.PLAYER_HEAD);
            CompoundTag tag = deepluckyblock.util.DeepNbt.getOrCreateTag(head);
            tag.putString("SkullOwner", p.getGameProfile().getName());
            tag.putString("TargetPlayerUUID", p.getUUID().toString());
            tag.putString("TargetPlayerName", p.getGameProfile().getName());
            deepluckyblock.util.DeepNbt.setTag(head, tag);

            head.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§e§l✦ Téléportation : §a§l" + p.getGameProfile().getName() + " §e§l✦"));
            addLore(head, "§7Cliquez pour vous téléporter", "§7directement sur ce joueur.");

            container.setItem(slotIndex++, head);
        }

        if (slotIndex == 0) {
            ItemStack emptyInfo = new ItemStack(Items.PAPER);
            emptyInfo.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§7§oAucun autre joueur en ligne..."));
            addLore(emptyInfo, "§cVous êtes seul sur le serveur.");
            container.setItem(22, emptyInfo);
        }

        // Slot 53 : Bloc Barrière pour fermer et récupérer la potion
        ItemStack barrier = new ItemStack(Items.BARRIER);
        barrier.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§c§l✦ Fermer & Récupérer la Potion ✦"));
        addLore(barrier, "§7Cliquez ici pour annuler", "§7et récupérer votre Firarain Potion.");
        container.setItem(53, barrier);

        serverPlayer.openMenu(new MenuProvider() {
            @Override
            public Component getDisplayName() {
                return Component.literal("§5§l✦ Téléportation Firarain ✦");
            }

            @Override
            public AbstractContainerMenu createMenu(int id, Inventory playerInventory, Player player) {
                return new FirarainTeleportMenu(id, playerInventory, container);
            }
        });
    }

    private static void addLore(ItemStack stack, String... lines) {
        try {
            // PORT 1.21.1 : le lore est le composant ItemLore (plus de NBT "display")
            List<Component> loreList = new ArrayList<>();
            for (String line : lines) {
                loreList.add(Component.literal(line));
            }
            stack.set(net.minecraft.core.component.DataComponents.LORE,
                    new net.minecraft.world.item.component.ItemLore(loreList));
        } catch (Exception ignored) {}
    }

    /**
     * Menu sécurisé empêchant le pillage ou le dépôt d'items.
     */
    public static class FirarainTeleportMenu extends ChestMenu {
        private boolean teleported = false;
        private final Container chestContainer;

        public FirarainTeleportMenu(int containerId, Inventory playerInventory, Container container) {
            super(MenuType.GENERIC_9x6, containerId, playerInventory, container, 6);
            this.chestContainer = container;
        }

        @Override
        public void clicked(int slotId, int button, ClickType clickType, Player player) {
            // S'assurer de n'exécuter la logique que côté serveur pour éviter les doubles clics/messages
            if (player.level().isClientSide()) {
                return;
            }

            // 1. Si le clic a lieu dans les 54 slots du coffre
            if (slotId >= 0 && slotId < this.chestContainer.getContainerSize()) {
                ItemStack clickedStack = this.chestContainer.getItem(slotId);

                if (!clickedStack.isEmpty()) {
                    // Clic sur la barrière : fermer l'interface (ce qui déclenche le remboursement silencieux)
                    if (clickedStack.is(Items.BARRIER)) {
                        player.closeContainer();
                        return;
                    }

                    // Clic sur une tête de joueur : téléportation immédiate
                    if (clickedStack.is(Items.PLAYER_HEAD)) {
                        CompoundTag tag = deepluckyblock.util.DeepNbt.tag(clickedStack);
                        if (tag != null && tag.contains("TargetPlayerUUID")) {
                            String targetUuidStr = tag.getString("TargetPlayerUUID");
                            if (player.level() instanceof ServerLevel sl && player instanceof ServerPlayer spPlayer) {
                                ServerPlayer targetPlayer = null;
                                for (ServerPlayer sp : sl.players()) {
                                    if (sp.getUUID().toString().equals(targetUuidStr)) {
                                        targetPlayer = sp;
                                        break;
                                    }
                                }

                                if (targetPlayer != null && targetPlayer != spPlayer) {
                                    this.teleported = true;
                                    spPlayer.teleportTo(targetPlayer.serverLevel(),
                                            targetPlayer.getX(), targetPlayer.getY(), targetPlayer.getZ(),
                                            targetPlayer.getYRot(), targetPlayer.getXRot());
                                    sl.playSound(null, spPlayer.getX(), spPlayer.getY(), spPlayer.getZ(),
                                            SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 1.0f, 1.0f);

                                    long now = System.currentTimeMillis();
                                    if (now - lastTeleportMessageTimeMs >= 10000L) {
                                        lastTeleportMessageTimeMs = now;
                                        spPlayer.sendSystemMessage(Component.literal("§aTéléportation à "
                                                + targetPlayer.getGameProfile().getName()));
                                    }

                                    player.closeContainer();
                                    return;
                                } else {
                                    spPlayer.sendSystemMessage(Component.literal("§cCe joueur n'est plus disponible."));
                                }
                            }
                        }
                    }
                }

                // SÉCURITÉ : on retourne immédiatement sans appeler super.clicked(...) -> l'item ne bouge pas
                return;
            }

            // 2. Si le clic a lieu dans l'inventaire du joueur : interdire tout Shift-clic (QUICK_MOVE)
            if (clickType == ClickType.QUICK_MOVE || clickType == ClickType.SWAP) {
                return;
            }

            // Autoriser le joueur à bouger les items normaux dans son inventaire personnel
            super.clicked(slotId, button, clickType, player);
        }

        @Override
        public ItemStack quickMoveStack(Player player, int index) {
            // Empêche le transfert automatique (Shift-clic) d'items dans ou hors du coffre
            return ItemStack.EMPTY;
        }

        @Override
        public boolean stillValid(Player player) {
            return true;
        }

        @Override
        public void removed(Player player) {
            super.removed(player);
            // Si le menu se ferme SANS téléportation (touche ESC ou clic sur la Barrière), on rembourse 1 potion
            if (!this.teleported && player instanceof ServerPlayer serverPlayer) {
                refundPotion(serverPlayer);
            }
        }

        private static void refundPotion(ServerPlayer player) {
            ItemStack refund = new ItemStack(Items.AIR);
            try {
                // 1) Lookup direct via le modid officiel : deep_lucky_block
                ResourceLocation rl = ResourceLocation.tryParse("deep_lucky_block:firarain_potion");
                Item potionItem = (rl != null) ? BuiltInRegistries.ITEM.get(rl) : null;

                // 2) Lookup alternatif via l'ancienne variante : deepluckyblock
                if (potionItem == null || potionItem == Items.AIR) {
                    rl = ResourceLocation.tryParse("deep_lucky_block:firarain_potion");
                    potionItem = (rl != null) ? BuiltInRegistries.ITEM.get(rl) : null;
                }

                // 3) Secours dynamique dans le registre : recherche par le path exact "firarain_potion"
                if (potionItem == null || potionItem == Items.AIR) {
                    for (Item it : BuiltInRegistries.ITEM) {
                        ResourceLocation key = BuiltInRegistries.ITEM.getKey(it);
                        if (key != null && key.getPath().equals("firarain_potion")) {
                            potionItem = it;
                            break;
                        }
                    }
                }

                if (potionItem != null && potionItem != Items.AIR) {
                    refund = new ItemStack(potionItem, 1);
                }
            } catch (Exception ignored) {}

            if (!refund.isEmpty() && refund.getItem() != Items.AIR) {
                // Retirer 1 bouteille en verre (GLASS_BOTTLE) issue de la consommation de la potion
                removeOneGlassBottle(player);

                if (!player.getInventory().add(refund)) {
                    player.drop(refund, false);
                }
            }
        }

        private static void removeOneGlassBottle(ServerPlayer player) {
            // Parcourt tous les slots de l'inventaire pour retirer 1 seule fiole vide
            for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (!stack.isEmpty() && stack.is(Items.GLASS_BOTTLE)) {
                    stack.shrink(1);
                    break;
                }
            }
        }
    }
}