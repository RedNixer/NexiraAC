package it.anticheat.fabric.listener;

import it.anticheat.core.AnticheatCore;
import it.anticheat.core.Check;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Combat: reach/killaura + NoFall + tregua riptide (logica invariata Fase 0). */
public final class FabricCombatListener {
    private FabricCombatListener() {}

    public static void register() {
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            double dist = sp.distanceTo(entity);
            Check.FightContext ctx = new Check.FightContext();
            ctx.distance = dist;
            try { ctx.targetId = entity.getId(); } catch (Throwable ignored2) { ctx.targetId = -1; }
            try { ctx.targetIsPlayer = entity instanceof net.minecraft.world.entity.player.Player; }
            catch (Throwable ignored2) { ctx.targetIsPlayer = false; }
            ctx.attackerYaw = sp.getYRot();
            try { ctx.attackerPitch = sp.getXRot(); } catch (Throwable ignored) {}
            ctx.ax = sp.getX();
            ctx.ay = sp.getY();
            ctx.az = sp.getZ();
            try { ctx.blocking = sp.isBlocking(); } catch (Throwable ignored) {}
            // occhio-attaccante -> bersaglio (anti-FP su mob sbalzati in aria)
            try {
                double dx = entity.getX() - sp.getX();
                double dy = entity.getY() - sp.getEyeY();
                double dz = entity.getZ() - sp.getZ();
                ctx.eyeDistance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            } catch (Throwable ignored) {
                ctx.eyeDistance = 0;
            }
            ctx.dtSinceLastAttackMillis = -1;
            try { ctx.ping = sp.connection.latency(); } catch (Throwable ignored) {}
            // Fase 1: cooldown vanilla (come CombatListener Paper).
            try { ctx.attackCooldown = sp.getAttackStrengthScale(0.5f); } catch (Throwable ignored) {}
            // yaw diff
            try {
                double dx = entity.getX() - sp.getX();
                double dz = entity.getZ() - sp.getZ();
                double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
                double diff = Math.abs(targetYaw - sp.getYRot()) % 360;
                if (diff > 180) diff = 360 - diff;
                ctx.targetYawDiff = diff;
            } catch (Throwable ignored) {}
            AnticheatCore.get().handleFight(sp.getUUID(), sp.getScoreboardName(), ctx);
            // Fase 3: click su ogni colpo (come Paper onHit -> handleClick).
            // La valutazione CPS/regolarita vive nel core (gate combat).
            try {
                AnticheatCore.get().handleClick(sp.getUUID(), sp.getScoreboardName());
            } catch (Throwable ignored) {}
            return InteractionResult.PASS;
        });

        // Fase 3: interact non-attacco (come evaluateInteractRange Paper).
        // Self-hit + gittata occhio->entita, tutto a eventi (niente pacchetti).
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            if (entity == null || entity.equals(sp)) {
                try {
                    if (entity != null) {
                        AnticheatCore.get().handleInteractAttack(sp.getUUID(), sp.getScoreboardName(),
                            false, true, entity.getId());
                    }
                } catch (Throwable ignored) {}
                return InteractionResult.PASS;
            }
            try {
                double dx = entity.getX() - sp.getX();
                double dy = entity.getY() - sp.getEyeY();
                double dz = entity.getZ() - sp.getZ();
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                AnticheatCore.get().handleInteractUse(sp.getUUID(), sp.getScoreboardName(),
                    entity.getId(), dist);
            } catch (Throwable ignored) {}
            return InteractionResult.PASS;
        });

        // NoFall: danno da caduta mancato + esenzione movimento (knockback)
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((LivingEntity entity, DamageSource src, float amount) -> {
            if (!(entity instanceof ServerPlayer sp)) return true;
            AnticheatCore.get().exemptMove(sp.getUUID(), 1500);
            try {
                if (src.typeHolder().is(net.minecraft.world.damagesource.DamageTypes.FALL)) {
                    AnticheatCore.get().handleFallDamage(sp.getUUID(), sp.getScoreboardName(),
                        sp.fallDistance, amount);
                }
            } catch (Throwable ignored) {}
            return true;
        });

        // Riptide: tridente in acqua/pioggia spara a 15+ b/s (sarebbe Speed).
        // Tregua 2.5s (su Paper il controllo incanto e preciso, qui no).
        UseItemCallback.EVENT.register((player, world, hand) -> {
            net.minecraft.world.item.ItemStack held = player.getItemInHand(hand);
            if (player instanceof ServerPlayer sp) {
                try {
                    if (held.getItem() instanceof net.minecraft.world.item.TridentItem
                            && (sp.isInWater() || world.isRaining())) {
                        AnticheatCore.get().exemptMove(sp.getUUID(), 2500);
                    }
                    // Perla ender (Fase 2, come ProjectileLaunch Paper): teleport = tregua 3s.
                    if (held.getItem() instanceof net.minecraft.world.item.EnderpearlItem) {
                        AnticheatCore.get().exemptMove(sp.getUUID(), 3000);
                    }
                } catch (Throwable ignored) {}
            }
            return net.minecraft.world.InteractionResult.PASS;
        });
    }
}
