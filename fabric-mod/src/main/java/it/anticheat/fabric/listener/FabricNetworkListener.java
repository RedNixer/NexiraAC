package it.anticheat.fabric.listener;

import it.anticheat.core.AnticheatCore;
import it.anticheat.core.ClientStatus;
import it.anticheat.core.PlayerData;
import it.anticheat.fabric.HelloPayload;
import it.anticheat.fabric.OpenGuiPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/** Payload client (hello + F8) (logica invariata Fase 0). */
public final class FabricNetworkListener {
    private FabricNetworkListener() {}

    public static void register() {
        PayloadTypeRegistry.playC2S().register(HelloPayload.TYPE, HelloPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HelloPayload.TYPE, HelloPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(OpenGuiPayload.TYPE, OpenGuiPayload.CODEC);

        // Tasto F8 dal client: apri la GUI admin (come Paper openGui).
        ServerPlayNetworking.registerGlobalReceiver(OpenGuiPayload.TYPE, (payload, context) -> {
            ServerPlayer p = context.player();
            FabricState.server().execute(() -> {
                try {
                    if (!FabricState.isAdmin(p)) {
                        p.sendSystemMessage(Component.literal("Non hai accesso all'anticheat."));
                        return;
                    }
                    it.anticheat.fabric.gui.FabricAdminGui.openOverview(p);
                } catch (Throwable ignored) {}
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(HelloPayload.TYPE, (payload, context) -> {
            String ver = HelloPayload.extractVersion(payload.text());
            boolean ok = FabricState.compareVersions(ver, AnticheatCore.get().config().clientMinVersion) >= 0;
            UUID uuid = context.player().getUUID();
            PlayerData d = AnticheatCore.get().data(uuid);
            d.name = context.player().getScoreboardName();
            d.clientVersion = ver;
            d.clientStatus = ok ? ClientStatus.VERIFIED : ClientStatus.UNVERIFIED;
        });
    }
}
