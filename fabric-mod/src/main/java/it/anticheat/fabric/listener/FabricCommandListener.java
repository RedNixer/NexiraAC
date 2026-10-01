package it.anticheat.fabric.listener;

import com.mojang.brigadier.arguments.StringArgumentType;
import it.anticheat.core.AnticheatCore;
import it.anticheat.core.PlayerData;
import it.anticheat.core.config.AnticheatConfig;
import it.anticheat.core.model.Report;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** Comandi /ac + /report (logica invariata Fase 0). */
public final class FabricCommandListener {
    private FabricCommandListener() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, env) -> {
            dispatcher.register(literal("ac")
                .requires(FabricState::isAdminCommand)
                .then(literal("gui").executes(ctx -> {
                    if (!ctx.getSource().isPlayer()) {
                        ctx.getSource().sendFailure(Component.literal("Solo in-game."));
                        return 0;
                    }
                    it.anticheat.fabric.gui.FabricAdminGui.openOverview(ctx.getSource().getPlayer());
                    return 1;
                }))
                .then(literal("settings").executes(ctx -> {
                    if (!ctx.getSource().isPlayer()) {
                        ctx.getSource().sendFailure(Component.literal("Solo in-game."));
                        return 0;
                    }
                    it.anticheat.fabric.gui.FabricAdminGui.openSettings(ctx.getSource().getPlayer(), 0);
                    return 1;
                }))
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
                }))
                // Fase 6: parita comandi Paper (stats/debug/add/reload/cps).
                .then(literal("stats").then(argument("player", StringArgumentType.word())
                    .executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "player");
                        ServerPlayer t = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                        if (t == null) {
                            ctx.getSource().sendFailure(Component.literal("Player offline."));
                            return 0;
                        }
                        sendStats(ctx.getSource(), t);
                        return 1;
                    })))
                .then(literal("stats").executes(ctx -> {
                    if (!ctx.getSource().isPlayer()) {
                        ctx.getSource().sendFailure(Component.literal("Uso: /ac stats <player>."));
                        return 0;
                    }
                    sendStats(ctx.getSource(), ctx.getSource().getPlayer());
                    return 1;
                }))
                .then(literal("debug").then(argument("player", StringArgumentType.word())
                    .executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "player");
                        ServerPlayer t = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                        if (t == null) {
                            ctx.getSource().sendFailure(Component.literal("Player offline."));
                            return 0;
                        }
                        toggleDebug(ctx.getSource(), t);
                        return 1;
                    })))
                .then(literal("cps").then(argument("limite", com.mojang.brigadier.arguments.IntegerArgumentType.integer(10, 100))
                    .executes(ctx -> {
                        int n = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "limite");
                        AnticheatCore.get().config().cpsLimit = n;
                        boolean saved = persistConfig();
                        ctx.getSource().sendSuccess(() -> Component.literal(saved
                            ? "Limite CPS impostato a " + n + "."
                            : "Salvataggio fallito, vedi log."), false);
                        return 1;
                    })))
                .then(literal("cps").executes(ctx -> {
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "Limite CPS attuale: " + AnticheatCore.get().config().cpsLimit), false);
                    return 1;
                }))
                .then(literal("add").then(argument("player", StringArgumentType.word())
                    .executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "player");
                        ServerPlayer t = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                        if (t == null) {
                            ctx.getSource().sendFailure(Component.literal("Player offline."));
                            return 0;
                        }
                        AnticheatConfig c = AnticheatCore.get().config();
                        if (c.adminUuids.contains(t.getUUID())) {
                            ctx.getSource().sendSuccess(() -> Component.literal(name + " era gia admin."), false);
                        } else {
                            c.adminUuids.add(t.getUUID());
                            boolean saved = persistConfig();
                            ctx.getSource().sendSuccess(() -> Component.literal(saved
                                ? name + " aggiunto come admin."
                                : "Aggiunto ma salvataggio fallito!"), false);
                        }
                        return 1;
                    })))
                .then(literal("reload").executes(ctx -> {
                    boolean ok = reloadConfig();
                    ctx.getSource().sendSuccess(() -> Component.literal(ok
                        ? "Config ricaricata."
                        : "Reload fallito, vedi console."), false);
                    return 1;
                }))
                // Fase A: /ac vl senza argomento = i tuoi VL (feedback mentre testi).
                .then(literal("vl").executes(ctx -> {
                    if (!ctx.getSource().isPlayer()) {
                        ctx.getSource().sendFailure(Component.literal("Uso: /ac vl <player>."));
                        return 0;
                    }
                    ServerPlayer me = ctx.getSource().getPlayer();
                    PlayerData d = AnticheatCore.get().data(me.getUUID());
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        d.name + " VL=" + d.totalVl() + " client=" + d.clientStatus
                            + " " + d.clientVersion + " " + d.violations), false);
                    return 1;
                }))
                // Fase A: exempt tester (non admin, solo skip check).
                .then(literal("exempt").then(argument("player", StringArgumentType.word())
                    .executes(ctx -> {
                        String name = StringArgumentType.getString(ctx, "player");
                        ServerPlayer t = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
                        if (t == null) {
                            ctx.getSource().sendFailure(Component.literal("Player offline."));
                            return 0;
                        }
                        AnticheatConfig c = AnticheatCore.get().config();
                        boolean now;
                        if (c.exemptUuids.contains(t.getUUID())) {
                            c.exemptUuids.remove(t.getUUID());
                            now = false;
                        } else {
                            c.exemptUuids.add(t.getUUID());
                            now = true;
                        }
                        boolean saved = persistConfig();
                        ctx.getSource().sendSuccess(() -> Component.literal(
                            (now ? "Exempt ON per " : "Exempt OFF per ") + name
                                + (saved ? "." : " (salvataggio fallito!)")), false);
                        return 1;
                    })))
                // Fase A: setback-test = min-vl a 5 per vedere il teleport subito.
                .then(literal("setback-test").executes(ctx -> {
                    AnticheatConfig c = AnticheatCore.get().config();
                    if (c.setbackMinVl == 5) {
                        c.setbackMinVl = 15;
                        c.setbackEnabled = true;
                    } else {
                        c.setbackMinVl = 5;
                        c.setbackEnabled = true;
                    }
                    boolean saved = persistConfig();
                    ctx.getSource().sendSuccess(() -> Component.literal(
                        "Setback-test: min-vl=" + c.setbackMinVl
                            + (saved ? "." : " (salvataggio fallito!)")), false);
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
    }

    private static boolean persistConfig() {
        try {
            AnticheatCore.get().config().saveFull(FabricState.configFile);
            return true;
        } catch (Exception e) {
            System.out.println("[AC] salvataggio config fallito: " + e.getMessage());
            return false;
        }
    }

    private static boolean reloadConfig() {
        try {
            AnticheatConfig cfg = AnticheatConfig.load(FabricState.configFile);
            AnticheatCore.get().init(cfg, AnticheatCore.get().storage(), null);
            System.out.println("[AC] config ricaricata. Admin UUID: " + cfg.adminUuids.size());
            return true;
        } catch (Exception e) {
            System.out.println("[AC] reload config fallito: " + e.getMessage());
            return false;
        }
    }

    private static void toggleDebug(net.minecraft.commands.CommandSourceStack src, ServerPlayer t) {
        boolean on = !AnticheatCore.get().isDebug(t.getUUID());
        AnticheatCore.get().setDebug(t.getUUID(), on);
        src.sendSuccess(() -> Component.literal(on
            ? "Debug ON per " + t.getScoreboardName() + ": guarda la console mentre gioca."
            : "Debug OFF per " + t.getScoreboardName() + "."), false);
    }

    private static void sendStats(net.minecraft.commands.CommandSourceStack src, ServerPlayer t) {
        PlayerData d = AnticheatCore.get().data(t.getUUID());
        long now = System.currentTimeMillis();
        int cps = 0;
        for (long ts : d.clickTimes) if (now - ts <= 1000) cps++;
        String line1 = d.name + " VL tot " + d.totalVl() + " ping " + d.ping
            + " client " + d.clientStatus + " " + d.clientVersion;
        String line2 = "CPS(1s): " + cps + " (limite " + AnticheatCore.get().config().cpsLimit + ")"
            + " viol: " + (d.violations.isEmpty() ? "-" : d.violations);
        src.sendSuccess(() -> Component.literal(line1), false);
        src.sendSuccess(() -> Component.literal(line2), false);
    }
}
