package it.anticheat.fabric;

import it.anticheat.core.AnticheatCore;
import it.anticheat.core.PlayerData;
import it.anticheat.core.config.AnticheatConfig;
import it.anticheat.core.storage.MemoryStorage;
import it.anticheat.fabric.listener.FabricCombatListener;
import it.anticheat.fabric.listener.FabricCommandListener;
import it.anticheat.fabric.listener.FabricLifecycleListener;
import it.anticheat.fabric.listener.FabricMoveListener;
import it.anticheat.fabric.listener.FabricNetworkListener;
import it.anticheat.fabric.listener.FabricState;
import it.anticheat.fabric.listener.FabricWorldListener;
import java.util.UUID;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.NameAndId;
import net.minecraft.server.players.PlayerList;
import net.minecraft.server.players.UserBanListEntry;

/**
 * Initializer sottile (Fase 0): init core + azioni + registrazione listener.
 * Tutta la logica eventi vive in it.anticheat.fabric.listener.*.
 */
public class AnticheatFabric implements ModInitializer {

    @Override
    public void onInitialize() {
        FabricState.configFile = FabricLoader.getInstance().getConfigDir()
            .resolve("anticheat").resolve("config.yml");
        AnticheatConfig cfg;
        try {
            cfg = AnticheatConfig.load(FabricState.configFile);
        } catch (Exception e) {
            System.out.println("[AC] config illeggibile, uso default: " + e.getMessage());
            cfg = new AnticheatConfig();
        }
        final AnticheatConfig config = cfg;
        // Punizioni per-check (stesso formato di Paper)
        try {
            AnticheatCore.get().setPunishments(
                it.anticheat.core.config.PunishmentConfig.load(
                    FabricLoader.getInstance().getConfigDir()
                        .resolve("anticheat").resolve("punishments.json")));
        } catch (Exception e) {
            System.out.println("[AC] punishments illeggibili, uso default: " + e.getMessage());
        }

        AnticheatCore.get().init(config, new MemoryStorage(), new AnticheatCore.ActionHandler() {
            @Override
            public void warn(UUID player, String check, int totalVl) {
                MinecraftServer srv = FabricState.server();
                if (srv == null) return;
                ServerPlayer p = srv.getPlayerList().getPlayer(player);
                if (p != null) p.sendSystemMessage(Component.literal("[AC] Comportamento sospetto (" + check + ")."));
            }

            @Override
            public void kick(UUID player, String reason) {
                MinecraftServer srv = FabricState.server();
                if (srv == null) return;
                srv.execute(() -> {
                    ServerPlayer p = srv.getPlayerList().getPlayer(player);
                    if (p != null) p.connection.disconnect(Component.literal(reason));
                });
            }

            @Override
            public void ban(UUID player, String reason) {
                tempban(player, reason, -1);
            }

            @Override
            public void tempban(UUID player, String reason, long expiresAtMs) {
                MinecraftServer srv = FabricState.server();
                if (srv == null) return;
                srv.execute(() -> {
                    ServerPlayer p = srv.getPlayerList().getPlayer(player);
                    PlayerList list = srv.getPlayerList();
                    if (p != null) {
                        java.util.Date exp = expiresAtMs < 0 ? null : new java.util.Date(expiresAtMs);
                        list.getBans().add(new UserBanListEntry(
                            new NameAndId(p.getUUID(), p.getScoreboardName()), null, "AntiCheat", exp, reason));
                        p.connection.disconnect(Component.literal(reason));
                    }
                });
            }

            @Override
            public void freeze(UUID player, boolean on) {
                AnticheatCore.get().data(player).frozen = on;
                MinecraftServer srv = FabricState.server();
                if (srv == null) return;
                srv.execute(() -> {
                    ServerPlayer p = srv.getPlayerList().getPlayer(player);
                    if (p != null) p.sendSystemMessage(Component.literal(on
                        ? "[AC] Sei stato congelato dallo staff. Non muoverti."
                        : "[AC] Scongelato, puoi muoverti."));
                });
            }

            @Override
            public void setback(UUID player) {
                MinecraftServer srv = FabricState.server();
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
                // Fase A: console SEMPRE (prima solo admin online = silenzio totale
                // se nessuno admin loggato o richiedente non riconosciuto).
                String plain = message.replaceAll("§.", "");
                System.out.println("[AC] " + plain);
                MinecraftServer srv = FabricState.server();
                if (srv == null) return;
                boolean announceAll = false;
                try { announceAll = AnticheatCore.get().config().announceAll; } catch (Throwable ignored) {}
                for (ServerPlayer p : srv.getPlayerList().getPlayers()) {
                    try {
                        if (FabricState.isAdmin(p)) p.sendSystemMessage(Component.literal(plain));
                        else if (announceAll) {
                            try {
                                if (srv.getPlayerList().isOp(p.nameAndId())) {
                                    p.sendSystemMessage(Component.literal(plain));
                                }
                            } catch (Throwable ignored) {}
                        }
                    } catch (Throwable ignored) {}
                }
            }
        });

        // Esenti dai controlli (Fase A): lista exempt-uuids + OP.
        // Prima solo OP: i tester non-OP si autoflaggavano, gli admin-uuid
        // senza OP venivano flaggati mentre testavano.
        AnticheatCore.get().setExemptChecker(uuid -> {
            try {
                if (AnticheatCore.get().config().exemptUuids.contains(uuid)) return true;
            } catch (Throwable ignored) {}
            MinecraftServer s = FabricState.server();
            if (s == null) return false;
            ServerPlayer pl = s.getPlayerList().getPlayer(uuid);
            try {
                return pl != null && s.getPlayerList().isOp(pl.nameAndId());
            } catch (Throwable t) {
                return false;
            }
        });

        FabricLifecycleListener.register();
        FabricNetworkListener.register();
        FabricMoveListener.register();
        FabricCombatListener.register();
        FabricWorldListener.register();
        FabricCommandListener.register();

        System.out.println("[AC] AntiCheat Fabric 1.21.11 caricato.");
    }

    static void setServer(MinecraftServer s) {
        FabricState.server = s;
    }
}
