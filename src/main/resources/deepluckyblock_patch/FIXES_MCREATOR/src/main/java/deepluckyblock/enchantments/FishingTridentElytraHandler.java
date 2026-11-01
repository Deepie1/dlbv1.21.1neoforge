package deepluckyblock.enchantments;

import deepluckyblock.procedures.TestProcedure;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Drowned;
import net.minecraft.world.entity.monster.Guardian;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.entity.projectile.ThrownTrident;
import net.minecraft.world.item.*;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.ItemFishedEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;

import java.util.*;

import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Runtime for fishing rod, trident and elytra expansion enchantments. */
@EventBusSubscriber(modid = "deep_lucky_block")
public final class FishingTridentElytraHandler {
    private FishingTridentElytraHandler(){}
    private static final Map<UUID,ThrownTrident> TRACKED_TRIDENTS=new HashMap<>();
    // FIX DOUBLE-COMPTAGE (ask #5) : IRON_WINGS_ARMOR (ancien identifiant du
    // modificateur d'Armor pose sur le JOUEUR a chaque tick) a ete retire --
    // ce bonus fixe et inconditionnel est desormais un vrai AttributeModifier
    // pose sur l'ITEM par RealAttributeModifierHandler#applyElytraBonuses,
    // visible nativement dans "When on Body".
    private static final String[] TRIDENT_IDS={"homing_trident","hydro_shock","sincere_loyalty","scorching_prongs","wind_lance","harpoon","undertow","chain_lightning","lingering_skewer","tidecaller","sea_pogo","explosive_return","hostile_boomerang_curse","lightning_rod_curse","depth_curse"};

    // ================= FISHING ROD =================
    @SubscribeEvent public static void reel(PlayerInteractEvent.RightClickItem e){try{reelImpl(e);}catch(Throwable t){guard("reel",t);}}
    private static void reelImpl(PlayerInteractEvent.RightClickItem e){Player p=e.getEntity();ItemStack rod=e.getItemStack();if(!(rod.getItem() instanceof FishingRodItem)||p.fishing==null)return;FishingHook hook=p.fishing;Entity caught=hook.getHookedIn();if(caught!=null){Entity pulled=caught.getRootVehicle();int pull=lvl(rod,"pulling_power");if(pull>0){net.minecraft.world.phys.Vec3 direction=p.position().subtract(pulled.position());double horizontal=Math.sqrt(direction.x*direction.x+direction.z*direction.z);if(horizontal>.01){double force=deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.velocity(.40,.32,pull);if(pulled instanceof Player)force*=.5;if(pulled.getType().getCategory()==MobCategory.MONSTER&&pulled.getBbWidth()>2)force*=.35;pulled.setDeltaMovement(direction.x/horizontal*force,.12+.05*pull,direction.z/horizontal*force);pulled.hurtMarked=true;if(p.level() instanceof ServerLevel sl)EnchantmentParticleFX.tetherBeam(sl,p.getEyePosition(),pulled.position().add(0,pulled.getBbHeight()*.5,0),ParticleTypes.SPLASH,ParticleTypes.BUBBLE_POP,.75);}}int barbed=lvl(rod,"barbed_hook");if(barbed>0&&caught instanceof LivingEntity living){living.hurt(p.damageSources().playerAttack(p),2f+2.25f*barbed);EnchantmentParticleFX.burstOnEntity(living,EnchantmentParticleFX.CRIMSON_BLOOD,8,.25,.02);}int thunder=lvl(rod,"thunder_hook");if(thunder>0&&p.level() instanceof ServerLevel sl&&sl.random.nextInt(100)<Math.min(100,12*thunder*(sl.isThundering()?2:1))){LightningBolt bolt=EntityType.LIGHTNING_BOLT.create(sl);if(bolt!=null){bolt.moveTo(caught.position());sl.addFreshEntity(bolt);}EnchantmentParticleFX.shockwaveRing(sl,caught.getX(),caught.getY()+.15,caught.getZ(),ParticleTypes.ELECTRIC_SPARK,1.6,14,.08);}}
        if(caught==null&&hook.onGround()){int grapple=lvl(rod,"fishing_grapple");if(grapple>0){net.minecraft.world.phys.Vec3 delta=hook.position().subtract(p.position());if(delta.length()<=24+12*grapple){if(p.level() instanceof ServerLevel sl)EnchantmentParticleFX.tetherBeam(sl,p.getEyePosition(),hook.position(),ParticleTypes.CRIT,ParticleTypes.CLOUD,.65);p.setDeltaMovement(delta.normalize().scale(deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.velocity(.825,.30,grapple)).add(0,.18,0));p.fallDistance=0;p.hurtMarked=true;}}int ender=lvl(rod,"ender_hook");long ready=p.getPersistentData().getLong("slb_ender_hook_ready");if(ender>0&&p.level().getGameTime()>=ready){BlockPos q=hook.blockPosition();if(p.level().getBlockState(q).isAir()&&p.level().getBlockState(q.above()).isAir()){if(p.level() instanceof ServerLevel sl)EnchantmentParticleFX.burstOnEntity(p,ParticleTypes.REVERSE_PORTAL,14,.35,.06);p.teleportTo(q.getX()+.5,q.getY(),q.getZ()+.5);EnchantmentParticleFX.doubleHelix(p,ParticleTypes.REVERSE_PORTAL,EnchantmentParticleFX.SHADOW_VIOLET,.55,10);p.getFoodData().addExhaustion(2f);p.getPersistentData().putLong("slb_ender_hook_ready",p.level().getGameTime()+60);}}}}

    @SubscribeEvent public static void fished(ItemFishedEvent e){try{fishedImpl(e);}catch(Throwable t){guard("fished",t);}}
    private static void fishedImpl(ItemFishedEvent e){Player p=e.getEntity();ItemStack rod=heldRod(p);if(rod.isEmpty())return;RandomSource r=p.getRandom();int falseBite=lvl(rod,"false_bite_curse");if(falseBite>0&&r.nextInt(100)<Math.min(90,12*falseBite)){e.setCanceled(true);e.damageRodBy(Math.max(1,e.getRodDamage()));p.level().playSound(null,p.blockPosition(),SoundEvents.FISHING_BOBBER_SPLASH,SoundSource.PLAYERS,1,.7f);return;}List<ItemStack> drops=e.getDrops();int reliability=lvl(rod,"reliability");if(reliability>0)for(ItemStack stack:drops)if(stack.isDamageableItem())stack.setDamageValue(Math.min(stack.getDamageValue(),(int)(stack.getMaxDamage()*Math.max(0,.4-.3*(reliability-1)))));int dredge=lvl(rod,"dredge");if(dredge>0&&!drops.isEmpty()&&r.nextInt(100)<Math.min(85,12*dredge+6))spawn(p,drops.get(r.nextInt(drops.size())).copy());
        // FIX (bug confirmé en jeu, ask #4 : "donne le poisson cru ET cuit
        // en même temps") : la version précédente APPELAIT spawn() pour
        // ajouter la version cuite SANS jamais retirer/remplacer le poisson
        // cru original dans `drops` -- le joueur recevait donc les deux
        // piles (le poisson cru livré normalement par ItemFishedEvent, PLUS
        // la version cuite ajoutée en supplément). Fix : on remplace
        // directement l'entrée cruecrue de la liste par sa version cuite,
        // via ListIterator#set (évite toute ConcurrentModificationException
        // pendant l'itération, contrairement à un for-each qui modifierait
        // `drops` en le parcourant) -- une seule pile est désormais reçue,
        // uniquement la version cuite, exactement l'effet annoncé
        // ("Cod and salmon catches also provide their cooked version").
        int cooked=lvl(rod,"cooked_catch");
        if(cooked>0){
            boolean anyCooked=false;
            ListIterator<ItemStack> it=drops.listIterator();
            while(it.hasNext()){
                ItemStack stack=it.next();
                if(stack.is(Items.COD)){it.set(new ItemStack(Items.COOKED_COD,stack.getCount()));anyCooked=true;}
                else if(stack.is(Items.SALMON)){it.set(new ItemStack(Items.COOKED_SALMON,stack.getCount()));anyCooked=true;}
            }
            if(anyCooked&&e.getHookEntity()!=null&&p.level() instanceof ServerLevel sl){
                EnchantmentParticleFX.burstOnEntity(e.getHookEntity(),ParticleTypes.SMALL_FLAME,8,.2,.02);
                EnchantmentParticleFX.burstOnEntity(e.getHookEntity(),ParticleTypes.SMOKE,5,.15,.01);
            }
        }int blessing=lvl(rod,"anglers_blessing");if(blessing>0)for(ItemStack stack:drops)if((stack.has(net.minecraft.core.component.DataComponents.FOOD))&&r.nextInt(100)<Math.min(75,8*blessing))spawn(p,stack.copy());int experienced=lvl(rod,"experienced_fishing");if(experienced>0){int base=Math.max(1,drops.stream().mapToInt(ItemStack::getCount).sum());p.giveExperiencePoints(Math.round(base*.375f*experienced));}int monster=lvl(rod,"monster_fishing");if(monster>0&&r.nextInt(100)<deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.percent(3,monster,90)&&p.level() instanceof ServerLevel sl)spawnFishingMonster(sl,e.getHookEntity().position(),monster,false);int abyss=lvl(rod,"abyss_curse");if(abyss>0&&r.nextInt(100)<Math.min(60,6*abyss+2)&&p.level() instanceof ServerLevel sl)spawnFishingMonster(sl,e.getHookEntity().position(),abyss,true);int scorch=lvl(rod,"scorching_line_curse");if(scorch>0&&r.nextInt(100)<Math.min(90,15*scorch)){if(p.isInWater())p.setAirSupply(Math.max(0,p.getAirSupply()-40*scorch));else p.setRemainingFireTicks((3*scorch) * 20);} }

    // Manual lava catches for Infernal Line; no vanilla loot table is replaced.
    @SubscribeEvent public static void fishingTick(PlayerTickEvent.Post e){try{fishingTickImpl(e);}catch(Throwable t){guard("fishingTick",t);}}
    private static void fishingTickImpl(PlayerTickEvent.Post e){if(e.getEntity().level().isClientSide())return;Player p=e.getEntity();fixStuckLure(p);ItemStack rod=heldRod(p);if(p.fishing!=null&&lvl(rod,"infernal_line")>0&&p.fishing.isInLava()&&p.tickCount%40==0&&p.getRandom().nextInt(100)<18){ItemStack loot=switch(p.getRandom().nextInt(6)){case 0->new ItemStack(Items.QUARTZ,1+p.getRandom().nextInt(3));case 1->new ItemStack(Items.GOLD_NUGGET,2+p.getRandom().nextInt(5));case 2->new ItemStack(Items.MAGMA_CREAM);case 3->new ItemStack(Items.BLAZE_POWDER);case 4->new ItemStack(Items.OBSIDIAN);default->p.getRandom().nextInt(200)==0?new ItemStack(Items.ANCIENT_DEBRIS):new ItemStack(Items.COOKED_COD);};if(p.level() instanceof ServerLevel sl)EnchantmentParticleFX.shockwaveRing(sl,p.fishing.getX(),p.fishing.getY()+.1,p.fishing.getZ(),ParticleTypes.LAVA,1.0,10,.04);spawn(p,loot);p.fishing.discard();p.fishing=null;}ItemStack trident=p.getMainHandItem().is(Items.TRIDENT)?p.getMainHandItem():p.getOffhandItem();int depth=lvl(trident,"depth_curse");if(depth>0&&p.isInWater()){p.setDeltaMovement(p.getDeltaMovement().add(0,-.025*depth,0));if(p.tickCount%20==0)p.setAirSupply(Math.max(0,p.getAirSupply()-10*depth));}}

    // ================= TRIDENT =================
    @SubscribeEvent public static void tridentRelease(LivingEntityUseItemEvent.Stop e){try{tridentReleaseImpl(e);}catch(Throwable t){guard("tridentRelease",t);}}
    private static void tridentReleaseImpl(LivingEntityUseItemEvent.Stop e){if(!(e.getEntity() instanceof LivingEntity p)||!e.getItem().is(Items.TRIDENT))return;for(String id:TRIDENT_IDS){int l=lvl(e.getItem(),id);if(l>0)p.getPersistentData().putInt("slb_pending_"+id,l);}}
    @SubscribeEvent public static void tridentJoin(EntityJoinLevelEvent e){try{tridentJoinImpl(e);}catch(Throwable t){guard("tridentJoin",t);}}
    private static void tridentJoinImpl(EntityJoinLevelEvent e){if(e.getLevel().isClientSide()||!(e.getEntity() instanceof ThrownTrident t)||!(t.getOwner() instanceof LivingEntity p))return;for(String id:TRIDENT_IDS){int l=p.getPersistentData().getInt("slb_pending_"+id);if(l>0){t.getPersistentData().putInt("slb_"+id,l);p.getPersistentData().remove("slb_pending_"+id);}}TRACKED_TRIDENTS.put(p.getUUID(),t);int curse=t.getPersistentData().getInt("slb_lightning_rod_curse");if(curse>0&&e.getLevel() instanceof ServerLevel sl&&sl.isRaining()&&sl.random.nextInt(100)<Math.min(90,8*curse)){LightningBolt b=EntityType.LIGHTNING_BOLT.create(sl);if(b!=null){b.moveTo(p.position());sl.addFreshEntity(b);}}if(t.getPersistentData().getInt("slb_homing_trident")>0&&e.getLevel() instanceof ServerLevel sl)homeTrident(sl,t);trackReturn((ServerLevel)e.getLevel(),t,p.getUUID());}
    private static void homeTrident(ServerLevel level,ThrownTrident t){if(!t.isAlive())return;int l=t.getPersistentData().getInt("slb_homing_trident");double radius=9+6*l;LivingEntity best=null;double dist=Double.MAX_VALUE;for(LivingEntity x:level.getEntitiesOfClass(LivingEntity.class,t.getBoundingBox().inflate(radius),x->x!=t.getOwner()&&x.isAlive())){double d=x.distanceToSqr(t);if(d<dist){dist=d;best=x;}}if(best!=null){net.minecraft.world.phys.Vec3 v=best.getEyePosition().subtract(t.position()).normalize();double speed=Math.max(.8,t.getDeltaMovement().length());t.setDeltaMovement(t.getDeltaMovement().scale(.82).add(v.scale(speed*.18)));if(t.tickCount%3==0)level.sendParticles(ParticleTypes.NAUTILUS,t.getX(),t.getY(),t.getZ(),1,.03,.03,.03,0);}TestProcedure.schedule(level,TestProcedure.currentTick(level)+1,()->homeTrident(level,t));}
    private static void trackReturn(ServerLevel level,ThrownTrident t,UUID ownerId){if(!t.isAlive()){TRACKED_TRIDENTS.remove(ownerId);return;}ServerPlayer owner=level.getServer().getPlayerList().getPlayer(ownerId);if(owner!=null&&t.distanceToSqr(owner)<4&&!t.getPersistentData().getBoolean("slb_return_effect_done")){t.getPersistentData().putBoolean("slb_return_effect_done",true);int boom=t.getPersistentData().getInt("slb_explosive_return");if(boom>0){double rad=deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.radius(0,2.5,boom);EnchantmentParticleFX.shockwaveRing(level,owner.getX(),owner.getY()+.2,owner.getZ(),ParticleTypes.GUST,rad,16,.06);for(Mob m:level.getEntitiesOfClass(Mob.class,owner.getBoundingBox().inflate(rad)))m.setDeltaMovement(m.position().subtract(owner.position()).normalize().scale(deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.velocity(0,.6,boom)));}int curse=t.getPersistentData().getInt("slb_hostile_boomerang_curse");if(curse>0&&level.random.nextInt(100)<deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.percent(12,curse,90))owner.hurt(owner.damageSources().trident(t,owner),Math.max(1f,(float)(t.getBaseDamage()*.3)));}TestProcedure.schedule(level,TestProcedure.currentTick(level)+2,()->trackReturn(level,t,ownerId));}
    @SubscribeEvent public static void tridentImpact(ProjectileImpactEvent e){try{tridentImpactImpl(e);}catch(Throwable t){guard("tridentImpact",t);}}
    private static void tridentImpactImpl(ProjectileImpactEvent e){if(!(e.getProjectile() instanceof ThrownTrident t)||!(t.getOwner() instanceof LivingEntity p)||p.level().isClientSide())return;net.minecraft.world.phys.Vec3 at=e.getRayTraceResult().getLocation();ServerLevel sl=(p.level() instanceof ServerLevel s)?s:null;if(e.getRayTraceResult() instanceof EntityHitResult hit&&hit.getEntity() instanceof LivingEntity target){int hydro=tag(t,"hydro_shock");if(hydro>0&&(target.isInWater()||p.level().isRainingAt(target.blockPosition())||target.isOnFire())){target.hurt(p.damageSources().trident(t,p),target.getMaxHealth()*.03f*hydro);if(sl!=null)EnchantmentParticleFX.shockwaveRing(sl,target.getX(),target.getY()+.25,target.getZ(),ParticleTypes.SPLASH,1.8,16,.12);if(p.getRandom().nextInt(100)<deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.percent(8,hydro,90)&&sl!=null){LightningBolt b=EntityType.LIGHTNING_BOLT.create(sl);if(b!=null){b.moveTo(target.position());sl.addFreshEntity(b);}}}int fire=tag(t,"scorching_prongs");if(fire>0){target.setRemainingFireTicks((3*fire) * 20);EnchantmentParticleFX.burstOnEntity(target,ParticleTypes.FLAME,12,.3,.04);}int harpoon=tag(t,"harpoon");if(harpoon>0){net.minecraft.world.phys.Vec3 d=p.position().subtract(target.position()).normalize();double f=deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.velocity(.40,.32,harpoon);target.setDeltaMovement(d.x*f,.12+.05*harpoon,d.z*f);target.hurtMarked=true;if(sl!=null)EnchantmentParticleFX.tetherBeam(sl,p.getEyePosition(),target.position().add(0,target.getBbHeight()*.5,0),ParticleTypes.BUBBLE_POP,ParticleTypes.SPLASH,.7);}int linger=tag(t,"lingering_skewer");if(linger>0&&sl!=null)for(int i=1,n__=deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.count(1,linger);i<=n__;i++){int delay=i*20;TestProcedure.schedule(sl,TestProcedure.currentTick(sl)+delay,()->{if(target.isAlive()){target.hurt(p.damageSources().trident(t,p),2f);EnchantmentParticleFX.burstOnEntity(target,EnchantmentParticleFX.CRIMSON_BLOOD,6,.2,.02);}});}int chain=tag(t,"chain_lightning");if(chain>0&&p.level().isRaining()){int n=0;for(Mob m:p.level().getEntitiesOfClass(Mob.class,target.getBoundingBox().inflate(6))){if(m==target||n++>=Math.min(24,1+chain))continue;m.hurt(p.damageSources().lightningBolt(),4f);if(sl!=null)EnchantmentParticleFX.tetherBeam(sl,target.position().add(0,target.getBbHeight()*.5,0),m.position().add(0,m.getBbHeight()*.5,0),ParticleTypes.ELECTRIC_SPARK,EnchantmentParticleFX.OVERCLOCK_CYAN,.5);}}}
        int wind=tag(t,"wind_lance");if(wind>0){double wr=deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.radius(0,2.5,wind);if(sl!=null)EnchantmentParticleFX.shockwaveRing(sl,at.x,at.y+.15,at.z,ParticleTypes.SMALL_GUST,wr,16,.08);for(LivingEntity x:p.level().getEntitiesOfClass(LivingEntity.class,new AABB(at,at).inflate(wr),x->x!=p)){net.minecraft.world.phys.Vec3 d=x.position().subtract(at).normalize();x.setDeltaMovement(x.getDeltaMovement().add(d.scale(deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.velocity(0,.9,wind))).add(0,.15*wind,0));x.hurtMarked=true;}}int undertow=tag(t,"undertow");if(undertow>0){double ur=deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.radius(0,2.5,undertow);if(sl!=null)EnchantmentParticleFX.shockwaveRing(sl,at.x,at.y+.2,at.z,ParticleTypes.CURRENT_DOWN,ur,16,-.06);for(LivingEntity x:p.level().getEntitiesOfClass(LivingEntity.class,new AABB(at,at).inflate(ur),x->x!=p&&x.isInWater()))x.setDeltaMovement(x.getDeltaMovement().add(at.subtract(x.position()).normalize().scale(.3*undertow)));}if(e.getRayTraceResult() instanceof BlockHitResult bh){int tide=tag(t,"tidecaller");if(tide>0&&sl!=null){BlockPos c=bh.getBlockPos().relative(bh.getDirection());EnchantmentParticleFX.shockwaveRing(sl,c.getX()+.5,c.getY()+.2,c.getZ()+.5,ParticleTypes.SPLASH,1.2,12,.08);if(sl.getBlockState(c).isAir()){sl.setBlockAndUpdate(c,Blocks.WATER.defaultBlockState());TestProcedure.schedule(sl,TestProcedure.currentTick(sl)+40+20*tide,()->{if(sl.getBlockState(c).is(Blocks.WATER))sl.setBlockAndUpdate(c,Blocks.AIR.defaultBlockState());});}}int pogo=tag(t,"sea_pogo");if(pogo>0&&p.fallDistance>0){p.setDeltaMovement(p.getDeltaMovement().x,.55+.25*pogo,p.getDeltaMovement().z);p.fallDistance=0;p.hurtMarked=true;if(sl!=null)EnchantmentParticleFX.shockwaveRing(sl,p.getX(),p.getY()+.1,p.getZ(),ParticleTypes.BUBBLE_COLUMN_UP,1.2,12,.05);}}}

    @SubscribeEvent public static void recall(PlayerInteractEvent.RightClickEmpty e){try{recallImpl(e);}catch(Throwable t){guard("recall",t);}}
    private static void recallImpl(PlayerInteractEvent.RightClickEmpty e){Player p=e.getEntity();ThrownTrident t=TRACKED_TRIDENTS.get(p.getUUID());if(t!=null&&t.isAlive()&&tag(t,"sincere_loyalty")>0&&t.level()==p.level()){t.setDeltaMovement(p.getEyePosition().subtract(t.position()).normalize().scale(2.2));}}

    // ================= ELYTRA =================
    @SubscribeEvent public static void elytraTick(PlayerTickEvent.Post e){try{elytraTickImpl(e);}catch(Throwable t){guard("elytraTick",t);}}
    private static void elytraTickImpl(PlayerTickEvent.Post e){if(e.getEntity().level().isClientSide())return;Player p=e.getEntity();ItemStack wings=p.getItemBySlot(EquipmentSlot.CHEST);if(!wings.is(Items.ELYTRA)){return;}int winging=lvl(wings,"winging");if(winging>0&&!p.isFallFlying()&&!p.isShiftKeyDown()&&!p.isInWater()&&p.fallDistance>=4&&wings.getDamageValue()<wings.getMaxDamage()-1)p.startFallFlying();if(p.isFallFlying()){net.minecraft.world.phys.Vec3 v=p.getDeltaMovement();double speed=v.length();int aero=lvl(wings,"aerodynamic");if(aero>0&&speed<3.5){p.setDeltaMovement(v.scale(1+.0025*aero));if(p.tickCount%8==0)EnchantmentParticleFX.wingRibbons(p,ParticleTypes.WHITE_SMOKE);}int brake=lvl(wings,"air_brake");if(brake>0&&p.isShiftKeyDown())p.setDeltaMovement(p.getDeltaMovement().scale(.82));int rider=lvl(wings,"wind_rider");if(rider>0){double bonus=Math.min(2.0,0.15+0.30*(rider-1));double mult=1+bonus;if(speed<4)p.setDeltaMovement(p.getDeltaMovement().scale(mult));if(p.tickCount%6==0)EnchantmentParticleFX.wingRibbons(p,ParticleTypes.CLOUD);}int skim=lvl(wings,"ground_skimmer");if(skim>0&&nearGround(p,4+2*skim))p.setDeltaMovement(p.getDeltaMovement().scale(1+.003*skim));int turb=lvl(wings,"turbulence_curse");if(turb>0&&p.tickCount%(Math.max(8,30-6*turb))==0){double f=.025*turb*(p.level().isThundering()?1.5:1);p.setDeltaMovement(p.getDeltaMovement().add((p.getRandom().nextDouble()-.5)*f,0,(p.getRandom().nextDouble()-.5)*f));}int brittle=lvl(wings,"brittle_wings_curse");if(brittle>0&&p.tickCount%20==0)wings.hurtAndBreak(1+brittle,p,EquipmentSlot.CHEST);int conductor=lvl(wings,"sky_conductor_curse");if(conductor>0&&p.level().isThundering()&&p.tickCount%100==0&&p.getRandom().nextInt(100)<deepluckyblock.procedures.EnchantmentLevelAuditProcedure.Scaling.percent(3,conductor,75)&&p.level() instanceof ServerLevel sl){LightningBolt b=EntityType.LIGHTNING_BOLT.create(sl);if(b!=null){b.moveTo(p.position());sl.addFreshEntity(b);}}}}
    @SubscribeEvent public static void rocket(PlayerInteractEvent.RightClickItem e){try{rocketImpl(e);}catch(Throwable t){guard("rocket",t);}}
    private static void rocketImpl(PlayerInteractEvent.RightClickItem e){Player p=e.getEntity();if(!e.getItemStack().is(Items.FIREWORK_ROCKET)||!p.isFallFlying())return;ItemStack wings=p.getItemBySlot(EquipmentSlot.CHEST);if(lvl(wings,"rocket_blast")>0){p.setDeltaMovement(p.getDeltaMovement().add(p.getLookAngle().scale(1.2)));p.hurtMarked=true;if(p.level() instanceof ServerLevel sl)EnchantmentParticleFX.shockwaveRing(sl,p.getX(),p.getY(),p.getZ(),ParticleTypes.FIREWORK,2.0,16,.12);for(Mob m:p.level().getEntitiesOfClass(Mob.class,p.getBoundingBox().inflate(3)))m.setDeltaMovement(m.position().subtract(p.position()).normalize().scale(.8));p.level().playSound((net.minecraft.world.entity.Entity) null, p.blockPosition(), SoundEvents.GENERIC_EXPLODE.value(), SoundSource.PLAYERS, .8f, 1.4f);}}

    private static boolean nearGround(Player p,int max){BlockPos c=p.blockPosition();for(int i=1;i<=max;i++)if(p.level().getBlockState(c.below(i)).blocksMotion())return true;return false;}
    private static void spawn(Player p,ItemStack s){if(!p.getInventory().add(s))p.drop(s,false);}
    private static void spawnFishingMonster(ServerLevel l,net.minecraft.world.phys.Vec3 pos,int level,boolean hostile){Entity entity=(hostile||level>=2)?(l.random.nextBoolean()?EntityType.DROWNED.create(l):EntityType.GUARDIAN.create(l)):EntityType.SQUID.create(l);if(entity!=null){entity.moveTo(pos);l.addFreshEntity(entity);}}
    private static ItemStack heldRod(Player p){if(p.getMainHandItem().getItem() instanceof FishingRodItem)return p.getMainHandItem();if(p.getOffhandItem().getItem() instanceof FishingRodItem)return p.getOffhandItem();return ItemStack.EMPTY;}
    private static int tag(Entity e,String id){return e.getPersistentData().getInt("slb_"+id);}
    // FILET DE SECURITE niveau force (commande/lucky block/NBT) : au-dela du
    // cap normal de l'enchantement, softCappedLevel() applique un rendement
    // decroissant (racine carree) au lieu de laisser une formule lineaire
    // non plafonnee produire des valeurs absurdes a tres haut niveau (ex.
    // 1000). En jeu normal (niveau <= cap), AUCUN changement de comportement.
    private static int lvl(ItemStack s,String id){if(s==null||s.isEmpty())return 0;Enchantment e=deepluckyblock.util.DeepEnchant.byId("deep_lucky_block:"+id);if(e==null)return 0;int l=deepluckyblock.util.DeepEnchant.getLevel(s,e);if(l<=0)return 0;if(e.getMaxLevel()<=1)return 1;return deepluckyblock.util.DeepEnchant.softCappedLevel(e,l);}

    // ============================================================
    // V37 - Filet de securite
    // Un effet qui leve une exception ne doit JAMAIS empecher les
    // enchantements suivants de s'executer, ni casser la peche vanilla.
    // ============================================================
    private static final java.util.Set<String> REPORTED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static void guard(String where, Throwable t) {
        if (REPORTED.add(where + ':' + t.getClass().getName())) {
            com.mojang.logging.LogUtils.getLogger().error(
                "[deep_lucky_block] Erreur dans FishingTridentElytraHandler#{} - effet ignore, la peche continue.", where, t);
        }
    }

    // ============================================================
    // V37 - Correctif Lure a haut niveau (bouchon fige)
    //
    // CAUSE (comportement VANILLA, pas un bug du mod) :
    // FishingHook.catchingFish() calcule
    //     timeUntilLured = 100 + rand(600) - 100 * niveauDeLure
    // Des que Lure >= 6, le resultat est <= 0 : le jeu retire alors un
    // nouveau delai a chaque tick sans jamais declencher la touche.
    // Avec Lure 100 le bouchon ne bouge donc PLUS JAMAIS.
    //
    // CORRECTIF : on surveille le bouchon et, si le compteur est bloque
    // a <= 0 alors que le Lure depasse le seuil sur, on reinjecte un
    // delai court mais valide. La canne devient tres rapide (l'effet
    // voulu par un Lure eleve) au lieu d'etre inutilisable.
    //
    // Le champ est prive : on tente le nom Mojang puis le nom SRG. Si
    // aucun n'est trouve, le correctif se desactive proprement en
    // journalisant une fois, sans jamais casser la peche.
    // ============================================================
    private static final int LURE_SAFE_CAP = 5;
    private static java.lang.reflect.Field LURED_FIELD;
    private static boolean LURE_FIX_DISABLED;

    private static java.lang.reflect.Field luredField() {
        if (LURED_FIELD != null || LURE_FIX_DISABLED) return LURED_FIELD;
        for (String n : new String[]{"timeUntilLured", "f_37096_"}) {
            try {
                java.lang.reflect.Field f = FishingHook.class.getDeclaredField(n);
                f.setAccessible(true);
                LURED_FIELD = f;
                return f;
            } catch (Throwable ignored) {
            }
        }
        LURE_FIX_DISABLED = true;
        com.mojang.logging.LogUtils.getLogger().warn(
            "[deep_lucky_block] Champ timeUntilLured introuvable : correctif Lure desactive (la peche reste fonctionnelle).");
        return null;
    }

    private static void fixStuckLure(Player p) {
        FishingHook hook = p.fishing;
        if (hook == null || p.level().isClientSide()) return;

        ItemStack rod = heldRod(p);
        if (rod.isEmpty()) return;

        // T306-FIX : vérifie bien Enchantments.LURE (et non LUCK_OF_THE_SEA !)
        int lure = net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(
                deepluckyblock.util.DeepEnchant.keyHolder(p.level().registryAccess(), net.minecraft.world.item.enchantment.Enchantments.LURE), rod);
        if (lure <= LURE_SAFE_CAP) return;   // vanilla gere tres bien jusqu'a 5

        java.lang.reflect.Field f = luredField();
        if (f == null) return;

        try {
            if (f.getInt(hook) <= 0) {
                // delai court mais strictement positif : la touche finit par arriver
                f.setInt(hook, 20 + p.level().random.nextInt(40));
            }
        } catch (Throwable t) {
            LURE_FIX_DISABLED = true;
            guard("fixStuckLure", t);
        }
    }

}
