package it.anticheat.fabric.listener;

import it.anticheat.core.AnticheatCore;
import it.anticheat.core.Check;
import it.anticheat.core.PlayerData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.server.level.ServerPlayer;

/** Check movimento ogni tick (logica invariata dallo split Fase 0). */
public final class FabricMoveListener {
    private FabricMoveListener() {}

    public static void register() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.currentTimeMillis();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.isCreative() || p.isSpectator()) continue;
                Long last = FabricState.lastTick.get(p.getUUID());
                if (last != null && now - last < 90) continue;
                double[] prev = FabricState.lastPos.get(p.getUUID());
                double x = p.getX(), y = p.getY(), z = p.getZ();
                // Freeze punizione: rimanda dov'era (prev) e salta i check
                try {
                    if (AnticheatCore.get().data(p.getUUID()).frozen && prev != null) {
                        p.teleportTo(prev[0], prev[1], prev[2]);
                        FabricState.lastTick.put(p.getUUID(), now);
                        continue;
                    }
                } catch (Throwable ignored) {}
                if (prev != null && last != null) {
                    double dx = x - prev[0], dy = y - prev[1], dz = z - prev[2];
                    double distXZ = Math.sqrt(dx * dx + dz * dz);
                    // Teleport/veicolo (Fase 2, come Paper): salto >12 = reset, non giudizio.
                    // Su Fabric niente evento teleport: il salto stesso e il segnale.
                    if (distXZ > 12 || Math.abs(dy) > 12) {
                        String wk;
                        try {
                            wk = p.level().dimension().toString();
                        } catch (Throwable ignored) {
                            wk = "";
                        }
                        AnticheatCore.get().resetMoveState(p.getUUID(), x, y, z, wk);
                        AnticheatCore.get().exemptMove(p.getUUID(), 2000);
                        FabricState.lastPos.put(p.getUUID(), new double[]{x, y, z});
                        FabricState.lastTick.put(p.getUUID(), now);
                        continue;
                    }
                    if (distXZ > 0.0001 || Math.abs(dy) > 0.0001) {
                        if (distXZ < 12 && Math.abs(dy) < 12) {
                            Check.MoveContext ctx = new Check.MoveContext();
                            ctx.dx = dx; ctx.dy = dy; ctx.dz = dz;
                            ctx.distXZ = distXZ;
                            ctx.y = y;
                            ctx.yaw = p.getYRot();
                            ctx.pitch = p.getXRot();
                            ctx.groundBelow = true; // ricalcolato sotto, fallback = niente flag
                            try {
                                net.minecraft.core.BlockPos sup1 = new net.minecraft.core.BlockPos(
                                    (int) Math.floor(x), (int) Math.floor(y - 0.51), (int) Math.floor(z));
                                net.minecraft.core.BlockPos sup2 = new net.minecraft.core.BlockPos(
                                    (int) Math.floor(x), (int) Math.floor(y - 1.01), (int) Math.floor(z));
                                ctx.groundBelow =
                                    (!p.level().getBlockState(sup1).isAir()
                                        && p.level().getFluidState(sup1).isEmpty())
                                    || (!p.level().getBlockState(sup2).isAir()
                                        && p.level().getFluidState(sup2).isEmpty());
                            } catch (Throwable ignored) {
                                ctx.groundBelow = true;
                            }
                            ctx.onGround = p.onGround();
                            ctx.flying = p.getAbilities().flying;
                            ctx.inWater = p.isInWater();
                            ctx.gliding = p.isFallFlying();
                            ctx.riding = p.isPassenger();
                            ctx.inCobweb = false; // calcolato sotto, fallback = niente flag
                            try {
                                net.minecraft.core.BlockPos feetPos = new net.minecraft.core.BlockPos(
                                    (int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
                                net.minecraft.world.level.block.state.BlockState feetState =
                                    p.level().getBlockState(feetPos);
                                ctx.inCobweb = feetState.is(net.minecraft.world.level.block.Blocks.COBWEB)
                                    || feetState.is(net.minecraft.world.level.block.Blocks.POWDER_SNOW);
                                if (!ctx.onGround && ctx.dy > 0.05) {
                                    ctx.onLadder = feetState.is(net.minecraft.world.level.block.Blocks.LADDER)
                                        || feetState.is(net.minecraft.world.level.block.Blocks.VINE)
                                        || feetState.is(net.minecraft.world.level.block.Blocks.WEEPING_VINES)
                                        || feetState.is(net.minecraft.world.level.block.Blocks.TWISTING_VINES)
                                        || feetState.is(net.minecraft.world.level.block.Blocks.CAVE_VINES)
                                        || feetState.is(net.minecraft.world.level.block.Blocks.SCAFFOLDING);
                                }
                            } catch (Throwable ignored) {
                                ctx.inCobweb = false;
                            }
                            try { ctx.sprinting = p.isSprinting(); } catch (Throwable ignored) {}
                            try { ctx.blocking = p.isBlocking(); } catch (Throwable ignored) {}
                            try { ctx.usingItem = p.isUsingItem(); } catch (Throwable ignored) {}
                            try { ctx.hungry = p.getFoodData().getFoodLevel() <= 6; } catch (Throwable ignored) {}
                            try { ctx.swimming = p.isSwimming(); } catch (Throwable ignored) {}
                            // Fase 1: cecita + pozione Speed (come MovementListener Paper).
                            // Nomi Mojang: MobEffects.BLINDNESS / MobEffects.SPEED.
                            try {
                                ctx.blind = p.hasEffect(net.minecraft.world.effect.MobEffects.BLINDNESS);
                            } catch (Throwable ignored) {}
                            try {
                                net.minecraft.world.effect.MobEffectInstance eff =
                                    p.getEffect(net.minecraft.world.effect.MobEffects.SPEED);
                                ctx.speedAmp = eff == null ? -1 : eff.getAmplifier();
                            } catch (Throwable ignored) {
                                ctx.speedAmp = -1;
                            }
                            // Fase 1: soul sand + soul speed (come Paper).
                            try {
                                net.minecraft.core.BlockPos feetPos =
                                    new net.minecraft.core.BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
                                net.minecraft.world.level.block.state.BlockState st =
                                    p.level().getBlockState(feetPos);
                                ctx.soulSand = st.is(net.minecraft.world.level.block.Blocks.SOUL_SAND);
                                if (ctx.soulSand) {
                                    ctx.soulSpeed = false;
                                    try {
                                        net.minecraft.world.item.ItemStack boots = p.getItemBySlot(
                                            net.minecraft.world.entity.EquipmentSlot.FEET);
                                        if (boots != null && boots.getEnchantments() != null
                                                && boots.getEnchantments().toString().contains("soul_speed")) {
                                            ctx.soulSpeed = true;
                                        }
                                    } catch (Throwable ignored) {}
                                }
                            } catch (Throwable ignored) {}
                            try {
                                // liquido ai piedi / sotto (per Jesus)
                                net.minecraft.core.BlockPos feetPos =
                                    new net.minecraft.core.BlockPos((int) Math.floor(x), (int) Math.floor(y), (int) Math.floor(z));
                                net.minecraft.core.BlockPos belowPos =
                                    new net.minecraft.core.BlockPos((int) Math.floor(x), (int) Math.floor(y - 0.51), (int) Math.floor(z));
                                ctx.liquidFeet = !p.level().getFluidState(feetPos).isEmpty();
                                ctx.liquidBelow = !p.level().getFluidState(belowPos).isEmpty();
                            } catch (Throwable ignored) {}
                            // inventario aperto (per GUIMove): menu diverso da quello player
                            try {
                                AnticheatCore.get().setInventoryOpen(p.getUUID(),
                                    p.containerMenu != p.inventoryMenu);
                            } catch (Throwable ignored) {}
                            ctx.ping = 0;
                            try { ctx.ping = p.connection.latency(); } catch (Throwable ignored) {}
                            ctx.dtMillis = Math.min(1000, Math.max(10, now - last));
                            String worldKey;
                            try {
                                worldKey = p.level().dimension().toString();
                            } catch (Throwable ignored) {
                                worldKey = "";
                            }
                            AnticheatCore.get().handleMove(p.getUUID(), p.getScoreboardName(), ctx, x, y, z, worldKey);
                            if (AnticheatCore.get().isDebug(p.getUUID())) {
                                PlayerData dd = AnticheatCore.get().data(p.getUUID());
                                System.out.println("[AC-DBG] move " + p.getScoreboardName()
                                    + " dy=" + String.format("%.2f", dy) + " ground=" + ctx.onGround
                                    + " below=" + ctx.groundBelow + " spdStreak=" + dd.speedStreak
                                    + " VL=" + dd.totalVl());
                            }
                        }
                    }
                }
                FabricState.lastPos.put(p.getUUID(), new double[]{x, y, z});
                FabricState.lastTick.put(p.getUUID(), now);
                // Veicoli (Fase 2, come VehicleEnter/Exit Paper): niente check in sella.
                try {
                    if (p.isPassenger()) AnticheatCore.get().exemptMove(p.getUUID(), 1000);
                } catch (Throwable ignored) {}
                // cambio hotbar (per AutoTool swap-timing)
                try {
                    int slot = p.getInventory().getSelectedSlot();
                    Integer prevSlot = FabricState.lastHeldSlot.put(p.getUUID(), slot);
                    if (prevSlot != null && prevSlot != slot) AnticheatCore.get().noteHeldChange(p.getUUID());
                } catch (Throwable ignored) {}
            }
            AnticheatCore.get().serverTick();
            // decay VL ogni ~60s
            if (server.getTickCount() % 1200 == 0) {
                for (PlayerData d : AnticheatCore.get().allPlayers().values()) d.decay(1);
            }
        });
    }
}
