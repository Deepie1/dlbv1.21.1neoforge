package deepluckyblock.procedures;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import deepluckyblock.DeepLuckyBlockMod;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;

import java.util.Random;

import net.neoforged.fml.common.EventBusSubscriber;

/**
 * Commande /spawnbob - Fait apparaître Bob 'The Eternal Fisher'
 * avec son équipement généré aléatoirement (Level 77)
 */
public class SpawnBobCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("spawnbob")
            .requires(source -> source.hasPermission(2))
            .executes(context -> {
                Entity entity = context.getSource().getEntity();
                if (entity == null) return 0;

                // entity.level() renvoie un Level -> cast sûr en ServerLevel
                if (!(entity.level() instanceof ServerLevel serverLevel)) return 0;

                spawnBob(serverLevel, entity.getX(), entity.getY(), entity.getZ(), 77);
                return 1;
            })
        );
    }

    /**
     * Spawn Bob avec le même équipement que GenerateBadProcedure.event_BobTheEternalFisher()
     */
    public static void spawnBob(ServerLevel level, double x, double y, double z, int luckLevel) {
        Zombie bob = new Zombie(net.minecraft.world.entity.EntityType.ZOMBIE, level);
        bob.moveTo(x + 0.5, y + 1.0, z + 0.5);
        bob.setCustomName(Component.literal("§6§lBob 'The Eternal Fisher'"));
        bob.setCustomNameVisible(true);
        bob.setPersistenceRequired();

        if (bob.getAttribute(Attributes.MAX_HEALTH) != null) {
            bob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(70.0);
        }
        bob.setHealth(70.0f);

        TestProcedure.QualityTier tier = TestProcedure.QualityTier.getQualityTier(luckLevel);
        Random random = new Random();

        // Génère l'armure et l'arme avec le même système que le Lucky Block
        bob.setItemSlot(EquipmentSlot.HEAD, GenerateLuckyItemProcedure.generateSpecific(level, luckLevel, tier, net.minecraft.util.RandomSource.create(), EquipmentSlot.HEAD));
        bob.setItemSlot(EquipmentSlot.CHEST, GenerateLuckyItemProcedure.generateSpecific(level, luckLevel, tier, net.minecraft.util.RandomSource.create(), EquipmentSlot.CHEST));
        bob.setItemSlot(EquipmentSlot.LEGS, GenerateLuckyItemProcedure.generateSpecific(level, luckLevel, tier, net.minecraft.util.RandomSource.create(), EquipmentSlot.LEGS));
        bob.setItemSlot(EquipmentSlot.FEET, GenerateLuckyItemProcedure.generateSpecific(level, luckLevel, tier, net.minecraft.util.RandomSource.create(), EquipmentSlot.FEET));
        bob.setItemSlot(EquipmentSlot.MAINHAND, GenerateLuckyItemProcedure.generateSpecific(level, luckLevel, tier, net.minecraft.util.RandomSource.create(), EquipmentSlot.MAINHAND));

        // Canne à pêche
        ItemStack rod = new ItemStack(Items.FISHING_ROD);
        rod.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal("§6§l✦ Eternal Fisher's Rod ✦"));
        deepluckyblock.util.DeepNbt.markLuckyBlockOrigin(rod);
        bob.setItemSlot(EquipmentSlot.OFFHAND, rod);

        // Tags de drop
        bob.setDropChance(EquipmentSlot.HEAD, 0.05f);
        bob.setDropChance(EquipmentSlot.CHEST, 0.05f);
        bob.setDropChance(EquipmentSlot.LEGS, 0.05f);
        bob.setDropChance(EquipmentSlot.FEET, 0.05f);
        bob.setDropChance(EquipmentSlot.MAINHAND, 0.08f);
        bob.setDropChance(EquipmentSlot.OFFHAND, 0.15f);

        // Tag CRITIQUE pour le système de pêche
        bob.getPersistentData().putBoolean("IsEternalBob", true);

        level.addFreshEntity(bob);

        // Feedback
        level.getServer().sendSystemMessage(Component.literal("§6🎣 Bob 'The Eternal Fisher' spawné avec équipement Level " + luckLevel + " !"));
    }

    // ==================== ENREGISTREMENT DE LA COMMANDE ====================
    // Sans ce bloc, /spawnbob n'existe pas en jeu ("Unknown or incomplete command").

    @EventBusSubscriber(modid = "deep_lucky_block")
    public static class Registration {

        @SubscribeEvent
        public static void onRegisterCommands(RegisterCommandsEvent event) {
            SpawnBobCommand.register(event.getDispatcher());
        }
    }
}
