package it.anticheat.fabric.listener;

import it.anticheat.core.AnticheatCore;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * Stato condiviso tra i listener Fabric (equivalente dei field
 * privati di AnticheatFabric prima dello split Fase 0).
 */
public final class FabricState {
    private FabricState() {}

    public static volatile MinecraftServer server;
    public static volatile Path configFile;

    public static final Map<UUID, double[]> lastPos = new ConcurrentHashMap<>();
    public static final Map<UUID, Long> lastTick = new ConcurrentHashMap<>();
    public static final Map<UUID, Integer> lastHeldSlot = new ConcurrentHashMap<>();

    public static MinecraftServer server() {
        return server;
    }

    public static boolean isAdmin(ServerPlayer p) {
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

    public static boolean isAdminCommand(net.minecraft.commands.CommandSourceStack src) {
        if (!src.isPlayer()) return true; // console sempre admin
        return isAdmin(src.getPlayer());
    }

    public static int compareVersions(String a, String b) {
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
