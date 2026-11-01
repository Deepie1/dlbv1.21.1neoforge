package deepluckyblock.enchantments;

import deepluckyblock.procedures.TestProcedure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.common.Tags;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.entity.player.ArrowLooseEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;

import org.joml.Vector3f;

import java.util.*;

import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/** Runtime behavior for the 49 equipment expansion enchantments. */
@EventBusSubscriber(modid = "deep_lucky_block")
public final class EquipmentExpansionHandler {
    private EquipmentExpansionHandler() {}
    private static final ThreadLocal<Boolean> DAMAGE_GUARD=ThreadLocal.withInitial(()->false);
    private static final ThreadLocal<Boolean> MINING_GUARD=ThreadLocal.withInitial(()->false);
    // PORT 1.21.1 : les identifiants d'AttributeModifier sont des ResourceLocation
    // (plus de UUID) ; le nom humain passe dans la ResourceLocation.
    // FIX DOUBLE-COMPTAGE (ask #5) : SUREFOOT_ID (ancien identifiant du
    // modificateur de Knockback Resistance pose sur le JOUEUR a chaque tick)
    // a ete retire -- ce bonus fixe et inconditionnel est desormais un vrai
    // AttributeModifier pose sur l'ITEM par RealAttributeModifierHandler
    // (voir #applyArmorBonuses), visible nativement dans "When on Body".
    private static final ResourceLocation PIVOT_ID=ResourceLocation.parse("deep_lucky_block:modifier_pivot"), HUNTER_ID=ResourceLocation.parse("deep_lucky_block:modifier_hunter"), CARRIER_ID=ResourceLocation.parse("deep_lucky_block:modifier_carrier"), TIDAL_ID=ResourceLocation.parse("deep_lucky_block:modifier_tidal"), WIND_ID=ResourceLocation.parse("deep_lucky_block:modifier_wind"), QUICKSAND_ID=ResourceLocation.parse("deep_lucky_block:modifier_quicksand"), KINETIC_ID=ResourceLocation.parse("deep_lucky_block:modifier_kinetic"), DIMENSION_ID=ResourceLocation.parse("deep_lucky_block:modifier_dimension");
    // FIX rapport en jeu ("l'enchantement Extension qui augmente la reach ne
    // fonctionne pas") : le code precedent n'ajoutait QUE +1.2 degats/niveau
    // (voir UniversalEnchantmentHandler#handleOffensiveHurt) sans jamais
    // toucher les attributs de portee reels du joueur. Extension doit aussi
    // augmenter Attributes.BLOCK_INTERACTION_RANGE et ENTITY_INTERACTION_RANGE
    // pour que la "reach" annoncee soit reellement ressentie en jeu.
    private static final ResourceLocation EXT_BLOCK_REACH_ID=ResourceLocation.parse("deep_lucky_block:modifier_extension_block_reach"), EXT_ENTITY_REACH_ID=ResourceLocation.parse("deep_lucky_block:modifier_extension_entity_reach");
    private static final Map<UUID, OreGuidanceState> ORE_GUIDANCE = new HashMap<>();
    private static final Map<UUID, ArrayDeque<net.minecraft.world.phys.Vec3>> TRACER_HISTORY = new HashMap<>();
    private static final Map<UUID, Integer> TRACER_END = new HashMap<>();
    private static final Map<UUID, Integer> TRACER_LEVEL = new HashMap<>();
    private static final Map<UUID, UUID> TRACER_VIEWER = new HashMap<>();

    private static final class OreGuidanceState {
        int level; long nextScan; String dimension=""; final List<OreTarget> targets=new ArrayList<>();
    }
    private static final class OreTarget {
        final String family; final Vector3f color; final List<BlockPos> blocks;
        OreTarget(String family,Vector3f color,List<BlockPos> blocks){this.family=family;this.color=color;this.blocks=blocks;}
        boolean refresh(ServerLevel level){blocks.removeIf(p->!isOreLike(level.getBlockState(p))||!oreFamily(level.getBlockState(p)).equals(family));return !blocks.isEmpty();}
        net.minecraft.world.phys.Vec3 center(){double x=0,y=0,z=0;for(BlockPos p:blocks){x+=p.getX()+.5;y+=p.getY()+.5;z+=p.getZ()+.5;}int n=Math.max(1,blocks.size());return new net.minecraft.world.phys.Vec3(x/n,y/n,z/n);}
    }

    // FIX (mob-equipment-enchant generalization) : ce handler tournait
    // auparavant sur PlayerTickEvent.Post, donc AUCUN de ces passifs
    // d'armure/enchantement (vitesse, knockback, coeurs elementaires...) ne
    // s'appliquait a un MOB portant la meme piece enchantee (ex: squelette
    // avec des bottes enchantees de Windrunner, zombie avec un plastron a
    // coeur elemental). EntityTickEvent.Post est declenche pour TOUTE
    // entite (voir EventHooks.fireEntityTickPost, appele depuis la fin de
    // LivingEntity#tick pour n'importe quelle LivingEntity, pas seulement
    // Player) -- on filtre nous-memes sur LivingEntity ci-dessous, ce qui
    // generalise tous les effets ne dependant QUE de LivingEntity (armure,
    // vitesse, coeurs...) aux mobs, tout en gardant les volets
    // intrinsequement lies au joueur (inventaire, Miner's Eye, faim...)
    // derriere des verifications explicites "instanceof Player" plus bas.
    @SubscribeEvent public static void entityTick(EntityTickEvent.Post e){try{entityTickImpl(e);}catch(Throwable t__){guard("entityTick",t__);}} private static void entityTickImpl(EntityTickEvent.Post e){
        if(!(e.getEntity() instanceof LivingEntity p))return;
        if(p.level().isClientSide())return; long now=p.level().getGameTime();
        if(p instanceof ServerPlayer sp&&p.level() instanceof ServerLevel sl){int guidance=armor(p,"miners_guidance");if(guidance>0&&p.tickCount%5==0){try{updateOreGuidance(sp,sl,guidance);}catch(Throwable failure){ORE_GUIDANCE.remove(p.getUUID());System.err.println("[DeepLuckyBlock] Miner's Eye disabled for this scan after an error: "+failure);}}else if(guidance<=0)ORE_GUIDANCE.remove(p.getUUID());}
        int clarity=armor(p,"mental_clarity"); if(clarity>0&&p.tickCount%20==0){ shorten(p,MobEffects.CONFUSION,clarity);shorten(p,MobEffects.BLINDNESS,clarity);shorten(p,MobEffects.DARKNESS,clarity); }
        int echo=armor(p,"echolocation"); if(echo>0&&p.tickCount%40==0&&p.level().getMaxLocalRawBrightness(p.blockPosition())<8){double er=deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.radius(10,6,echo);if(p.level() instanceof ServerLevel sl)EnchantmentParticleFX.shockwaveRing(sl,p.getX(),p.getY()+.2,p.getZ(),ParticleTypes.SCULK_CHARGE_POP,1.4,12,.03);for(Mob m:p.level().getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(er)))m.addEffect(new MobEffectInstance(MobEffects.GLOWING,35,0,false,false));}
        int conc=armor(p,"concentration"); double hs=p.getDeltaMovement().horizontalDistance(); int focus=p.getPersistentData().getInt("slb_focus"); focus=conc>0&&hs<.01?Math.min(100,focus+1):Math.max(0,focus-4);p.getPersistentData().putInt("slb_focus",focus);
        int whispers=armor(p,"curse_of_whispers"); if(whispers>0){long next=p.getPersistentData().getLong("slb_whisper_next");if(next<=0)p.getPersistentData().putLong("slb_whisper_next",now+200+p.getRandom().nextInt(2801));else if(now>=next){playFakePack(p);EnchantmentParticleFX.ankleTrail(p,ParticleTypes.SCULK_SOUL,4,.02);p.getPersistentData().putLong("slb_whisper_next",now+200+p.getRandom().nextInt(2801));}}
        // Phantom Step: after 4 seconds without attacking, sneaking clears nearby mob targets and hides the player.
        int phantom=armor(p,"phantom_step"); long lastAttack=p.getPersistentData().getLong("slb_last_attack");if(phantom>0&&p.isShiftKeyDown()&&now-lastAttack>=80){p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY,8,0,true,false));if(p.tickCount%12==0)EnchantmentParticleFX.ankleTrail(p,ParticleTypes.SMOKE,2,.01);for(Mob m:p.level().getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.radius(4,3,phantom))))if(m.getTarget()==p)m.setTarget(null);}
        // Pivot detects a sharp horizontal direction change.
        int pivot=armor(p,"pivot");double vx=p.getDeltaMovement().x,vz=p.getDeltaMovement().z,oldx=p.getPersistentData().getDouble("slb_old_vx"),oldz=p.getPersistentData().getDouble("slb_old_vz");double dot=vx*oldx+vz*oldz;setSpeed(p,PIVOT_ID,pivot>0&&dot<-.005?.06*pivot:0);p.getPersistentData().putDouble("slb_old_vx",vx);p.getPersistentData().putDouble("slb_old_vz",vz);
        int anchor=armor(p,"anchoring");if(anchor>0&&p.isShiftKeyDown()){p.setDeltaMovement(p.getDeltaMovement().multiply(Math.max(.35,1-.18*anchor),1,Math.max(.35,1-.18*anchor)));setKnockback(p,.25*anchor);}else setKnockback(p,0);
        int hunter=armor(p,"hunters_pace");boolean chase=false;if(hunter>0)for(Mob m:p.level().getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(12))){if(m.getHealth()<m.getMaxHealth()*.5&&p.getLookAngle().dot(m.position().subtract(p.position()).normalize())>.25){chase=true;break;}}setSpeed(p,HUNTER_ID,chase?.06*hunter:0);
        int carrier=armor(p,"carriers_stride");setSpeed(p,CARRIER_ID,carrier>0&&p.isUsingItem()?.045*carrier:0);
        int tidal=armor(p,"tidal_step");boolean shallow=tidal>0&&p.isInWater()&&p.level().getBlockState(p.blockPosition().below()).blocksMotion();setSpeed(p,TIDAL_ID,shallow?.07*tidal:0);
        int sure=armor(p,"surefooted");if(sure>0){p.fallDistance=Math.max(0,p.fallDistance-.3f*sure);}
        int dim=armor(p,"dimensional_step");setSpeed(p,DIMENSION_ID,dim>0&&p.level().dimension()==Level.OVERWORLD?.04*dim:0);if(dim>0){if(p.level().dimension()==Level.NETHER)p.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE,30,0,true,false));else if(p.level().dimension()==Level.END&&p.getDeltaMovement().y<0)p.setDeltaMovement(p.getDeltaMovement().multiply(1,.86,1));}
        int kineticLevel=p.getPersistentData().getInt("slb_kinetic_level");setSpeed(p,KINETIC_ID,now<p.getPersistentData().getLong("slb_kinetic_until")?.05*kineticLevel:0);
        int quick=armor(p,"quicksand_curse");BlockState under=p.level().getBlockState(p.blockPosition().below());boolean soft=under.is(Blocks.SAND)||under.is(Blocks.RED_SAND)||under.is(Blocks.GRAVEL)||under.is(Blocks.DIRT)||under.is(Blocks.MUD);setSpeed(p,QUICKSAND_ID,quick>0&&soft?-.10*quick:0);
        // T306-FIX : retrait du double-comptage de Windrunner (géré en source unique par UniversalEnchantmentHandler).
        setSpeed(p,WIND_ID,0);
        // Frost/ember trails are passive and temporary.
        if(p.tickCount%5==0&&p.onGround()){int frost=armor(p,"frost_trail");if(frost>0){trail(p,true,frost);if(p.tickCount%15==0)EnchantmentParticleFX.ankleTrail(p,ParticleTypes.SNOWFLAKE,2,.01);}int ember=armor(p,"ember_step");if(ember>0&&p.isSprinting()){trail(p,false,ember);if(p.tickCount%10==0)EnchantmentParticleFX.ankleTrail(p,ParticleTypes.SMALL_FLAME,2,.01);}}
        // PORT 1.21.1 : LivingJumpEvent supprimé -> polling onGround() (brittle_knees)
        boolean wasG=p.getPersistentData().getBoolean("slb_was_on_ground");
        p.getPersistentData().putBoolean("slb_was_on_ground",p.onGround());
        if(!p.onGround()&&wasG&&p.getDeltaMovement().y>0){int bk=armor(p,"brittle_knees");if(bk>0){p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 40+20*bk-1));ItemStack bkLegs=p.getItemBySlot(EquipmentSlot.LEGS);bkLegs.hurtAndBreak(bk,p,EquipmentSlot.LEGS);}
            // CHIMERA'S WHIM (multi-element, bottes, FUN) : a chaque saut,
            // effet elementaire aleatoire parmi les themes des autres cœurs --
            // imprevisible, capstone "farceur" (reutilise le meme polling
            // onGround() que Brittle Knees car LivingJumpEvent n'existe pas
            // dans ce port 1.21.1).
            int chimera=armor(p,"chimeras_whim");
            if(chimera>0 && !p.level().isClientSide()) triggerChimeraWhim(p);
        }
        if(p.tickCount%10==0){ updateElementalHearts(p); updateExtensionReach(p); updateHeartEnchantmentPassives(p); }
        if(p.tickCount%20==0) tickHeartEnchantmentPeriodics(p);
    }

    // T306-OVERHAUL : Extension est désormais un vrai AttributeModifier d'ITEM dans
    // RealAttributeModifierHandler ; ici, EXT_ENTITY_REACH_ID sert à appliquer l'allonge
    // à mains nues de Gantelet Allonge (straight_reach_gauntlet, +0.75 bloc/niv main vide).
    private static void updateExtensionReach(LivingEntity p){
        int unarmedReachLvl = p.getMainHandItem().isEmpty() ? armor(p, "straight_reach_gauntlet") : 0;
        AttributeInstance blockReach = p.getAttribute(Attributes.BLOCK_INTERACTION_RANGE);
        AttributeInstance entityReach = p.getAttribute(Attributes.ENTITY_INTERACTION_RANGE);
        if (blockReach != null) {
            blockReach.removeModifier(EXT_BLOCK_REACH_ID);
            if (unarmedReachLvl > 0) blockReach.addTransientModifier(new AttributeModifier(EXT_BLOCK_REACH_ID, 0.5 * unarmedReachLvl, AttributeModifier.Operation.ADD_VALUE));
        }
        if (entityReach != null) {
            entityReach.removeModifier(EXT_ENTITY_REACH_ID);
            if (unarmedReachLvl > 0) entityReach.addTransientModifier(new AttributeModifier(EXT_ENTITY_REACH_ID, 0.75 * unarmedReachLvl, AttributeModifier.Operation.ADD_VALUE));
        }
    }

    @SubscribeEvent public static void livingHurt(LivingIncomingDamageEvent e){try{livingHurtImpl(e);}catch(Throwable t__){guard("livingHurt",t__);}} private static void livingHurtImpl(LivingIncomingDamageEvent e){ if(DAMAGE_GUARD.get())return;
        LivingEntity target=e.getEntity(); long now=target.level().getGameTime();
        // FIX (mob-equipment-enchant generalization) : "instanceof Player" ->
        // "instanceof LivingEntity" -- un mob equipe d'une armure enchantee
        // (Foresight, Adaptive Shell, Bastion Heart, Ember Core, coeurs
        // elementaires, etc.) beneficie desormais des memes effets de
        // reduction/riposte qu'un joueur portant la meme piece.
        if(target instanceof LivingEntity p){
            long previousHit=p.getPersistentData().getLong("slb_last_hurt");p.getPersistentData().putLong("slb_last_hurt",now);if(e.getSource().getEntity()!=null){p.getPersistentData().putUUID("slb_retaliate",e.getSource().getEntity().getUUID());p.getPersistentData().putLong("slb_retaliate_until",now+100);}
            int foresight=armor(p,"foresight");if(foresight>0&&now-previousHit>=200){e.setAmount(e.getAmount()*Math.max(.2f,1f-.2f*foresight));}
            int adaptive=armor(p,"adaptive_shell");if(adaptive>0){String type=e.getSource().getMsgId(),last=p.getPersistentData().getString("slb_adapt_type");int stacks=type.equals(last)?Math.min(3,p.getPersistentData().getInt("slb_adapt_stacks")+1):1;p.getPersistentData().putString("slb_adapt_type",type);p.getPersistentData().putInt("slb_adapt_stacks",stacks);e.setAmount(e.getAmount()*(1f-Math.min(.45f,.05f*adaptive*stacks)));}
            int bastion=armor(p,"bastion_heart");if(bastion>0&&nearWall(p))e.setAmount(e.getAmount()*(1f-.08f*bastion));
            int kinetic=armor(p,"kinetic_conversion");if(kinetic>0){p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION,60,Math.min(1,kinetic-1),true,false));p.getPersistentData().putInt("slb_kinetic_level",kinetic);p.getPersistentData().putLong("slb_kinetic_until",now+40);}
            int glass=armor(p,"glass_bones_champion_curse");if(glass>0)e.setAmount(e.getAmount()*(1f+.25f*glass));
            int resonance=shield(p,"resonance_curse");int vulnerable=p.getPersistentData().getInt("slb_resonance_vulnerable");if(vulnerable>0){e.setAmount(e.getAmount()*(1f+.15f*vulnerable));p.getPersistentData().remove("slb_resonance_vulnerable");}
            if(e.getSource().getMsgId().equals("fall")){int roll=armor(p,"combat_roll");long ready=p.getPersistentData().getLong("slb_roll_ready");if(roll>0&&now>=ready&&p.getDeltaMovement().horizontalDistance()>.03){e.setAmount(e.getAmount()*Math.max(.1f,1f-.25f*roll));p.setDeltaMovement(p.getLookAngle().scale(.25+.12*roll).add(0,.18,0));p.getPersistentData().putLong("slb_roll_ready",now+Math.max(30,70-10L*roll));if(p.level() instanceof ServerLevel sl)EnchantmentParticleFX.shockwaveRing(sl,p.getX(),p.getY()+.1,p.getZ(),ParticleTypes.CLOUD,1.1,12,.06);}int elastic=armor(p,"elastic_soles");if(elastic>0){e.setAmount(e.getAmount()*Math.max(0f,1f-.25f*elastic));if(!p.isShiftKeyDown())p.setDeltaMovement(p.getDeltaMovement().x,.3+.15*elastic,p.getDeltaMovement().z);} }
            // EMBER CORE (Fire Heart, casque) : quand touche par une entite
            // vivante en melee proche, chance d'enflammer l'agresseur --
            // "noyau ardent" d'auto-defense, positif/agressif sans malus.
            int emberCore = lvl(p.getItemBySlot(EquipmentSlot.HEAD), "ember_core");
            if (emberCore > 0 && e.getSource().getEntity() instanceof LivingEntity attackerEntity
                    && p.getRandom().nextInt(100) < (15 + 10 * emberCore)) {
                attackerEntity.setRemainingFireTicks(Math.max(attackerEntity.getRemainingFireTicks(), 40 + 20 * emberCore));
                if (p.level() instanceof ServerLevel emberLevel) {
                    emberLevel.sendParticles(ParticleTypes.FLAME,
                            attackerEntity.getX(), attackerEntity.getY() + attackerEntity.getBbHeight() * 0.5, attackerEntity.getZ(),
                            12, 0.25, 0.35, 0.25, 0.02);
                }
            }

            // ==================== ASK #9 : CONVERSION DEGATS -> PERTE DE COEUR ====================
            // Chaque coeur elementaire vaut 2 PV de vie MAX bonus. Pour
            // rester coherent avec "perdre de la vie retire un coeur",
            // on accumule les degats subis sur le plastron (accumulateur
            // partage, resistant aux petits coups repetes) et, des que
            // 2 PV cumules sont atteints, on retire UN coeur au DERNIER
            // element actif dans l'ordre de la barre (le plus recemment
            // ajoute est le premier a "se casser"), avec un effet visuel
            // dedie (voir spawnHeartFx, ask #10).
            // isCreative()/isSpectator() n'existent que sur Player ; un mob
            // generique n'a jamais ces modes, la condition equivaut donc a
            // "true" pour lui (on garde le check Player-only par simplicite).
            if (e.getAmount() > 0 && !(p instanceof Player creativeCheck && (creativeCheck.isCreative() || creativeCheck.isSpectator()))) {
                ItemStack chestForLoss = p.getItemBySlot(EquipmentSlot.CHEST);
                if (!chestForLoss.isEmpty()) {
                    float acc = deepluckyblock.util.ElementalHeartLoss.getDamageAcc(chestForLoss) + e.getAmount();
                    while (acc >= 2.0f) {
                        String brokenElement = breakOneElementalHeart(p, chestForLoss);
                        if (brokenElement == null) break; // plus aucun coeur bonus actif a casser
                        acc -= 2.0f;
                    }
                    deepluckyblock.util.ElementalHeartLoss.setDamageAcc(chestForLoss, acc);
                }
            }
        }
        if(e.getSource().getEntity() instanceof Player attacker){ItemStack weapon=attacker.getMainHandItem();attacker.getPersistentData().putLong("slb_last_attack",now);int glass=armor(attacker,"glass_bones_champion_curse");if(glass>0)e.setAmount(e.getAmount()*(1f+.25f*glass));int pred=lvl(weapon,"predator");if(pred>0&&target.level().getEntitiesOfClass(LivingEntity.class,target.getBoundingBox().inflate(6),x->x!=target&&x!=attacker&&x.isAlive()).isEmpty())e.setAmount(e.getAmount()*(1f+.2f*pred));int ret=lvl(weapon,"retaliation");if(ret>0&&now<=attacker.getPersistentData().getLong("slb_retaliate_until")&&attacker.getPersistentData().hasUUID("slb_retaliate")&&attacker.getPersistentData().getUUID("slb_retaliate").equals(target.getUUID())){e.setAmount(e.getAmount()*(1f+.2f*ret));attacker.getPersistentData().remove("slb_retaliate");}int mark=lvl(weapon,"marked_prey");if(mark>0){String key="slb_mark_"+attacker.getUUID(),expiry=key+"_until";int st=now<=target.getPersistentData().getLong(expiry)?target.getPersistentData().getInt(key):0;st=Math.min(5,st+1);target.getPersistentData().putInt(key,st);target.getPersistentData().putLong(expiry,now+100);e.setAmount(e.getAmount()*(1f+.06f*mark*st));}int mercy=lvl(weapon,"mercy_curse");if(mercy>0)e.setAmount(Math.max(0f,Math.min(e.getAmount(),target.getHealth()-1f)));int interrupt=lvl(weapon,"interrupting_strike");if(interrupt>0&&target.isUsingItem())target.stopUsingItem();int guard=lvl(weapon,"guard_breaker");if(guard>0&&target instanceof Player tp&&tp.isBlocking())tp.getCooldowns().addCooldown(tp.getUseItem().getItem(),40+20*guard);int echo=lvl(weapon,"echo_strike");if(echo>0&&target.level() instanceof ServerLevel sl){float dmg=e.getAmount()*(.15f+.10f*echo);TestProcedure.schedule(sl,TestProcedure.currentTick(sl)+10,()->{guardedHurt(target,attacker.damageSources().playerAttack(attacker),dmg);EnchantmentParticleFX.burstOnEntity(target,ParticleTypes.SONIC_BOOM,1,0,0);});}int seismic=lvl(weapon,"seismic_strike");if(seismic>0&&attacker.onGround()&&attacker.getAttackStrengthScale(.5f)>.9f){double sRad=deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.radius(2,.5,seismic);if(target.level() instanceof ServerLevel sl){BlockState ground=sl.getBlockState(target.blockPosition().below());if(ground.isAir())ground=Blocks.STONE.defaultBlockState();EnchantmentParticleFX.blockDebrisRing(sl,target.getX(),target.getY(),target.getZ(),ground,sRad,16);}for(Mob m:attacker.level().getEntitiesOfClass(Mob.class,target.getBoundingBox().inflate(sRad)))if(m!=target)guardedHurt(m,attacker.damageSources().playerAttack(attacker),e.getAmount()*.2f*seismic);}}
    }

    // T306-FIX : Moisson d'Expérience garantit +5 XP fixes/niv (+ bonus sur gros boss) + glyphes d'enchantement
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void experienceDrop(LivingExperienceDropEvent e){Player p=e.getAttackingPlayer();if(p==null)return;int level=lvl(p.getMainHandItem(),"experience_harvest");if(level>0){int base=Math.max(0,e.getDroppedExperience());int bonus=5*level+Math.round(base*.25f*level);e.setDroppedExperience(base+bonus);EnchantmentParticleFX.burstOnEntity(e.getEntity(),ParticleTypes.ENCHANT,10,.35,.25);}}
    @SubscribeEvent public static void livingDeath(LivingDeathEvent e){try{livingDeathImpl(e);}catch(Throwable t__){guard("livingDeath",t__);}} private static void livingDeathImpl(LivingDeathEvent e){if(e.getEntity() instanceof LivingEntity p){int l=armor(p,"final_breath");long n=p.level().getGameTime(),r=p.getPersistentData().getLong("slb_final_breath_ready");if(l>0&&n>=r){e.setCanceled(true);p.setHealth(1f);p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 1));p.getPersistentData().putLong("slb_final_breath_ready",n+2400);EnchantmentParticleFX.doubleHelix(p,ParticleTypes.TOTEM_OF_UNDYING,ParticleTypes.SOUL_FIRE_FLAME,.65,12);}}}
    @SubscribeEvent public static void livingHeal(LivingHealEvent e){try{livingHealImpl(e);}catch(Throwable t__){guard("livingHeal",t__);}} private static void livingHealImpl(LivingHealEvent e){if(e.getEntity() instanceof LivingEntity p){int l=armor(p,"vital_reserve");float excess=p.getHealth()+e.getAmount()-p.getMaxHealth();if(l>0&&excess>0)p.setAbsorptionAmount(Math.min(4f*l,p.getAbsorptionAmount()+excess));}}

    @SubscribeEvent public static void blockBreak(BlockEvent.BreakEvent e){try{blockBreakImpl(e);}catch(Throwable t__){guard("blockBreak",t__);}} private static void blockBreakImpl(BlockEvent.BreakEvent e){if(MINING_GUARD.get())return;Player p=e.getPlayer();ItemStack tool=p.getMainHandItem();Level level=(Level)e.getLevel();BlockPos pos=e.getPos();BlockState state=e.getState();

        int stability=lvl(tool,"mining_stability");if(stability>0)for(BlockPos q:BlockPos.betweenClosed(pos.offset(-stability,-1,-stability),pos.offset(stability,stability,stability))){BlockState s=level.getBlockState(q);if(s.is(Blocks.SAND))level.setBlockAndUpdate(q,Blocks.SANDSTONE.defaultBlockState());else if(s.is(Blocks.RED_SAND))level.setBlockAndUpdate(q,Blocks.RED_SANDSTONE.defaultBlockState());else if(s.is(Blocks.GRAVEL)&&level.getBlockState(q.below()).isAir())level.setBlockAndUpdate(q,Blocks.COBBLESTONE.defaultBlockState());}
        int selective=lvl(tool,"selective_excavation");if(selective>0&&!p.isShiftKeyDown()){int cap=4+4*selective,b=0;try{MINING_GUARD.set(true);for(BlockPos q:BlockPos.betweenClosed(pos.offset(-selective,-selective,-selective),pos.offset(selective,selective,selective))){if(b>=cap)break;if(!q.equals(pos)&&level.getBlockState(q).is(state.getBlock())){level.destroyBlock(q,true,p);b++;}}}finally{MINING_GUARD.set(false);}}
        int delicate=lvl(tool,"delicate_extraction");if(delicate>0&&level.random.nextInt(100)<deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.percent(15,delicate,90)&&state.getBlock().asItem()!=Items.AIR)Block.popResource(level,pos,new ItemStack(state.getBlock().asItem()));
        int prospect=lvl(tool,"prospecting");if(prospect>0){int c=p.getPersistentData().getInt("slb_prospect");if(state.is(Tags.Blocks.ORES)){if(c>=Math.max(8,24-4*prospect))Block.popResource(level,pos,new ItemStack(state.getBlock().asItem()));p.getPersistentData().putInt("slb_prospect",0);}else p.getPersistentData().putInt("slb_prospect",Math.min(100,c+1));}
        int replant=lvl(tool,"replanting");if(replant>0&&state.getBlock() instanceof CropBlock crop&&crop.isMaxAge(state)&&consumeSeed(p,state)){if(level instanceof ServerLevel sl)TestProcedure.schedule(sl,TestProcedure.currentTick(sl)+1,()->{if(level.getBlockState(pos).isAir())level.setBlockAndUpdate(pos,crop.defaultBlockState());});}
        int compost=lvl(tool,"composting");if(compost>0&&state.getBlock() instanceof BushBlock&&level.random.nextInt(100)<deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.percent(12,compost,90))Block.popResource(level,pos,new ItemStack(Items.BONE_MEAL));
        int cave=lvl(tool,"cave_in_curse");if(cave>0&&state.is(BlockTags.BASE_STONE_OVERWORLD)&&level.random.nextInt(100)<deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.percent(4,cave,90)){BlockPos q=pos.above(2);if(level.getBlockState(q).isAir())level.setBlockAndUpdate(q,Blocks.GRAVEL.defaultBlockState());p.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN,60,cave-1));}
    }

    @SubscribeEvent public static void arrowLoose(ArrowLooseEvent e){try{arrowLooseImpl(e);}catch(Throwable t__){guard("arrowLoose",t__);}} private static void arrowLooseImpl(ArrowLooseEvent e){Player p=e.getEntity();ItemStack bow=e.getBow();int recoil=lvl(bow,"recoil_launch");if(recoil>0){p.setDeltaMovement(p.getDeltaMovement().subtract(p.getLookAngle().scale(.18*recoil)));p.hurtMarked=true;}}
    // FIX (mob-equipment-enchant generalization) : LivingEntityUseItemEvent
    // est deja generique (LivingEntity) -- generalise pour couvrir un mob
    // lancant un trident enchante Ocean Current (Drowned, etc.).
    @SubscribeEvent public static void tridentRelease(LivingEntityUseItemEvent.Stop e){try{tridentReleaseImpl(e);}catch(Throwable t__){guard("tridentRelease",t__);}} private static void tridentReleaseImpl(LivingEntityUseItemEvent.Stop e){if(e.getEntity() instanceof LivingEntity p&&e.getItem().is(Items.TRIDENT)){int ocean=lvl(e.getItem(),"ocean_current");if(ocean>0)p.getPersistentData().putInt("slb_pending_ocean_current",ocean);}}
    // FIX (mob-equipment-enchant generalization) : "instanceof Player"
    // -> "instanceof LivingEntity" -- un mob archer (squelette, pillager...)
    // tirant avec un arc/arbalete enchante beneficie desormais lui aussi de
    // Concentration/Tracer Shot/Briar Arrow/Suppression Shot/Elemental
    // Chain/Ocean Current, exactement comme un joueur equipe du meme arc.
    @SubscribeEvent public static void arrowJoin(EntityJoinLevelEvent e){try{arrowJoinImpl(e);}catch(Throwable t__){guard("arrowJoin",t__);}} private static void arrowJoinImpl(EntityJoinLevelEvent e){
        if(e.getLevel().isClientSide()||!(e.getEntity() instanceof AbstractArrow a)||!(a.getOwner() instanceof LivingEntity p))return;
        int concentration=armor(p,"concentration"),focus=p.getPersistentData().getInt("slb_focus");
        if(concentration>0&&focus>0){a.setBaseDamage(a.getBaseDamage()*(1+Math.min(.6,focus*concentration/1000.0)));double speed=a.getDeltaMovement().length();if(speed>.01)a.setDeltaMovement(p.getLookAngle().scale(speed));}
        ItemStack weapon=p.getMainHandItem();if(weapon.isEmpty()||!(weapon.getItem() instanceof BowItem||weapon.getItem() instanceof CrossbowItem||weapon.getItem() instanceof TridentItem))weapon=p.getOffhandItem();
        String[] ids={"tracer_shot","briar_arrow","suppression_shot","elemental_chain","ocean_current"};for(String id:ids){int level=lvl(weapon,id);if(level>0)a.getPersistentData().putInt("slb_"+id,level);}
        int pendingOcean=p.getPersistentData().getInt("slb_pending_ocean_current");if(pendingOcean>0){p.getPersistentData().remove("slb_pending_ocean_current");a.getPersistentData().putInt("slb_ocean_current",pendingOcean);}
        int ocean=a.getPersistentData().getInt("slb_ocean_current");if(ocean>0&&(p.isInWater()||p.level().isRainingAt(p.blockPosition())))a.setDeltaMovement(a.getDeltaMovement().scale(1+.2*ocean));
    }
    // FIX (bug "plein d'enchantements ne fonctionnent pas" : Elemental Chain,
    // Tracer Shot, Suppression Shot, Ocean Current, Briar Arrow) : ce
    // listener partage net.minecraft.world.entity.projectile.Projectile
    // ProjectileImpactEvent avec UniversalEnchantmentHandler.onArrowImpact(),
    // qui gère Pierce/Phasing et appelle event.setCanceled(true) pour laisser
    // la flèche continuer sa trajectoire. NeoForge exécute les listeners d'un
    // évènement annulable dans l'ordre de PRIORITÉ (HIGHEST -> LOWEST), et un
    // listener SANS receiveCanceled=true ne reçoit JAMAIS un évènement déjà
    // annulé par un listener de priorité plus haute exécuté avant lui. Sans
    // priorité explicite ici (NORMAL par défaut, comme dans
    // UniversalEnchantmentHandler), l'ordre entre les deux classes n'est PAS
    // garanti : dès que Pierce/Phasing annulent l'évènement avant que CE
    // listener ne s'exécute, tous les effets ci-dessous (Elemental Chain,
    // Tracer Shot, Suppression Shot, Ocean Current, Briar Arrow) sont
    // silencieusement ignorés sur un arc qui a AUSSI Pierce ou Phasing --
    // exactement la situation de l'Arc de l'Ascension (Pierce V + Elemental
    // Chain III + Briar Arrow III simultanés). On force donc HIGHEST +
    // receiveCanceled=true : ces effets se déclenchent toujours au premier
    // impact réel, qu'il soit ensuite annulé ou non par un autre enchant.
    // FIX (mob-equipment-enchant generalization) : "instanceof Player" ->
    // "instanceof LivingEntity" -- generalise Tracer Shot/Suppression
    // Shot/Elemental Chain/Ocean Current/Briar Arrow a n'importe quel
    // tireur LivingEntity (mob archer avec arc enchante inclus).
    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true) public static void projectile(ProjectileImpactEvent e){try{projectileImpl(e);}catch(Throwable t__){guard("projectile",t__);}} private static void projectileImpl(ProjectileImpactEvent e){if(!(e.getProjectile() instanceof AbstractArrow a)||!(a.getOwner() instanceof LivingEntity p)||p.level().isClientSide())return;ItemStack bow=p.getMainHandItem().isEmpty()?p.getOffhandItem():p.getMainHandItem();if(e.getRayTraceResult() instanceof EntityHitResult hit&&hit.getEntity() instanceof LivingEntity t){int tracer=arrowLvl(a,bow,"tracer_shot");if(tracer>0&&p.level() instanceof ServerLevel sl){t.addEffect(new MobEffectInstance(MobEffects.GLOWING,10,0,false,false));startTracer(sl,t,p,tracer);}int suppress=arrowLvl(a,bow,"suppression_shot");if(suppress>0){t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,40+20*suppress,suppress-1));t.addEffect(new MobEffectInstance(MobEffects.WEAKNESS,40+20*suppress,0));}int elem=arrowLvl(a,bow,"elemental_chain");if(elem>0){if(p.level().dimension()==Level.NETHER)t.setRemainingFireTicks((2+elem) * 20);else if(p.level().isRaining())t.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,40+20*elem,elem-1));else if(p.level().dimension()==Level.END)t.addEffect(new MobEffectInstance(MobEffects.LEVITATION,10+5*elem,0));}int ocean=a.getPersistentData().getInt("slb_ocean_current");if(ocean>0&&t.isInWater()){guardedHurt(t,p.damageSources().drown(),2f*ocean);for(LivingEntity x:p.level().getEntitiesOfClass(LivingEntity.class,t.getBoundingBox().inflate(deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.radius(4,1,ocean)),x->x!=p&&x!=t&&x.isInWater()))x.setDeltaMovement(x.getDeltaMovement().add(t.position().subtract(x.position()).normalize().scale(.08*ocean)));}}
        if(e.getRayTraceResult() instanceof BlockHitResult bh){int briar=arrowLvl(a,bow,"briar_arrow");if(briar>0&&p.level() instanceof ServerLevel sl){BlockPos c=bh.getBlockPos().relative(bh.getDirection());for(BlockPos q:BlockPos.betweenClosed(c.offset(-briar,0,-briar),c.offset(briar,0,briar))){if(sl.getBlockState(q).isAir()&&sl.getBlockState(q.below()).is(Blocks.GRASS_BLOCK)&&sl.random.nextInt(100)<35){sl.setBlockAndUpdate(q,Blocks.SWEET_BERRY_BUSH.defaultBlockState().setValue(SweetBerryBushBlock.AGE,2));BlockPos saved=q.immutable();TestProcedure.schedule(sl,TestProcedure.currentTick(sl)+200,()->{if(sl.getBlockState(saved).is(Blocks.SWEET_BERRY_BUSH))sl.setBlockAndUpdate(saved,Blocks.AIR.defaultBlockState());});}}}}
    }
    // FIX (mob-equipment-enchant generalization) : "instanceof Player" ->
    // "instanceof LivingEntity" -- un mob bloquant avec un bouclier
    // enchante (Perfect Parry, Resonance Curse) beneficie desormais lui
    // aussi de ces effets defensifs.
    @SubscribeEvent public static void shieldAttack(LivingIncomingDamageEvent e){try{shieldAttackImpl(e);}catch(Throwable t__){guard("shieldAttack",t__);}} private static void shieldAttackImpl(LivingIncomingDamageEvent e){if(!(e.getEntity() instanceof LivingEntity p)||!p.isBlocking())return;ItemStack s=p.getUseItem();int parry=lvl(s,"perfect_parry");if(parry>0&&p.getTicksUsingItem()<=4+parry&&e.getSource().getEntity() instanceof LivingEntity a){a.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN,20+10*parry,2));a.setDeltaMovement(a.position().subtract(p.position()).normalize().scale(.5+.25*parry));a.hurtMarked=true;}int resonance=lvl(s,"resonance_curse");if(resonance>0){p.level().playSound(null,p.blockPosition(),SoundEvents.WARDEN_HEARTBEAT,SoundSource.PLAYERS,1,1);p.getPersistentData().putInt("slb_resonance_vulnerable",resonance);for(Mob m:p.level().getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.radius(8,3,resonance))))m.setTarget(p);}}

    private static void playFakePack(LivingEntity p){SoundEvent[] sounds={SoundEvents.ZOMBIE_AMBIENT,SoundEvents.SKELETON_AMBIENT,SoundEvents.SPIDER_AMBIENT,SoundEvents.CREEPER_PRIMED,SoundEvents.ENDERMAN_STARE,SoundEvents.WARDEN_NEARBY_CLOSE};int n=1+p.getRandom().nextInt(3);for(int i=0;i<n;i++){double ang=p.getRandom().nextDouble()*Math.PI*2,dist=2+p.getRandom().nextDouble()*8;p.level().playSound(null,p.getX()+Math.cos(ang)*dist,p.getY(),p.getZ()+Math.sin(ang)*dist,sounds[p.getRandom().nextInt(sounds.length)],SoundSource.HOSTILE,.9f,.8f+p.getRandom().nextFloat()*.4f);}}
    private static void updateOreGuidance(ServerPlayer player, ServerLevel level, int enchantLevel) {
        OreGuidanceState state=ORE_GUIDANCE.computeIfAbsent(player.getUUID(),u->new OreGuidanceState());
        String dimension=level.dimension().location().toString();
        if(!state.dimension.equals(dimension)||state.level!=enchantLevel){state.targets.clear();state.nextScan=0;state.dimension=dimension;state.level=enchantLevel;}
        boolean removed=state.targets.removeIf(target->!target.refresh(level));
        int wanted=maxOreTargets(enchantLevel);long now=level.getGameTime();
        if(now>=state.nextScan||removed||state.targets.size()<Math.min(2,wanted)){
            state.targets.clear();state.targets.addAll(scanOreTargets(level,player,enchantLevel,wanted));state.nextScan=now+100;
        }
        for(OreTarget target:state.targets)drawOreTrail(player,level,target);
    }

    private static int maxOreTargets(int level){return switch(Math.min(5,level)){case 1->5;case 2->7;case 3->10;case 4->15;default->20;};}
    private static int detectionRadius(int level){return 16+8*Math.min(5,level);}

    private static List<OreTarget> scanOreTargets(ServerLevel level,ServerPlayer player,int enchantLevel,int maximum){
        BlockPos origin=player.blockPosition();int radius=detectionRadius(enchantLevel);LinkedHashSet<BlockPos> candidates=new LinkedHashSet<>();
        int local=Math.min(10,radius);for(BlockPos p:BlockPos.betweenClosed(origin.offset(-local,-local,-local),origin.offset(local,local,local)))if(isAllowedOre(level.getBlockState(p),enchantLevel))candidates.add(p.immutable());
        RandomSource random=level.random;int samples=10000+5000*Math.min(5,enchantLevel);for(int i=0;i<samples;i++){int dx=random.nextInt(radius*2+1)-radius,dy=random.nextInt(radius*2+1)-radius,dz=random.nextInt(radius*2+1)-radius;if(dx*dx+dy*dy+dz*dz>radius*radius)continue;BlockPos p=origin.offset(dx,dy,dz);if(level.hasChunkAt(p)&&isAllowedOre(level.getBlockState(p),enchantLevel))candidates.add(p);}
        ArrayList<BlockPos> ordered=new ArrayList<>(candidates);
        // IMPORTANT : le score est calculé UNE SEULE FOIS. Utiliser un nombre
        // aléatoire directement dans le Comparator rendait la comparaison non
        // transitive et faisait crasher TimSort (« violates its general contract »).
        Map<Long,Double> scores=new HashMap<>();
        for(BlockPos pos:ordered)scores.put(pos.asLong(),oreScore(level,pos,origin,enchantLevel));
        ordered.sort(Comparator.<BlockPos>comparingDouble(pos->scores.get(pos.asLong()))
                .reversed().thenComparingLong(pos->pos.asLong()));
        ArrayList<OreTarget> result=new ArrayList<>();HashSet<Long> consumed=new HashSet<>();for(BlockPos candidate:ordered){if(result.size()>=maximum)break;if(consumed.contains(candidate.asLong()))continue;String family=oreFamily(level.getBlockState(candidate));List<BlockPos> vein=connectedVeinBlocks(level,candidate,family);for(BlockPos p:vein)consumed.add(p.asLong());if(!vein.isEmpty())result.add(new OreTarget(family,oreColor(family),vein));}
        return result;
    }

    private static boolean isAllowedOre(BlockState state,int level){if(!isOreLike(state))return false;ResourceLocation id=BuiltInRegistries.BLOCK.getKey(state.getBlock());if(id!=null&&!id.getNamespace().equals("minecraft"))return true;String f=oreFamily(state);int needed=switch(f){case "coal","iron","copper"->1;case "gold","redstone","lapis"->2;case "diamond","emerald"->3;case "netherite"->4;default->2;};return level>=needed;}
    private static double oreScore(ServerLevel level,BlockPos pos,BlockPos origin,int enchantLevel){BlockState state=level.getBlockState(pos);String f=oreFamily(state);ResourceLocation id=BuiltInRegistries.BLOCK.getKey(state.getBlock());boolean modded=id!=null&&!id.getNamespace().equals("minecraft");double priority=switch(f){case "coal"->110;case "iron"->108;case "copper"->100;case "gold"->enchantLevel>=5?185:145;case "redstone","lapis"->135;case "diamond"->enchantLevel>=5?205:180;case "emerald"->178;case "netherite"->enchantLevel>=5?235:220;default->modded?(enchantLevel>=5?210:120):90;};long mixed=pos.asLong()^(long)f.hashCode()*0x9E3779B97F4A7C15L;double stableJitter=Math.floorMod(mixed,1000L)/1000.0;double distance=Math.sqrt(pos.distSqr(origin));return -distance+priority*.10+stableJitter*.02;}

    private static void drawOreTrail(ServerPlayer player,ServerLevel level,OreTarget target){net.minecraft.world.phys.Vec3 center=target.center();net.minecraft.world.phys.Vec3 fromPlayer=center.subtract(player.position());net.minecraft.world.phys.Vec3 horizontal=new net.minecraft.world.phys.Vec3(fromPlayer.x,0,fromPlayer.z);if(horizontal.lengthSqr()<.0001){net.minecraft.world.phys.Vec3 look=player.getLookAngle();horizontal=new net.minecraft.world.phys.Vec3(look.x,0,look.z);}horizontal=horizontal.normalize();net.minecraft.world.phys.Vec3 from=player.position().add(horizontal.scale(1.35)).add(0,1.25,0);net.minecraft.world.phys.Vec3 delta=center.subtract(from);double distance=delta.length();if(distance<.01)return;net.minecraft.world.phys.Vec3 direction=delta.scale(1.0/distance);double length=Math.min(5.0,distance);int index=0;for(double d=0;d<=length;d+=.38){double progress=d/Math.max(.01,length);float particleSize=(float)(.30+.85*progress);DustParticleOptions dust=new DustParticleOptions(target.color,particleSize);net.minecraft.world.phys.Vec3 point=from.add(direction.scale(d));level.sendParticles(player,dust,true,point.x,point.y,point.z,2,.012+.008*progress,.012+.008*progress,.012+.008*progress,0);if(d>=1.5&&index%3==0)level.sendParticles(player,ParticleTypes.END_ROD,true,point.x,point.y,point.z,1,0,0,0,0);index++;}}

    private static boolean isOreLike(BlockState state){if(state.is(Tags.Blocks.ORES))return true;ResourceLocation id=BuiltInRegistries.BLOCK.getKey(state.getBlock());if(id==null)return false;String path=id.getPath().toLowerCase(Locale.ROOT);return path.contains("_ore")||path.startsWith("ore_")||path.contains("raw_")||path.contains("_raw")||path.contains("ancient_debris");}
    private static String oreFamily(BlockState state){ResourceLocation id=BuiltInRegistries.BLOCK.getKey(state.getBlock());String path=id==null?"unknown":id.getPath().toLowerCase(Locale.ROOT);String[] known={"diamond","emerald","redstone","lapis","copper","iron","gold","coal","quartz","netherite","uranium","tin","silver","lead","nickel","zinc","osmium","aluminum","aluminium","cobalt"};for(String name:known)if(path.contains(name))return name;if(path.contains("ancient_debris"))return "netherite";return path.replace("deepslate_","").replace("_ore","").replace("ore_","").replace("raw_","").replace("_block","");}
    private static Vector3f oreColor(String family){return switch(family){case "iron"->new Vector3f(.95f,.95f,.92f);case "emerald"->new Vector3f(.1f,.95f,.3f);case "diamond"->new Vector3f(.15f,.9f,1f);case "lapis"->new Vector3f(.08f,.2f,.85f);case "redstone"->new Vector3f(1f,.05f,.03f);case "gold"->new Vector3f(1f,.85f,.05f);case "copper"->new Vector3f(.65f,.28f,.10f);case "coal"->new Vector3f(.12f,.12f,.12f);case "quartz"->new Vector3f(.95f,.88f,.8f);case "netherite"->new Vector3f(.28f,.18f,.16f);case "uranium"->new Vector3f(.45f,1f,.05f);case "tin"->new Vector3f(.65f,.85f,.9f);case "silver"->new Vector3f(.8f,.88f,1f);case "lead"->new Vector3f(.25f,.28f,.42f);case "nickel"->new Vector3f(.78f,.7f,.3f);case "zinc"->new Vector3f(.55f,.7f,.72f);case "osmium"->new Vector3f(.35f,.55f,1f);case "aluminum","aluminium"->new Vector3f(.8f,.82f,.85f);case "cobalt"->new Vector3f(.05f,.3f,1f);default->{int h=Math.abs(family.hashCode());yield new Vector3f(.25f+(h&255)/510f,.25f+((h>>8)&255)/510f,.25f+((h>>16)&255)/510f);}};}
    private static List<BlockPos> connectedVeinBlocks(ServerLevel level,BlockPos start,String family){ArrayDeque<BlockPos> queue=new ArrayDeque<>();HashSet<BlockPos> seen=new HashSet<>();ArrayList<BlockPos> blocks=new ArrayList<>();queue.add(start);seen.add(start);while(!queue.isEmpty()&&blocks.size()<256){BlockPos pos=queue.removeFirst();BlockState state=level.getBlockState(pos);if(!isOreLike(state)||!oreFamily(state).equals(family))continue;blocks.add(pos.immutable());for(BlockPos n:BlockPos.betweenClosed(pos.offset(-1,-1,-1),pos.offset(1,1,1))){BlockPos immutable=n.immutable();if(seen.add(immutable))queue.addLast(immutable);}}return blocks;}

    // NOTE (mob-equipment-enchant generalization) : shooter est desormais
    // LivingEntity (peut etre un mob archer). Le rendu du tracer reste
    // intrinsequement visuel-JOUEUR (sendParticles cible un ServerPlayer
    // precis, voir trace() ci-dessous) -- si le tireur n'est PAS un vrai
    // joueur connecte, TRACER_VIEWER contiendra un UUID de mob, la
    // recherche de viewer dans trace() echouera silencieusement (viewer ==
    // null) et le tracer state pour cette cible sera immediatement
    // nettoye au premier cycle, sans effet visuel -- comportement sur, pas
    // de NPE, simplement "pas de spectateur a qui montrer le tracer".
    private static void startTracer(ServerLevel level,LivingEntity target,LivingEntity shooter,int enchantLevel){UUID id=target.getUUID();int end=TestProcedure.currentTick(level)+600+200*Math.max(0,enchantLevel-1);TRACER_END.put(id,Math.max(end,TRACER_END.getOrDefault(id,0)));TRACER_LEVEL.put(id,Math.max(enchantLevel,TRACER_LEVEL.getOrDefault(id,1)));TRACER_VIEWER.put(id,shooter.getUUID());if(!TRACER_HISTORY.containsKey(id)){TRACER_HISTORY.put(id,new ArrayDeque<>());trace(level,target);}}
    private static void trace(ServerLevel level,LivingEntity target){UUID id=target.getUUID();int now=TestProcedure.currentTick(level),end=TRACER_END.getOrDefault(id,0),enchantLevel=TRACER_LEVEL.getOrDefault(id,1);if(!target.isAlive()||now>end){TRACER_HISTORY.remove(id);TRACER_END.remove(id);TRACER_LEVEL.remove(id);TRACER_VIEWER.remove(id);return;}ServerPlayer viewer=level.getServer().getPlayerList().getPlayer(TRACER_VIEWER.get(id));if(viewer==null){TRACER_HISTORY.remove(id);TRACER_END.remove(id);TRACER_LEVEL.remove(id);TRACER_VIEWER.remove(id);return;}ArrayDeque<net.minecraft.world.phys.Vec3> history=TRACER_HISTORY.computeIfAbsent(id,k->new ArrayDeque<>());net.minecraft.world.phys.Vec3 current=new net.minecraft.world.phys.Vec3(target.getX(),target.getY()+target.getBbHeight()*.55,target.getZ());history.addFirst(current);int historyLimit=8+4*enchantLevel;while(history.size()>historyLimit)history.removeLast();double radius=.45+.28*enchantLevel;int ring=8+4*enchantLevel;for(int i=0;i<ring;i++){double angle=Math.PI*2*i/ring,x=current.x+Math.cos(angle)*radius,z=current.z+Math.sin(angle)*radius;level.sendParticles(viewer,ParticleTypes.END_ROD,true,x,current.y,z,1,0,0,0,0);if(i%2==0)level.sendParticles(viewer,ParticleTypes.ELECTRIC_SPARK,true,x,current.y,z,1,0,0,0,0);}int index=0;for(net.minecraft.world.phys.Vec3 old:history){if(index++==0)continue;int count=Math.max(1,4-index/3);level.sendParticles(viewer,ParticleTypes.END_ROD,true,old.x,old.y,old.z,1,.035,.035,.035,.002);level.sendParticles(viewer,ParticleTypes.WHITE_ASH,true,old.x,old.y+.15,old.z,count,.08,.06,.08,.008);}TestProcedure.schedule(level,now+4,()->trace(level,target));}
    private static boolean nearWall(LivingEntity p){BlockPos c=p.blockPosition();return p.level().getBlockState(c.north()).blocksMotion()||p.level().getBlockState(c.south()).blocksMotion()||p.level().getBlockState(c.east()).blocksMotion()||p.level().getBlockState(c.west()).blocksMotion();}
    private static void trail(LivingEntity p,boolean frost,int l){if(!(p.level() instanceof ServerLevel sl))return;BlockPos q=p.blockPosition().below();if(frost&&sl.getBlockState(q).is(Blocks.WATER)){sl.setBlockAndUpdate(q,Blocks.FROSTED_ICE.defaultBlockState());BlockPos z=q.immutable();TestProcedure.schedule(sl,TestProcedure.currentTick(sl)+80+20*l,()->{if(sl.getBlockState(z).is(Blocks.FROSTED_ICE))sl.setBlockAndUpdate(z,Blocks.WATER.defaultBlockState());});}else if(!frost){BlockPos a=q.above();if(sl.getBlockState(a).isAir()&&sl.getBlockState(q).blocksMotion()){sl.setBlockAndUpdate(a,Blocks.FIRE.defaultBlockState());BlockPos z=a.immutable();TestProcedure.schedule(sl,TestProcedure.currentTick(sl)+30+10*l,()->{if(sl.getBlockState(z).is(Blocks.FIRE))sl.setBlockAndUpdate(z,Blocks.AIR.defaultBlockState());});}}}
    private static boolean consumeSeed(Player p,BlockState s){Item wanted=s.is(Blocks.WHEAT)?Items.WHEAT_SEEDS:s.is(Blocks.BEETROOTS)?Items.BEETROOT_SEEDS:s.is(Blocks.CARROTS)?Items.CARROT:s.is(Blocks.POTATOES)?Items.POTATO:Items.AIR;if(wanted==Items.AIR)return false;for(int i=0;i<p.getInventory().getContainerSize();i++){ItemStack x=p.getInventory().getItem(i);if(x.is(wanted)){if(!p.isCreative())x.shrink(1);return true;}}return false;}
    private static void shorten(LivingEntity p,net.minecraft.core.Holder<net.minecraft.world.effect.MobEffect> e,int l){MobEffectInstance x=p.getEffect(e);if(x!=null){int n=x.getDuration()-20*l;if(n<=0)p.removeEffect(e);else p.addEffect(new MobEffectInstance(e,n,x.getAmplifier(),x.isAmbient(),x.isVisible(),x.showIcon()));}}
    private static void setSpeed(LivingEntity p,ResourceLocation id,double amount){AttributeInstance a=p.getAttribute(Attributes.MOVEMENT_SPEED);if(a==null)return;a.removeModifier(id);if(Math.abs(amount)>.0001)a.addTransientModifier(new AttributeModifier(id,amount,AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));}
    private static final ResourceLocation KNOCK_ID=ResourceLocation.parse("deep_lucky_block:modifier_knockback");private static void setKnockback(LivingEntity p,double amount){setKnockbackBonus(p,KNOCK_ID,amount);}private static void setKnockbackBonus(LivingEntity p,ResourceLocation id,double amount){AttributeInstance a=p.getAttribute(Attributes.KNOCKBACK_RESISTANCE);if(a==null)return;a.removeModifier(id);if(amount>0)a.addTransientModifier(new AttributeModifier(id,Math.min(1,amount),AttributeModifier.Operation.ADD_VALUE));}

    // ==================== COEURS ELEMENTAIRES (feature demandee) ====================
    // Un enchantement de plastron par element, +1 coeur (2 PV) par niveau
    // (niveau max 3 -> jusqu'a +6 PV), applique/retire via un modificateur
    // MAX_HEALTH transitoire (comme setArmor/setSpeed ci-dessus : recalcule
    // a chaque tick a partir du plastron porte, jamais persiste en NBT donc
    // jamais desynchronise si le plastron est retire/change/detruit).
    // L'element actif (pour le rendu HUD colore, voir ElementalHeartOverlay)
    // est mémorisé dans les PersistentData du joueur.
    private static final String[] ELEMENTAL_HEART_IDS = {
        "fire_heart", "water_heart", "earth_heart", "wind_heart",
        "electric_heart", "shadow_heart", "light_heart"
    };
    // ==================== ASK #4 : TIMING DE REGENERATION LENTE ====================
    // Voir Javadoc detaillee dans updateElementalHearts() a l'endroit ou ces
    // constantes sont utilisees. Resume : condition tenue en continu
    // (jamais remise a zero par une courte interruption, seulement gelee)
    // pendant HOLD_TICKS_BEFORE_REGEN avant le premier demi-coeur, puis
    // STAGE_TICKS supplementaires par moitie -- environ 40s reelles au
    // total par coeur perdu, soit largement plus de 2x plus lent que la
    // perte (qui peut arriver en un seul coup encaisse).
    private static final long HOLD_TICKS_BEFORE_REGEN = 200; // 10s avant meme le 1er demi-coeur
    // FIX (rapporté en jeu, "et meme de 05 a 1 coeur, tu mets une pause trop
    // longue, la regen semble stoppée pendant trop longtemps, une fois le
    // demicoeur fait, tu enchaine direct pour avoir le coeur complet avec
    // les anims") : la VERSION PRECEDENTE attendait le MEME délai STAGE_TICKS
    // (300 ticks = 15s) pour les DEUX moitiés (vide->demi ET demi->plein),
    // donnant l'impression d'un arrêt complet après le demi-coeur alors que
    // le joueur s'attend à voir l'animation "enchaîner directement" vers le
    // coeur complet. Seule la PREMIÈRE moitié (vide->demi, le tout premier
    // signe visible de régénération après la perte) garde un délai "lent et
    // satisfaisant" comparable à HOLD_TICKS_BEFORE_REGEN -- la SECONDE
    // moitié (demi->plein) n'attend plus qu'un court délai, juste assez pour
    // laisser l'animation de remplissage du demi-coeur se terminer
    // visuellement (voir ANIM_FRAME_TICKS_GAIN côté client) avant
    // d'enchaîner immédiatement sur celle du coeur complet, au lieu de
    // rester figé pendant 15 secondes supplémentaires.
    private static final long STAGE_TICKS = 300;              // 15s : vide -> demi (1ere moitie, inchange)
    private static final long STAGE_TICKS_SECOND_HALF = 40;   // 2s : demi -> plein (enchaine directement)
    // FIX (rapporté en jeu, "l'animation mets trop de temps entre deux
    // coeurs a charger") : en plus des deux délais ci-dessus (par coeur),
    // l'ancien code ajoutait un TROISIÈME délai fixe de 200 ticks (10s)
    // *entre* deux coeurs perdus consécutifs (cooldown avant même de
    // recommencer à accumuler pour le coeur suivant), en plus du délai
    // HOLD_TICKS_BEFORE_REGEN déjà requis pour ce coeur suivant --
    // cumulant deux pauses de 10s d'affilée (20s de silence total) entre la
    // fin d'un coeur et le tout premier signe de progression du suivant.
    // Ce cooldown supplémentaire, jamais demandé explicitement par
    // l'utilisateur, est désormais supprimé : HOLD_TICKS_BEFORE_REGEN reste
    // le SEUL espacement naturel entre deux coeurs, largement suffisant
    // pour que chaque étape reste bien visible sans provoquer l'impression
    // d'un chargement anormalement lent rapportée en jeu.
    private static final long INTER_HEART_COOLDOWN_TICKS = 0;

    private static final ResourceLocation[] ELEMENTAL_HEART_MODIFIER_IDS = new ResourceLocation[ELEMENTAL_HEART_IDS.length];
    static {
        for (int i = 0; i < ELEMENTAL_HEART_IDS.length; i++) {
            ELEMENTAL_HEART_MODIFIER_IDS[i] = ResourceLocation.parse("deep_lucky_block:modifier_" + ELEMENTAL_HEART_IDS[i]);
        }
    }

    // FIX (mob-equipment-enchant generalization) : generalise de Player a
    // LivingEntity -- un mob (squelette, zombie, etc.) portant un plastron
    // enchante d'un coeur elementaire recoit desormais lui aussi le bonus
    // de PV MAX correspondant (auparavant reserve au joueur uniquement,
    // alors que RealAttributeModifierHandler applique deja les VRAIS
    // AttributeModifier d'item de la meme facon a n'importe quel porteur).
    private static void updateElementalHearts(LivingEntity p) {
        AttributeInstance healthAttr = p.getAttribute(Attributes.MAX_HEALTH);
        if (healthAttr == null) return;
        // FIX (rapporte en jeu : "les coeurs elementaires donnent quand meme
        // leur bonus de PV en Creatif alors qu'on est invincible") : le
        // joueur Creatif/Spectateur/invulnerable n'a pas de jauge de vie
        // pertinente -- on retire tout bonus deja applique et on n'en
        // recalcule aucun nouveau, au lieu de laisser le bonus vivre alors
        // que l'overlay HUD, lui, se cachait deja correctement. Un MOB n'a
        // pas de notion de Creatif/Spectateur/invulnerable ability -- ce
        // garde-fou ne s'applique donc qu'aux Player, un mob passe toujours
        // au calcul normal ci-dessous.
        if (p instanceof Player pl && (pl.isCreative() || pl.isSpectator() || pl.getAbilities().invulnerable)) {
            for (ResourceLocation id : ELEMENTAL_HEART_MODIFIER_IDS) healthAttr.removeModifier(id);
            p.getPersistentData().putString("slb_elemental_hearts", "");
            p.getPersistentData().putInt("slb_elemental_hearts_total", 0);
            return;
        }
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        float oldMax = p.getMaxHealth();
        int totalBonusHearts = 0;
        int[] levelsSnapshot = new int[ELEMENTAL_HEART_IDS.length];
        StringBuilder activeElements = new StringBuilder();
        for (int i = 0; i < ELEMENTAL_HEART_IDS.length; i++) {
            int level = lvl(chest, ELEMENTAL_HEART_IDS[i]);
            levelsSnapshot[i] = level;
            healthAttr.removeModifier(ELEMENTAL_HEART_MODIFIER_IDS[i]);
            if (level > 0) {
                healthAttr.addTransientModifier(new AttributeModifier(
                        ELEMENTAL_HEART_MODIFIER_IDS[i], 2.0 * level, AttributeModifier.Operation.ADD_VALUE));
                totalBonusHearts += level;
                if (activeElements.length() > 0) activeElements.append(',');
                activeElements.append(ELEMENTAL_HEART_IDS[i]).append(':').append(level);
            }
        }
        // Si la vie max augmente, on ajoute la difference a la vie actuelle
        // (comme vanilla le fait pour Health Boost) pour ne jamais laisser le
        // joueur "sous" sa nouvelle jauge sans possibilite de la remplir.
        float newMax = p.getMaxHealth();
        if (newMax > oldMax && p.getHealth() > 0f) {
            p.setHealth(Math.min(newMax, p.getHealth() + (newMax - oldMax)));
        } else if (newMax < oldMax) {
            p.setHealth(Math.min(p.getHealth(), newMax));
        }
        // ==================== ASK #9 : PERTE/REGEN PAR CONDITION ====================
        // "chaque type de coeur = UNE seule condition de regen ; un coeur
        // perdu hors condition NE regenere PAS tant que la condition n'est
        // pas remplie". On applique ici le bonus de PV REDUIT du nombre de
        // coeurs actuellement "perdus" par element (voir ElementalHeartLoss,
        // NBT sur le plastron -- lu/ecrit AUSSI par livingHurtImpl ci-dessus
        // pour convertir une perte de vie reelle en perte d'un coeur bonus).
        // On retouche donc le modificateur DEJA pose plus haut pour chaque
        // element actif, en le reduisant du nombre de coeurs perdus, PUIS on
        // tente une regen (1 coeur a la fois) si la condition est remplie.
        long gameTime = p.level().getGameTime();
        int totalLost = 0;
        for (int i = 0; i < ELEMENTAL_HEART_IDS.length; i++) {
            int level = levelsSnapshot[i];
            if (level <= 0) continue;
            String elementId = ELEMENTAL_HEART_IDS[i];
            int lost = deepluckyblock.util.ElementalHeartLoss.getLost(chest, elementId);
            if (lost <= 0) {
                // Rien a regenerer pour cet element : on remet a zero toute
                // progression residuelle (evite qu'un vieux compteur reste
                // fige puis morde immediatement sur un futur coeur perdu).
                deepluckyblock.util.ElementalHeartLoss.resetConditionProgress(chest, elementId);
                deepluckyblock.util.ElementalHeartLoss.setRegenStage(chest, elementId, 0);
                continue;
            }
            // ==================== ASK #4 : REGEN LENTE, EN 2 ETAPES, JAMAIS INSTANTANEE ====================
            // FIX CRITIQUE (rapporte en jeu : "je rentre dans l'eau et je
            // regen instantanement un coeur complet, c'est abuse ; il faut
            // un effet de regen lent, solide et beau, en 2 etapes bien
            // visibles : 0 -> 0.5, pause, 0.5 -> 1.0, et la regen doit etre
            // ~2x plus lente que la moyenne (perte) pour bien voir les deux
            // etapes") : remplace l'ancien systeme "1 coeur d'un coup des
            // que gameTime >= regenAt" par une progression EXPLICITE en
            // TICKS ACCUMULES pendant que la condition reste vraie (gelee,
            // jamais remise a zero, si la condition devient momentanement
            // fausse -- voir ElementalHeartLoss). Chronologie par coeur :
            //   1. HOLD_TICKS_BEFORE_REGEN (200 ticks = 10s) de condition
            //      tenue avant meme le premier demi-coeur -- exclut tout
            //      "aller-retour eclair" (ex: sauter 1 seconde dans l'eau).
            //   2. STAGE_TICKS (300 ticks = 15s) de plus -> premiere moitie
            //      accordee (le coeur passe de "vide" a "demi", visible en
            //      jeu et anime cote client via ANIM_STAGE_HALF, voir
            //      ElementalHeartOverlay).
            //   3. STAGE_TICKS (300 ticks = 15s) de plus -> seconde moitie
            //      accordee (le coeur passe de "demi" a "plein").
            // Total ~40 secondes reelles par coeur perdu, contre l'ancien
            // "jusqu'a plusieurs coeurs en 2-3 secondes" : c'est bien >2x
            // plus lent que le rythme de PERTE (1 coup encaisse = -2 PV =
            // potentiellement 1 coeur casse instantanement), donc les deux
            // etapes de la regen restent toujours largement visibles et
            // jamais "ratees" par un joueur qui ne regarde qu'un instant.
            boolean conditionNow = deepluckyblock.util.ElementalHeartConditions.conditionMet(p, elementId);
            if (conditionNow) {
                long progress = deepluckyblock.util.ElementalHeartLoss.getConditionProgressTicks(chest, elementId) + 10;
                int stage = deepluckyblock.util.ElementalHeartLoss.getRegenStage(chest, elementId);
                // progress est remis a 0 a CHAQUE transition d'etape (voir plus
                // bas), donc le seuil est simplement celui DE L'ETAPE EN COURS :
                // stage 0 (avant le 1er demi-coeur) attend HOLD_TICKS_BEFORE_REGEN,
                // stage 1 (avant la 2e moitie) attend desormais SEULEMENT
                // STAGE_TICKS_SECOND_HALF (FIX "pause trop longue entre 0.5
                // et 1 coeur" -- voir Javadoc de la constante) au lieu du
                // meme STAGE_TICKS que la 1ere moitie.
                long threshold = (stage == 0) ? HOLD_TICKS_BEFORE_REGEN : STAGE_TICKS_SECOND_HALF;
                if (progress >= threshold && gameTime >= deepluckyblock.util.ElementalHeartLoss.getRegenAt(chest, elementId)) {
                    if (stage == 0) {
                        // Premiere moitie : le coeur passe visuellement a
                        // "demi" mais ne rend PAS encore de PV (le bonus de
                        // vie n'est accorde qu'a la moitie complete, stage 1
                        // -> evite un micro-gain de PV a la moitie qui
                        // rendrait le HUD et la jauge de vie incoherents).
                        deepluckyblock.util.ElementalHeartLoss.setRegenStage(chest, elementId, 1);
                        deepluckyblock.util.DebugLog.heart("{} : demi-coeur regenere (etape 1/2) -- condition tenue en continu : {}",
                                elementId, deepluckyblock.util.ElementalHeartConditions.describe(elementId));
                        spawnHeartFx(p, elementId, true);
                        // Nouveau palier : redemarre le decompte pour la 2e moitie.
                        progress = 0;
                    } else {
                        // Seconde moitie : le coeur redevient officiellement
                        // "non perdu" (PV restaures) et l'etape repart a 0
                        // pour le PROCHAIN coeur perdu de cet element.
                        lost--;
                        deepluckyblock.util.ElementalHeartLoss.setLost(chest, elementId, lost, level);
                        deepluckyblock.util.ElementalHeartLoss.setRegenStage(chest, elementId, 0);
                        // FIX (rapporté en jeu, "l'animation mets trop de
                        // temps entre deux coeurs a charger") : ce délai
                        // supplémentaire de 200 ticks (10s) empilait une
                        // DEUXIÈME pause de 10s par-dessus celle déjà exigée
                        // par HOLD_TICKS_BEFORE_REGEN pour le coeur suivant
                        // (20s de silence total avant le moindre signe de
                        // progression) -- voir Javadoc de
                        // INTER_HEART_COOLDOWN_TICKS. Supprimé : le seul
                        // espacement restant entre deux coeurs est désormais
                        // HOLD_TICKS_BEFORE_REGEN, déjà largement perceptible.
                        deepluckyblock.util.ElementalHeartLoss.setRegenAt(chest, elementId, gameTime + INTER_HEART_COOLDOWN_TICKS);
                        deepluckyblock.util.DebugLog.heart("{} : coeur entierement regenere (etape 2/2, {} restants perdus) -- condition tenue en continu : {}",
                                elementId, lost, deepluckyblock.util.ElementalHeartConditions.describe(elementId));
                        spawnHeartFx(p, elementId, true);
                        progress = 0;
                    }
                }
                deepluckyblock.util.ElementalHeartLoss.setConditionProgressTicks(chest, elementId, progress);
            }
            // Condition fausse : la progression reste GELEE (pas de reset),
            // voir Javadoc ElementalHeartLoss -- rien a faire ici.
            totalLost += lost;
            if (lost > 0) {
                // Reduit le modificateur MAX_HEALTH deja pose pour cet
                // element de 2 PV par coeur perdu (jamais sous 0).
                healthAttr.removeModifier(ELEMENTAL_HEART_MODIFIER_IDS[i]);
                double remaining = Math.max(0, 2.0 * (level - lost));
                if (remaining > 0) {
                    healthAttr.addTransientModifier(new AttributeModifier(
                            ELEMENTAL_HEART_MODIFIER_IDS[i], remaining, AttributeModifier.Operation.ADD_VALUE));
                }
            }
        }
        if (totalLost > 0 && p.getHealth() > p.getMaxHealth()) {
            p.setHealth(p.getMaxHealth());
        }
        p.getPersistentData().putInt("slb_elemental_hearts_lost_total", totalLost);

        // Mémorisé pour le rendu HUD client (ElementalHeartOverlay) : quels
        // éléments sont actifs et à quel niveau, séparés par virgule.
        p.getPersistentData().putString("slb_elemental_hearts", activeElements.toString());
        p.getPersistentData().putInt("slb_elemental_hearts_total", totalBonusHearts);

        // Log concis (demande utilisateur, ask #6) : UNE ligne quand un
        // coeur elementaire apparait/change de niveau -- jamais a chaque
        // tick (cette methode tourne toutes les 10 ticks). On compare a
        // l'etat precedemment logge par JOUEUR et on ne logge QUE la ou les
        // differences (ex: "fire hearts +3"), pas l'etat complet a chaque
        // fois. Passe par DebugLog (ask #7) : coupe automatiquement si le
        // flag debug est desactive, jamais de spam.
        logHeartChanges(p.getUUID(), activeElements.toString());
    }

    /**
     * Casse UN coeur du DERNIER element elementaire encore intact (ordre de
     * {@link #ELEMENTAL_HEART_IDS}, qui correspond a l'ordre d'affichage
     * dans {@code ElementalHeartOverlay}). Renvoie l'id de l'element casse,
     * ou {@code null} si aucun coeur bonus n'est disponible a casser.
     */
    private static String breakOneElementalHeart(LivingEntity p, ItemStack chest) {
        for (int i = ELEMENTAL_HEART_IDS.length - 1; i >= 0; i--) {
            String elementId = ELEMENTAL_HEART_IDS[i];
            int level = lvl(chest, elementId);
            if (level <= 0) continue;
            int lost = deepluckyblock.util.ElementalHeartLoss.getLost(chest, elementId);
            if (lost >= level) continue; // deja tous casses pour cet element
            deepluckyblock.util.ElementalHeartLoss.setLost(chest, elementId, lost + 1, level);

            // FIX CRITIQUE (rapporte en jeu : "une fois un coeur elementaire
            // perdu, les 3 derniers coeurs de la barre vanilla NORMALE prennent
            // BRIEVEMENT l'apparence de nos coeurs custom, sans effet, puis
            // disparaissent seulement au coup SUIVANT quand la barre se
            // rafraichit -- tres bizarre") : le compteur NBT "perdu" ci-dessus
            // est ecrit ICI, IMMEDIATEMENT, au moment exact du coup. Mais
            // l'ancien code laissait le VRAI attribut MAX_HEALTH (qui doit
            // diminuer de 2 PV pour ce coeur perdu) inchange jusqu'au PROCHAIN
            // cycle de updateElementalHearts(), qui ne tourne que tous les 10
            // ticks (0,5s, voir playerTickImpl "p.tickCount%10==0"). Pendant
            // cette fenetre de derive, ElementalHeartOverlay (qui lit le VRAI
            // MAX_HEALTH pour placer sa grille de coeurs) et le compteur
            // "perdu" (deja incremente) sont MOMENTANEMENT desynchronises :
            // le nombre total de slots de coeur affiches par vanilla contient
            // un slot de trop, que notre mixin de suppression NE VISE PAS
            // encore (il utilise, lui, la meme formule mais recalculee a
            // partir de l'etat pas encore mis a jour) -- resultat, un coeur
            // vanilla ROUGE normal se retrouve visuellement a la place qu'un
            // coeur elementaire va bientot occuper, sans jamais recevoir de
            // sprite dessus (le mixin ne le supprime pas non plus), jusqu'a
            // ce que le prochain cycle de tick rattrape tout d'un coup.
            // FIX : on retire IMMEDIATEMENT (dans le meme appel, avant tout
            // rendu) le modificateur MAX_HEALTH de CET element de 2 PV,
            // exactement comme le fera le prochain cycle de toute facon --
            // rendant les deux etats (compteur "perdu" et MAX_HEALTH reel)
            // TOUJOURS coherents entre eux, image par image, sans jamais de
            // fenetre de derive possible.
            AttributeInstance healthAttrNow = p.getAttribute(Attributes.MAX_HEALTH);
            if (healthAttrNow != null) {
                float oldMaxNow = p.getMaxHealth();
                healthAttrNow.removeModifier(ELEMENTAL_HEART_MODIFIER_IDS[i]);
                double remainingNow = Math.max(0, 2.0 * (level - (lost + 1)));
                if (remainingNow > 0) {
                    healthAttrNow.addTransientModifier(new AttributeModifier(
                            ELEMENTAL_HEART_MODIFIER_IDS[i], remainingNow, AttributeModifier.Operation.ADD_VALUE));
                }
                float newMaxNow = p.getMaxHealth();
                if (newMaxNow < oldMaxNow && p.getHealth() > newMaxNow) {
                    p.setHealth(newMaxNow);
                }
            }

            deepluckyblock.util.DebugLog.heart("{} : 1 coeur PERDU ({}/{} perdus) -- ne regenerera qu'en etant {}",
                    elementId, lost + 1, level, deepluckyblock.util.ElementalHeartConditions.describe(elementId));
            spawnHeartFx(p, elementId, false);
            // FIRE HEART (cahier des charges original) : la perte d'un coeur
            // de Feu declenche une petite explosion autour du joueur --
            // degats de bloc DESACTIVES (mobGriefing/protection du monde),
            // uniquement l'onde de choc + degats aux entites proches (jamais
            // au porteur lui-meme). Sans destruction de terrain pour rester
            // utilisable en survie normale.
            if ("fire_heart".equals(elementId) && p.level() instanceof ServerLevel fireLevel) {
                fireLevel.explode(p, p.getX(), p.getY() + p.getBbHeight() * 0.5, p.getZ(),
                        1.6f, Level.ExplosionInteraction.TRIGGER);
            }
            return elementId;
        }
        return null;
    }

    // ==================== ASK #10 : EFFETS VISUELS PERTE/GAIN DE COEUR ====================
    // Particules SERVEUR propres a chaque element (le fondu/animation HUD
    // proprement dit est cote CLIENT, voir ElementalHeartOverlay -- ceci est
    // le "punch" 3D dans le monde, complementaire au HUD 2D).
    private static void spawnHeartFx(LivingEntity p, String elementId, boolean gained) {
        if (!(p.level() instanceof ServerLevel sl)) return;
        double x = p.getX(), y = p.getY() + p.getBbHeight() * 0.5, z = p.getZ();
        int count = gained ? 18 : 24;
        double spread = 0.35;
        switch (elementId) {
            case "fire_heart" -> sl.sendParticles(gained ? ParticleTypes.FLAME : ParticleTypes.LAVA, x, y, z, count, spread, spread, spread, gained ? 0.02 : 0.0);
            case "water_heart" -> sl.sendParticles(gained ? ParticleTypes.SPLASH : ParticleTypes.BUBBLE_POP, x, y, z, count, spread, spread, spread, 0.05);
            case "earth_heart" -> sl.sendParticles(new net.minecraft.core.particles.BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()), x, y, z, count, spread, spread, spread, 0.05);
            case "wind_heart" -> sl.sendParticles(ParticleTypes.CLOUD, x, y, z, count, spread * 1.5, spread, spread * 1.5, gained ? 0.03 : 0.06);
            case "electric_heart" -> sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, count, spread, spread, spread, 0.08);
            case "shadow_heart" -> sl.sendParticles(ParticleTypes.SCULK_SOUL, x, y, z, count, spread, spread, spread, 0.02);
            case "light_heart" -> sl.sendParticles(gained ? ParticleTypes.END_ROD : ParticleTypes.WHITE_ASH, x, y, z, count, spread, spread, spread, 0.02);
            default -> sl.sendParticles(ParticleTypes.HEART, x, y, z, count, spread, spread, spread, 0.02);
        }
        // Complement sonore discret : plus aigu/joyeux au gain, plus grave au retrait.
        sl.playSound(null, p.blockPosition(),
                gained ? SoundEvents.PLAYER_LEVELUP : SoundEvents.PLAYER_HURT,
                SoundSource.PLAYERS, 0.5f, gained ? 1.4f : 0.7f);
    }

    // Dernier etat "coeurs actifs" logge par joueur (evite de reloguer a
    // chaque appel de updateElementalHearts si rien n'a change).
    private static final Map<UUID, String> LAST_LOGGED_HEARTS = new HashMap<>();

    private static void logHeartChanges(UUID uuid, String activeElementsCsv) {
        String previous = LAST_LOGGED_HEARTS.put(uuid, activeElementsCsv);
        if (activeElementsCsv.equals(previous)) return; // rien n'a change, pas de log
        Map<String, Integer> prevLevels = parseHeartCsv(previous);
        Map<String, Integer> newLevels = parseHeartCsv(activeElementsCsv);
        for (Map.Entry<String, Integer> entry : newLevels.entrySet()) {
            String heartId = entry.getKey();
            int newLvl = entry.getValue();
            int prevLvl = prevLevels.getOrDefault(heartId, 0);
            if (newLvl == prevLvl) continue;
            String label = heartId.replace("_heart", " hearts");
            deepluckyblock.util.DebugLog.heart("{} +{} (niveau {} -> {})", label, newLvl, prevLvl, newLvl);
        }
        for (Map.Entry<String, Integer> entry : prevLevels.entrySet()) {
            if (!newLevels.containsKey(entry.getKey())) {
                String label = entry.getKey().replace("_heart", " hearts");
                deepluckyblock.util.DebugLog.heart("{} retire (etait niveau {})", label, entry.getValue());
            }
        }
    }

    private static Map<String, Integer> parseHeartCsv(String csv) {
        Map<String, Integer> out = new HashMap<>();
        if (csv == null || csv.isEmpty()) return out;
        for (String part : csv.split(",")) {
            int sep = part.indexOf(':');
            if (sep <= 0) continue;
            try { out.put(part.substring(0, sep), Integer.parseInt(part.substring(sep + 1))); }
            catch (NumberFormatException ignored) { }
        }
        return out;
    }
    // ==================== ENCHANTEMENTS LIES AUX COEURS ELEMENTAIRES ====================
    // Passifs recalcules a chaque tick (comme les modificateurs plus haut) --
    // jamais persistes en NBT, donc jamais desynchronises si l'equipement
    // change. Le drain d'Umbral Pact est periodique et independant des coups
    // recus (voir tickHeartEnchantmentPeriodics).
    // FIX DOUBLE-COMPTAGE (ask #5, cf. RealAttributeModifierHandler) :
    // STONEBOUND_KNOCKBACK_ID / STONEBOUND_SPEED_ID / GALE_JUMP_ID /
    // GALE_FALL_ID / TITANS_GRASP_SPEED_ID ont ete retires -- ces cinq
    // bonus/malus (Stonebound Roots : resistance au recul + vitesse ;
    // Gale Step : saut + degats de chute ; Titan's Grasp : vitesse
    // d'attaque) sont FIXES et INCONDITIONNELS, ils sont desormais de vrais
    // AttributeModifier poses sur l'ITEM par RealAttributeModifierHandler
    // (applyArmorBonuses / applyWeaponBonuses), visibles nativement dans
    // "When on Body"/"When in Main Hand" et actifs sans avoir besoin d'un
    // recalcul par tick.

    private static void updateHeartEnchantmentPassives(LivingEntity p) {
        // FIX (meme rapport que updateElementalHearts ci-dessus) : un joueur
        // Creatif/Spectateur/invulnerable ne doit recevoir AUCUN passif lie
        // aux coeurs (bonus ou malus). Les cinq AttributeModifier autrefois
        // nettoyes ici (Stonebound Roots, Gale Step, Titan's Grasp) sont
        // desormais poses sur l'ITEM (voir FIX ci-dessus) : ils suivent donc
        // automatiquement les regles vanilla habituelles de l'equipement en
        // Creatif/Spectateur, plus rien a nettoyer manuellement ici pour eux.
        // Seuls les VRAIS effets de tick (soin, faim, effets de statut...)
        // restent a bloquer explicitement ci-dessous.
        // FIX (mob-equipment-enchant generalization) : isCreative()/isSpectator()/
        // getAbilities() n'existent que sur Player ; un mob equipe n'a aucun de ces
        // etats et doit donc simplement traverser ce garde-fou.
        if (p instanceof Player pAb && (pAb.isCreative() || pAb.isSpectator() || pAb.getAbilities().invulnerable)) return;
        // TIDAL WARD (Water Heart, bottes) : respiration aquatique + regen en nageant.
        int tidalWard = lvl(p.getItemBySlot(EquipmentSlot.FEET), "tidal_ward");
        if (tidalWard > 0) {
            p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 220, 0, true, false));
            if (p.isInWater() && p.tickCount % 40 == 0) p.heal(1.0f * tidalWard);
        }
        // WATER HEART (cahier des charges original) : bonus faim/saturation
        // tant que le coeur d'Eau est actif (pas besoin d'etre dans l'eau
        // pour ce volet-la -- c'est un bienfait "hydratation permanente" qui
        // complete, sans le remplacer, le bonus de nage de Tidal Ward
        // ci-dessus).
        int waterHeartLvl = elementalHeartLvl(p.getItemBySlot(EquipmentSlot.CHEST), "water_heart");
        // FIX (mob-equipment-enchant generalization) : la barre de faim
        // (getFoodData) est Player-only -- un mob n'en a pas, ce volet reste
        // donc reserve au joueur.
        if (waterHeartLvl > 0 && p.tickCount % 100 == 0 && p instanceof Player pFood) {
            var food = pFood.getFoodData();
            if (food.getFoodLevel() < 20) food.setFoodLevel(Math.min(20, food.getFoodLevel() + waterHeartLvl));
            food.setSaturation(Math.min(20f, food.getSaturationLevel() + 1.0f * waterHeartLvl));
        }

        // STONEBOUND ROOTS (Earth Heart, jambieres) : le volet resistance au
        // recul + vitesse est desormais un vrai AttributeModifier d'ITEM
        // (voir FIX ci-dessus) ; seule la reduction de distance de chute
        // (instantanee, au moment de l'atterrissage) reste geree ici.
        int stonebound = lvl(p.getItemBySlot(EquipmentSlot.LEGS), "stonebound_roots");
        if (stonebound > 0) p.fallDistance = Math.max(0, p.fallDistance - 1.2f * stonebound);
        // EARTH HEART (cahier des charges original, volet "combo equilibree") :
        // en plus du tradeoff anti-recul/chute de Stonebound Roots, le coeur
        // de Terre lui-meme accorde un petit bonus PERMANENT de vitesse de
        // minage tant qu'il est actif -- "enracine" = a l'aise avec la
        // pierre. La reduction de degats est geree cote combat (voir
        // UniversalEnchantmentHandler#handleDefensiveHurt, earthHeartLvl).
        int earthHeartLvl = elementalHeartLvl(p.getItemBySlot(EquipmentSlot.CHEST), "earth_heart");
        if (earthHeartLvl > 0) {
            p.addEffect(new MobEffectInstance(MobEffects.DIG_SPEED, 30, Math.min(2, earthHeartLvl - 1), true, false));
        }

        // LIGHT HEART (cahier des charges original, volets "repousse les
        // mobs" + "purge des effets negatifs") : independant de Radiant
        // Ward (enchantement de casque separe, plus haut) qui gere deja
        // l'annulation de degats -- ici c'est le coeur LUI-MEME qui
        // eloigne doucement les monstres proches et purge les effets
        // negatifs courants, tant qu'il est actif.
        int lightHeartPassiveLvl = elementalHeartLvl(p.getItemBySlot(EquipmentSlot.CHEST), "light_heart");
        if (lightHeartPassiveLvl > 0) {
            if (p.tickCount % 40 == 0 && p.level() instanceof ServerLevel lightLevel) {
                double radius = 3.0 + 1.5 * lightHeartPassiveLvl;
                for (net.minecraft.world.entity.monster.Monster monster : p.level().getEntitiesOfClass(net.minecraft.world.entity.monster.Monster.class, p.getBoundingBox().inflate(radius))) {
                    if (!monster.isAlive()) continue;
                    net.minecraft.world.phys.Vec3 away = monster.position().subtract(p.position());
                    double horiz = away.horizontalDistance();
                    if (horiz < 0.01) continue;
                    monster.setDeltaMovement(monster.getDeltaMovement().add(
                            away.x / horiz * 0.10 * lightHeartPassiveLvl, 0.02, away.z / horiz * 0.10 * lightHeartPassiveLvl));
                    monster.hurtMarked = true;
                }
                lightLevel.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + p.getBbHeight() * 0.5, p.getZ(),
                        6, 0.4, 0.4, 0.4, 0.01);
            }
            if (p.tickCount % 100 == 0) {
                p.removeEffect(MobEffects.POISON);
                p.removeEffect(MobEffects.WEAKNESS);
                if (lightHeartPassiveLvl >= 2) p.removeEffect(MobEffects.WITHER);
                if (lightHeartPassiveLvl >= 3) p.removeEffect(MobEffects.BLINDNESS);
            }
        }

        // GALE STEP (Wind Heart, bottes) : le bonus de saut + la reduction
        // des degats de chute sont desormais de vrais AttributeModifier
        // d'ITEM (voir FIX ci-dessus, RealAttributeModifierHandler) --
        // plus rien a recalculer par tick pour cet enchantement. On garde
        // uniquement la lecture du niveau (variable "gale"), necessaire plus
        // bas pour le comptage "activeElements" de Heart Resonance.
        int gale = lvl(p.getItemBySlot(EquipmentSlot.FEET), "gale_step");

        // T306-FIX : Malédiction du Courant (riptide_curse, jambières) -> respiration aquatique + grâce du dauphin dans l'eau
        int riptideCurse = lvl(p.getItemBySlot(EquipmentSlot.LEGS), "riptide_curse");
        if (riptideCurse > 0) {
            p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 40, 0, true, false));
            if (p.isInWater()) {
                p.addEffect(new MobEffectInstance(MobEffects.DOLPHINS_GRACE, 30, Math.min(2, riptideCurse - 1), true, false));
                if (p.tickCount % 20 == 0) EnchantmentParticleFX.ankleTrail(p, ParticleTypes.BUBBLE_COLUMN_UP, 3, 0.04);
            }
        }

        // VOIDLING'S HUNGER (Shadow Heart, casque, MAUDIT)
        int voidHunger = lvl(p.getItemBySlot(EquipmentSlot.HEAD), "voidlings_hunger");
        if (voidHunger > 0 && p.level().isDay() && p.level().canSeeSky(p.blockPosition()) && !p.level().isClientSide()) {
            if (p.tickCount % 60 == 0 && p.getHealth() > 1.0f) {
                p.hurt(p.damageSources().magic(), 0.5f + 0.25f * voidHunger);
            }
        }

        // T306-FIX : HEART RESONANCE prend en compte les 7 cœurs + TOUS les pactes élémentaires (vagues 1 et 2)
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        int resonance = lvl(chest, "heart_resonance");
        if (resonance > 0) {
            int activeElements = countActiveElementalSynergies(p, chest, tidalWard, stonebound, gale, riptideCurse, voidHunger);
            if (activeElements >= 3) {
                if (p.tickCount % 60 == 0) {
                    p.heal(1.0f);
                    EnchantmentParticleFX.crownHalo(p, ParticleTypes.END_ROD, 6);
                }
                p.setAbsorptionAmount(Math.max(p.getAbsorptionAmount(), 2.0f * Math.min(4, activeElements - 2)));
            }
        }
    }

    private static int countActiveElementalSynergies(LivingEntity p, ItemStack chest,
            int tidalWard, int stonebound, int gale, int riptideCurse, int voidHunger) {
        ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack legs = p.getItemBySlot(EquipmentSlot.LEGS);
        ItemStack feet = p.getItemBySlot(EquipmentSlot.FEET);
        ItemStack main = p.getMainHandItem();
        int count = 0;
        if (lvl(chest, "fire_heart") > 0 || lvl(head, "ember_core") > 0 || lvl(chest, "salamanders_vow") > 0) count++;
        if (lvl(chest, "water_heart") > 0 || tidalWard > 0 || riptideCurse > 0) count++;
        if (lvl(chest, "earth_heart") > 0 || stonebound > 0 || lvl(main, "titans_grasp") > 0) count++;
        if (lvl(chest, "wind_heart") > 0 || gale > 0 || lvl(feet, "zephyrs_gambit") > 0) count++;
        if (lvl(chest, "electric_heart") > 0 || lvl(main, "overcharge") > 0 || lvl(main, "thundercall_pact") > 0) count++;
        if (lvl(chest, "light_heart") > 0 || lvl(head, "radiant_ward") > 0 || lvl(main, "seraphs_judgment") > 0) count++;
        if (lvl(chest, "shadow_heart") > 0 || lvl(legs, "umbral_pact") > 0 || voidHunger > 0) count++;
        if (lvl(feet, "chimeras_whim") > 0) count++;
        return count;
    }

    // UMBRAL PACT : drain periodique independant des coups recus (toutes les ~10s)
    private static void tickHeartEnchantmentPeriodics(LivingEntity p) {
        if (p instanceof Player pl && (pl.isCreative() || pl.isSpectator() || pl.getAbilities().invulnerable)) return;
        int umbralPact = lvl(p.getItemBySlot(EquipmentSlot.LEGS), "umbral_pact");
        if (umbralPact <= 0) return;
        if (p.tickCount % 200 != 0) return;
        if (p.getHealth() <= 2f) return;
        guardedHurt(p, p.damageSources().magic(), 1f);
        p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 60 + 20 * umbralPact, 0, true, false));
        if (p.level() instanceof ServerLevel sl) {
            sl.sendParticles(ParticleTypes.SQUID_INK, p.getX(), p.getY() + p.getBbHeight() * 0.5, p.getZ(),
                    20, 0.4, 0.5, 0.4, 0.02);
        }
    }

    // T306-FIX : Caprice de la Chimère puise parmi les éléments ACTIFS équipés (les 7 thèmes, Lumière incluse !),
    // avec repli sur les 7 éléments si aucun autre thème élémentaire n'est équipé.
    private static void triggerChimeraWhim(LivingEntity p) {
        if (!(p.level() instanceof ServerLevel level)) return;
        ItemStack head = p.getItemBySlot(EquipmentSlot.HEAD);
        ItemStack chest = p.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack legs = p.getItemBySlot(EquipmentSlot.LEGS);
        ItemStack feet = p.getItemBySlot(EquipmentSlot.FEET);
        ItemStack main = p.getMainHandItem();
        int[] pool = new int[7];
        int poolLen = 0;
        if (lvl(chest, "fire_heart") > 0 || lvl(head, "ember_core") > 0 || lvl(chest, "salamanders_vow") > 0) pool[poolLen++] = 0;
        if (lvl(chest, "water_heart") > 0 || lvl(chest, "tidal_ward") > 0 || lvl(legs, "riptide_curse") > 0) pool[poolLen++] = 1;
        if (lvl(chest, "earth_heart") > 0 || lvl(legs, "stonebound_roots") > 0 || lvl(main, "titans_grasp") > 0) pool[poolLen++] = 2;
        if (lvl(chest, "wind_heart") > 0 || lvl(feet, "gale_step") > 0 || lvl(feet, "zephyrs_gambit") > 0) pool[poolLen++] = 3;
        if (lvl(chest, "electric_heart") > 0 || lvl(main, "overcharge") > 0 || lvl(main, "thundercall_pact") > 0) pool[poolLen++] = 4;
        if (lvl(chest, "shadow_heart") > 0 || lvl(legs, "umbral_pact") > 0 || lvl(head, "voidlings_hunger") > 0) pool[poolLen++] = 5;
        if (lvl(chest, "light_heart") > 0 || lvl(head, "radiant_ward") > 0 || lvl(main, "seraphs_judgment") > 0) pool[poolLen++] = 6;
        RandomSource rand = p.level().random;
        int roll = poolLen > 0 ? pool[rand.nextInt(poolLen)] : rand.nextInt(7);
        switch (roll) {
            case 0 -> { // Fire
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, 60, 0, true, true));
                level.sendParticles(ParticleTypes.FLAME, p.getX(), p.getY() + 0.2, p.getZ(), 12, 0.3, 0.2, 0.3, 0.02);
            }
            case 1 -> { // Water
                p.addEffect(new MobEffectInstance(MobEffects.WATER_BREATHING, 200, 0, true, true));
                level.sendParticles(ParticleTypes.SPLASH, p.getX(), p.getY() + 0.2, p.getZ(), 12, 0.3, 0.2, 0.3, 0.02);
            }
            case 2 -> { // Earth
                p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 60, 0, true, true));
                level.sendParticles(ParticleTypes.CRIT, p.getX(), p.getY() + 0.2, p.getZ(), 12, 0.3, 0.2, 0.3, 0.02);
            }
            case 3 -> { // Wind
                p.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 100, 1, true, true));
                level.sendParticles(ParticleTypes.CLOUD, p.getX(), p.getY() + 0.2, p.getZ(), 12, 0.3, 0.2, 0.3, 0.02);
            }
            case 4 -> { // Electricity
                p.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, true, true));
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.getX(), p.getY() + 0.2, p.getZ(), 12, 0.3, 0.2, 0.3, 0.02);
            }
            case 5 -> { // Shadow
                p.addEffect(new MobEffectInstance(MobEffects.INVISIBILITY, 30, 0, true, true));
                level.sendParticles(ParticleTypes.SMOKE, p.getX(), p.getY() + 0.2, p.getZ(), 12, 0.3, 0.2, 0.3, 0.02);
            }
            default -> { // Light
                p.addEffect(new MobEffectInstance(MobEffects.REGENERATION, 50, 0, true, true));
                level.sendParticles(ParticleTypes.END_ROD, p.getX(), p.getY() + 0.2, p.getZ(), 12, 0.3, 0.2, 0.3, 0.02);
            }
        }
    }

    private static int armor(LivingEntity e,String id){int m=0;for(ItemStack s:e.getArmorSlots())m=Math.max(m,lvl(s,id));return m;}private static int shield(LivingEntity p,String id){int a=lvl(p.getUseItem(),id);if(a==0)a=lvl(p.getOffhandItem(),id);return a;}
    private static int arrowLvl(AbstractArrow arrow,ItemStack held,String id){int tagged=arrow.getPersistentData().getInt("slb_"+id);return tagged>0?tagged:lvl(held,id);}
    // FILET DE SECURITE niveau force (commande/lucky block/NBT) : au-dela du
    // cap normal de l'enchantement, softCappedLevel() applique un rendement
    // decroissant (racine carree) au lieu de laisser une formule lineaire
    // non plafonnee produire des valeurs absurdes a tres haut niveau (ex.
    // 1000). En jeu normal (niveau <= cap), AUCUN changement de comportement.
    private static int lvl(ItemStack s,String id){if(s==null||s.isEmpty())return 0;Enchantment e=deepluckyblock.util.DeepEnchant.byId("deep_lucky_block:"+id);if(e==null)return 0;int l=deepluckyblock.util.DeepEnchant.getLevel(s,e);if(l<=0)return 0;if(e.getMaxLevel()<=1)return 1;return deepluckyblock.util.DeepEnchant.softCappedLevel(e,l);}

    /**
     * Niveau EFFECTIF (pouvoir ACTIF, pas le bonus de PV) d'un des 7 cœurs
     * élémentaires à condition de régénération, une fois déduits les cœurs
     * actuellement "perdus" sur CE plastron (voir {@link
     * deepluckyblock.util.ElementalHeartLoss}). Correctif audit demande
     * utilisateur #3 : avant ce correctif, les volets passifs par tick
     * (faim/saturation de l'Eau, vitesse de minage de la Terre, répulsion
     * des mobs + purge d'effets négatifs de la Lumière) restaient à pleine
     * puissance même après la perte réelle d'un ou plusieurs cœurs de cet
     * élément. Ne PAS utiliser pour fire_heart : sa perte de cœur DÉCLENCHE
     * elle-même l'explosion (voir breakOneElementalHeart) et n'a pas de
     * pouvoir passif continu à réduire.
     */
    private static int elementalHeartLvl(ItemStack chest, String elementId) {
        int raw = lvl(chest, elementId);
        if (raw <= 0) return 0;
        return deepluckyblock.util.ElementalHeartLoss.effectiveLevel(chest, elementId, raw);
    }
    private static void guardedHurt(LivingEntity t,net.minecraft.world.damagesource.DamageSource s,float a){if(a<=0||!t.isAlive())return;try{DAMAGE_GUARD.set(true);t.hurt(s,a);}finally{DAMAGE_GUARD.set(false);}}
    // FIX (mob-equipment-enchant generalization) : DamageSources.playerAttack
    // exige un Player ; l'equivalent generique mobAttack(LivingEntity)
    // fonctionne pour n'importe quel attaquant (y compris un Player).
    private static net.minecraft.world.damagesource.DamageSource atkSrc(LivingEntity attacker){return attacker instanceof Player p?attacker.damageSources().playerAttack(p):attacker.damageSources().mobAttack(attacker);}

    // V37 - un effet en erreur ne doit jamais bloquer les suivants
    private static final java.util.Set<String> REPORTED__ = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private static void guard(String where, Throwable t) {
        if (REPORTED__.add(where + ':' + t.getClass().getName())) {
            com.mojang.logging.LogUtils.getLogger().error(
                "[deep_lucky_block] Erreur dans EquipmentExpansionHandler#{} - effet ignore.", where, t);
        }
    }

}
