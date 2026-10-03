package it.anticheat.fabric.listener;

import it.anticheat.core.AnticheatCore;
import it.anticheat.core.checks.ScaffoldCheck;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;

/** Scaffold + FastBreak (logica invariata Fase 0). */
public final class FabricWorldListener {
    private FabricWorldListener() {}

    public static void register() {
        // Scaffold: piazzamenti in aria guardando in basso
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player instanceof ServerPlayer sp && !world.isClientSide()) {
                boolean sneaking;
                try {
                    sneaking = sp.getPose() == net.minecraft.world.entity.Pose.CROUCHING;
                } catch (Throwable ignored) {
                    sneaking = false;
                }
                AnticheatCore.get().handleBlockPlace(sp.getUUID(), sp.getScoreboardName(),
                    sp.onGround(), sp.getXRot(), sp.getX(), sp.getZ(), sneaking, sp.getYRot());
                // RotationPlace + FarPlace (stile Grim, matematica nel core)
                if (!sp.isCreative()) {
                    try {
                        net.minecraft.core.BlockPos bp = hitResult.getBlockPos();
                        double cx = bp.getX() + 0.5, cy = bp.getY() + 0.5, cz = bp.getZ() + 0.5;
                        double ex = sp.getX(), ey = sp.getEyeY(), ez = sp.getZ();
                        double ddx = cx - ex, ddy = cy - ey, ddz = cz - ez;
                        double dist = Math.sqrt(ddx * ddx + ddy * ddy + ddz * ddz);
                        double angle = ScaffoldCheck.lookAngleDiff(
                            sp.getYRot(), sp.getXRot(), ddx, ddy, ddz);
                        AnticheatCore.get().handlePlaceRotation(sp.getUUID(), sp.getScoreboardName(), angle, dist);
                    } catch (Throwable ignored) {}
                }
            }
            return InteractionResult.PASS;
        });

        // FastBreak (no creativa)
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer sp && !sp.isCreative()) {
                float hardness = 1.0f;
                try {
                    hardness = state.getDestroySpeed(world, pos);
                } catch (Throwable ignored) {}
                boolean hard = hardness > 0.05f;
                // Fase 4: mine-timing + DPS (come WorldListener Paper).
                // Nomi Mojang verificati in compilazione: MobEffects.HASTE /
                // MINING_FATIGUE, getMainHandItem, getDestroySpeed.
                String tool = "HAND";
                int effLvl = 0;
                int hasteAmp = -1;
                int fatigueAmp = -1;
                boolean hasHaste = false;
                try {
                    net.minecraft.world.item.ItemStack hand = sp.getMainHandItem();
                    if (hand != null && !hand.isEmpty()) {
                        String ipath = "";
                        try {
                            ipath = net.minecraft.core.registries.BuiltInRegistries.ITEM
                                .getKey(hand.getItem()).getPath().toUpperCase();
                        } catch (Throwable ignored) {}
                        if (ipath.contains("WOODEN")) tool = "WOOD";
                        else if (ipath.contains("STONE")) tool = "STONE";
                        else if (ipath.contains("IRON")) tool = "IRON";
                        else if (ipath.contains("GOLDEN")) tool = "GOLD";
                        else if (ipath.contains("DIAMOND")) tool = "DIAMOND";
                        else if (ipath.contains("NETHERITE")) tool = "NETHERITE";
                        try {
                            String ench = String.valueOf(hand.getEnchantments());
                            int ei = ench.indexOf("efficiency");
                            if (ei >= 0) {
                                String sub = ench.substring(ei, Math.min(ench.length(), ei + 40));
                                String digits = sub.replaceAll("[^0-9]", " ").trim().split("\\s+")[0];
                                try { effLvl = Integer.parseInt(digits); } catch (Throwable ignored) {}
                            }
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
                try {
                    net.minecraft.world.effect.MobEffectInstance h =
                        sp.getEffect(net.minecraft.world.effect.MobEffects.HASTE);
                    if (h != null) { hasteAmp = h.getAmplifier(); hasHaste = true; }
                } catch (Throwable ignored) {}
                try {
                    net.minecraft.world.effect.MobEffectInstance f =
                        sp.getEffect(net.minecraft.world.effect.MobEffects.MINING_FATIGUE);
                    if (f != null) fatigueAmp = f.getAmplifier();
                } catch (Throwable ignored) {}
                boolean inWater = false;
                try { inWater = sp.isInWater(); } catch (Throwable ignored) {}
                boolean onGround = true;
                try { onGround = sp.onGround(); } catch (Throwable ignored) {}
                String key = "";
                try {
                    key = sp.level().dimension().toString() + ":" + pos.getX() + ":" + pos.getY() + ":" + pos.getZ();
                } catch (Throwable ignored) {}
                // mine-timing + DPS PRIMA del break (stesso ordine Paper)
                try {
                    AnticheatCore.get().handleMineTiming(sp.getUUID(), sp.getScoreboardName(),
                        hardness, key.isEmpty() ? null : key, hasHaste);
                } catch (Throwable ignored) {}
                try {
                    AnticheatCore.get().handleMineDps(sp.getUUID(), sp.getScoreboardName(),
                        hardness, key.isEmpty() ? null : key, tool, effLvl,
                        hasteAmp, fatigueAmp, inWater, onGround);
                } catch (Throwable ignored) {}
                AnticheatCore.get().handleBlockBreak(sp.getUUID(), sp.getScoreboardName(), hard, null);
            }
            return true; // mai cancellare: solo rilevazione
        });
    }
}
