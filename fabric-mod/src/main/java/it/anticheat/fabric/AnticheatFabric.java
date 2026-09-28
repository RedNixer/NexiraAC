package it.anticheat.fabric;

import com.mojang.brigadier.arguments.StringArgumentType;
import it.anticheat.core.AnticheatCore;
import it.anticheat.core.Check;
import it.anticheat.core.ClientStatus;
import it.anticheat.core.PlayerData;
import it.anticheat.core.config.AnticheatConfig;
import it.anticheat.core.model.Report;
import it.anticheat.core.storage.MemoryStorage;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.UserBanListEntry;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public class AnticheatFabric implements ModInitializer {

    private final Map<UUID, double[]> lastPos = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastTick = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> lastHeldSlot = new ConcurrentHashMap<>();
    private static Path configFile;

    @Override
    public void onInitialize() {
        configFile = FabricLoader.getInstance().getConfigDir()
            .resolve("anticheat").resolve("config.yml");
        AnticheatConfig cfg;
        try {
            cfg = AnticheatConfig.load(configFile);
        } catch (Exception e) {
            System.out.println("[AC] config illeggibile, uso default: " + e.getMessage());
            cfg = new AnticheatConfig();
        }
        final AnticheatConfig config = cfg;

        AnticheatCore.get().init(config, new MemoryStorage(), new AnticheatCore.ActionHandler() {
            @Override
            public void warn(UUID player, String check, int totalVl) {
                MinecraftServer srv = server();
                if (srv == null) return;
                ServerPlayer p = srv.getPlayerList().getPlayer(player);
                if (p != null) p.sendSystemMessage(Component.literal("[AC] Comportamento sospetto (" + check + ")."));
            }

            @Override
            public void kick(UUID player, String reason) {
                MinecraftServer srv = server();
                if (srv == null) return;
                srv.execute(() -> {
                    ServerPlayer p = srv.getPlayerList().getPlayer(player);
                    if (p != null) p.connection.disconnect(Component.literal(reason));
                });
            }

            @Override
            public void ban(UUID player, String reason) {
                MinecraftServer srv = server();
                if (srv == null) return;
                srv.execute(() -> {
                    ServerPlayer p = srv.getPlayerList().getPlayer(player);
                    PlayerList list = srv.getPlayerList();
                    if (p != null) {
                        list.getBans().add(new UserBanListEntry(
                            new NameAndId(p.getUUID(), p.getScoreboardName()), null, "AntiCheat", null, reason));
                        p.connection.disconnect(Component.literal(reason));
                    }
                });
            }

            @Override
            public void setback(UUID player) {
                MinecraftServer srv = server();
                if (srv == null) return;
                srv.execute(() -> {
                    ServerPlayer p = srv.getPlayerList().getPlayer(player);
                    if (p == null) return;
                    PlayerData d = AnticheatCore.get().data(player);
                    if (!d.hasGroundPos) return;
                    try {
                        if (!d.lastGroundWorld.equals(p.level().dimension().toString())) return;
                    } catch (Throwable ignored) {
                        return;
                    }
                    p.teleportTo(Math.floor(d.lastGroundX) + 0.5, d.lastGroundY,
                        Math.floor(d.lastGroundZ) + 0.5);
                    p.sendSystemMessage(Component.literal("[AC] Movimento irregolare: riposizionato."));
                });
            }

            @Override
            public void notifyStaff(String message) {
                MinecraftServer srv = server();
                if (srv == null) return;
                String plain = message.replaceAll("§.", "");
                for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                    if (isAdmin(p)) p.sendSystemMessage(Component.literal(plain));
                }
            }
        });

        ServerLifecycleEvents.SERVER_STARTED.register(s -> srv = s);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> { if (srv == s) srv = null; });

        // Esenti dai controlli: gli operator (come anticheat.exempt su Paper)
        AnticheatCore.get().setExemptChecker(uuid -> {
            MinecraftServer s = server();
            if (s == null) return false;
            ServerPlayer pl = s.getPlayerList().getPlayer(uuid);
            try {
                return pl != null && s.getPlayerList().isOp(pl.nameAndId());
            } catch (Throwable t) {
                return false;
            }
        });

        PayloadTypeRegistry.playC2S().register(HelloPayload.TYPE, HelloPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HelloPayload.TYPE, HelloPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(OpenGuiPayload.TYPE, OpenGuiPayload.CODEC);

        // Tasto F8 dal client: manda lista sospetti (su Fabric niente chest-GUI, solo testo)
        ServerPlayNetworking.registerGlobalReceiver(OpenGuiPayload.TYPE, (payload, context) -> {
            ServerPlayer p = context.player();
            if (!isAdmin(p)) return;
            List<PlayerData> all = new ArrayList<>(AnticheatCore.get().allPlayers().values());
            all.sort(Comparator.comparingInt(PlayerData::totalVl).reversed());
            if (all.isEmpty()) {
                p.sendSystemMessage(Component.literal("[AC] Nessun dato giocatori."));
                return;
            }
            p.sendSystemMessage(Component.literal("[AC] Sospetti (top 10) — dettaglio: /ac vl <nome>"));
            for (PlayerData d : all.subList(0, Math.min(10, all.size()))) {
                p.sendSystemMessage(Component.literal("- " + d.name + " VL=" + d.totalVl() + " " + d.violations));
            }
        });

        ServerPlayNetworking.registerGlobalReceiver(HelloPayload.TYPE, (payload, context) -> {
            String ver = HelloPayload.extractVersion(payload.text());
            boolean ok = compareVersions(ver, AnticheatCore.get().config().clientMinVersion) >= 0;
            UUID uuid = context.player().getUUID();
            PlayerData d = AnticheatCore.get().data(uuid);
            d.name = context.player().getScoreboardName();
            d.clientVersion = ver;
            d.clientStatus = ok ? ClientStatus.VERIFIED : ClientStatus.UNVERIFIED;
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer p = handler.getPlayer();
            PlayerData d = AnticheatCore.get().data(p.getUUID());
            d.name = p.getScoreboardName();
            d.joinTime = System.currentTimeMillis();
            d.clientStatus = ClientStatus.MISSING;
            lastPos.put(p.getUUID(), new double[]{p.getX(), p.getY(), p.getZ()});
            lastTick.put(p.getUUID(), System.currentTimeMillis());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            lastPos.remove(handler.getPlayer().getUUID()));

        // Movement check ogni tick (ogni ~100ms per player per non spammare)
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            long now = System.currentTimeMillis();
            for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                if (p.isCreative() || p.isSpectator()) continue;
                Long last = lastTick.get(p.getUUID());
                if (last != null && now - last < 90) continue;
                double[] prev = lastPos.get(p.getUUID());
                double x = p.getX(), y = p.getY(), z = p.getZ();
                if (prev != null && last != null) {
                    double dx = x - prev[0], dy = y - prev[1], dz = z - prev[2];
                    double distXZ = Math.sqrt(dx * dx + dz * dz);
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
                        }
                    }
                }
                lastPos.put(p.getUUID(), new double[]{x, y, z});
                lastTick.put(p.getUUID(), now);
                // cambio hotbar (per AutoTool swap-timing)
                try {
                    int slot = p.getInventory().getSelectedSlot();
                    Integer prevSlot = lastHeldSlot.put(p.getUUID(), slot);
                    if (prevSlot != null && prevSlot != slot) AnticheatCore.get().noteHeldChange(p.getUUID());
                } catch (Throwable ignored) {}
            }
            AnticheatCore.get().serverTick();
            // decay VL ogni ~60s
            if (server.getTickCount() % 1200 == 0) {
                for (PlayerData d : AnticheatCore.get().allPlayers().values()) d.decay(1);
            }
        });

        // Combat: reach + killaura
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!(player instanceof ServerPlayer sp)) return InteractionResult.PASS;
            double dist = sp.distanceTo(entity);
            Check.FightContext ctx = new Check.FightContext();
            ctx.distance = dist;
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
                } catch (Throwable ignored) {}
            }
            return net.minecraft.world.InteractionResult.PASS;
        });

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
                        double angle = it.anticheat.core.checks.ScaffoldCheck.lookAngleDiff(
                            sp.getYRot(), sp.getXRot(), ddx, ddy, ddz);
                        AnticheatCore.get().handlePlaceRotation(sp.getUUID(), sp.getScoreboardName(), angle, dist);
                    } catch (Throwable ignored) {}
                }
            }
            return InteractionResult.PASS;
        });

        // FastBreak + XRay statistico (no creativa)
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer sp && !sp.isCreative()) {
                boolean hard = true;
                try {
                    hard = state.getDestroySpeed(world, pos) > 0.05f;
                } catch (Throwable ignored) {}
                AnticheatCore.get().handleBlockBreak(sp.getUUID(), sp.getScoreboardName(), hard, null);
                String path = blockPath(state);
                boolean valuable = path.equals("diamond_ore")
                    || path.equals("deepslate_diamond_ore")
                    || path.equals("ancient_debris");
                boolean stone = !valuable && (path.equals("stone")
                    || path.equals("deepslate")
                    || path.equals("netherrack")
                    || path.equals("tuff")
                    || path.equals("andesite")
                    || path.equals("diorite")
                    || path.equals("granite")
                    || path.equals("calcite")
                    || path.equals("smooth_basalt")
                    || path.equals("basalt")
                    || path.equals("blackstone")
                    || path.equals("gravel"));
                if (valuable || stone) {
                    AnticheatCore.get().handleXrayBreak(sp.getUUID(), sp.getScoreboardName(), valuable, true);
                }
            }
            return true; // mai cancellare: solo rilevazione
        });

        // Comandi: /ac vl|reset|reports + /report
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> {
            dispatcher.register(literal("ac")
                .requires(AnticheatFabric::isAdminCommand)
                .then(literal("vl").then(argument("player", StringArgumentType.word())
                    .executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "player");
                        ServerPlayer t = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                        if (t == null) {
                            ctx.getSource().sendFailure(Component.literal("Player offline."));
                            return 0;
                        }
                        PlayerData d = AnticheatCore.get().data(t.getUUID());
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            d.name + " VL=" + d.totalVl() + " client=" + d.clientStatus
                                + " " + d.clientVersion + " " + d.violations), false);
                        return 1;
                    })))
                .then(literal("reset").then(argument("player", StringArgumentType.word())
                    .executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "player");
                        ServerPlayer t = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                        if (t == null) {
                            ctx.getSource().sendFailure(Component.literal("Player offline."));
                            return 0;
                        }
                        AnticheatCore.get().resetVl(t.getUUID());
                        ctx.getSource().sendSuccess(() -> Component.literal("VL resettate."), false);
                        return 1;
                    })))
                .then(literal("reports").executes(ctx -> {
                    List<Report> reps = AnticheatCore.get().storage().openReports();
                    if (reps.isEmpty()) {
                        ctx.getSource().sendSuccess(() -> Component.literal("Nessun report aperto."), false);
                    } else {
                        for (Report r : reps.subList(0, Math.min(10, reps.size()))) {
                            ctx.getSource().sendSuccess(() -> Component.literal(
                                "#" + r.id + " " + r.reportedName + " da " + r.reporterName + ": " + r.reason), false);
                        }
                    }
                    return 1;
                }))
                .then(literal("suspects").executes(ctx -> {
                    List<PlayerData> all = new ArrayList<>(AnticheatCore.get().allPlayers().values());
                    all.sort(Comparator.comparingInt(PlayerData::totalVl).reversed());
                    for (PlayerData d : all.subList(0, Math.min(10, all.size()))) {
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            d.name + " VL=" + d.totalVl() + " " + d.violations), false);
                    }
                    return 1;
                }))
                .then(literal("testmode").executes(ctx -> {
                    AnticheatConfig cc = AnticheatCore.get().config();
                    cc.testMode = !cc.testMode;
                    boolean saved = persistConfig();
                    ctx.getSource().sendSuccess(() -> Component.literal(cc.testMode
                        ? "Test-mode ATTIVO: nessuna punizione." + (saved ? "" : " (salvataggio fallito!)")
                        : "Test-mode spento: punizioni attive."), false);
                    return 1;
                }))
                .then(literal("experimental").executes(ctx -> {
                    AnticheatConfig cc2 = AnticheatCore.get().config();
                    cc2.experimentalChecks = !cc2.experimentalChecks;
                    boolean saved2 = persistConfig();
                    ctx.getSource().sendSuccess(() -> Component.literal(cc2.experimentalChecks
                        ? "Check sperimentali ATTIVI." + (saved2 ? "" : " (salvataggio fallito!)")
                        : "Check sperimentali spenti."), false);
                    return 1;
                })));
            dispatcher.register(literal("report")
                .then(argument("player", StringArgumentType.word())
                    .then(argument("motivo", StringArgumentType.greedyString())
                        .executes(ctx -> {
                            String name = StringArgumentType.getString(ctx, "player");
                            String motivo = StringArgumentType.getString(ctx, "motivo");
                            ServerPlayer t = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                            if (t == null) {
                                ctx.getSource().sendFailure(Component.literal("Giocatore non trovato."));
                                return 0;
                            }
                            var src = ctx.getSource();
                            UUID repId = src.isPlayer() ? src.getPlayer().getUUID() : new UUID(0, 0);
                            String repName = src.isPlayer() ? src.getPlayer().getScoreboardName() : "console";
                            AnticheatCore.get().report(repId, repName, t.getUUID(), t.getScoreboardName(), motivo);
                            src.sendSuccess(() -> Component.literal("Report inviato. Grazie!"), false);
                            return 1;
                        }))));
        });

        System.out.println("[AC] AntiCheat Fabric 1.21.11 caricato.");
    }

    private static MinecraftServer srv;

    private static MinecraftServer server() {
        return srv;
    }

    private static boolean persistConfig() {
        try {
            AnticheatCore.get().config().saveFull(configFile);
            return true;
        } catch (Exception e) {
            System.out.println("[AC] salvataggio config fallito: " + e.getMessage());
            return false;
        }
    }

    /** Nome registro del blocco rotto (es. "diamond_ore"), "" se non determinabile. */
    private static String blockPath(net.minecraft.world.level.block.state.BlockState state) {
        try {
            return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        } catch (Throwable t) {
            return "";
        }
    }

    static void setServer(MinecraftServer s) {
        srv = s;
    }

    private static boolean isAdminCommand(net.minecraft.commands.CommandSourceStack src) {
        if (!src.isPlayer()) return true; // console sempre admin
        return isAdmin(src.getPlayer());
    }

    private static boolean isAdmin(ServerPlayer p) {
        if (p == null) return false;
        if (AnticheatCore.get().config().adminUuids.contains(p.getUUID())) return true;
        if (!AnticheatCore.get().config().usePermissionToo) return false;
        try {
            MinecraftServer s = server();
            return s != null && s.getPlayerList().isOp(p.nameAndId());
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean isAdmin(net.minecraft.commands.CommandSourceStack src) {
        if (!src.isPlayer()) return true; // console sempre admin
        return isAdmin(src.getPlayer());
    }

    static int compareVersions(String a, String b) {
        try {
            String[] pa = a.split("\\.");
            String[] pb = b.split("\\.");
            for (int i = 0; i < Math.max(pa.length, pb.length); i++) {
                int x = i < pa.length ? Integer.parseInt(pa[i].replaceAll("\\D", "")) : 0;
                int y = i < pb.length ? Integer.parseInt(pb[i].replaceAll("\\D", "")) : 0;
                if (x != y) return Integer.compare(x, y);
            }
            return 0;
        } catch (Exception e) {
            return -1;
        }
    }
}
