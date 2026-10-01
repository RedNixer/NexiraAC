package it.anticheat.fabric.listener;

import it.anticheat.core.AnticheatCore;
import it.anticheat.core.ClientStatus;
import it.anticheat.core.PlayerData;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

/** Join/quit + exempt respawn/cambio-mondo/veicoli (Fase 2, API verificate nei sources jar). */
public final class FabricLifecycleListener {
    private FabricLifecycleListener() {}

    public static void register() {
        ServerLifecycleEvents.SERVER_STARTED.register(s -> FabricState.server = s);
        ServerLifecycleEvents.SERVER_STOPPED.register(s -> { if (FabricState.server == s) FabricState.server = null; });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer p = handler.getPlayer();
            PlayerData d = AnticheatCore.get().data(p.getUUID());
            d.name = p.getScoreboardName();
            d.joinTime = System.currentTimeMillis();
            d.clientStatus = ClientStatus.MISSING;
            FabricState.lastPos.put(p.getUUID(), new double[]{p.getX(), p.getY(), p.getZ()});
            FabricState.lastTick.put(p.getUUID(), System.currentTimeMillis());
        });
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
            FabricState.lastPos.remove(handler.getPlayer().getUUID()));

        // Respawn (come PlayerRespawnEvent Paper): reset tracking + tregua 2s.
        // Firma reale: afterRespawn(oldPlayer, newPlayer, alive).
        net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents.AFTER_RESPAWN.register(
            (oldPlayer, newPlayer, alive) -> {
                try {
                    String worldKey;
                    try {
                        worldKey = newPlayer.level().dimension().toString();
                    } catch (Throwable ignored) {
                        worldKey = "";
                    }
                    AnticheatCore.get().resetMoveState(newPlayer.getUUID(),
                        newPlayer.getX(), newPlayer.getY(), newPlayer.getZ(), worldKey);
                    AnticheatCore.get().exemptMove(newPlayer.getUUID(), 2000);
                    AnticheatCore.get().exemptFight(newPlayer.getUUID(), 2000);
                    FabricState.lastPos.put(newPlayer.getUUID(),
                        new double[]{newPlayer.getX(), newPlayer.getY(), newPlayer.getZ()});
                    FabricState.lastTick.put(newPlayer.getUUID(), System.currentTimeMillis());
                } catch (Throwable ignored) {}
            });

        // Cambio mondo (come teleport Paper tra mondi): la vecchia safe-pos non vale piu.
        // Firma reale: afterChangeWorld(player, origin, destination).
        net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD.register(
            (player, origin, destination) -> {
                try {
                    String worldKey;
                    try {
                        worldKey = player.level().dimension().toString();
                    } catch (Throwable ignored) {
                        worldKey = "";
                    }
                    AnticheatCore.get().resetMoveState(player.getUUID(),
                        player.getX(), player.getY(), player.getZ(), worldKey);
                    AnticheatCore.get().exemptMove(player.getUUID(), 2000);
                } catch (Throwable ignored) {}
            });
    }
}
