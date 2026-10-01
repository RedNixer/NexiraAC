package it.anticheat.paper.listener;

import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/** Login flood guard: per-IP rate, bad names, join/quit storms. No VL — in or out. */
public class LoginGuard implements Listener {

    private static it.anticheat.core.config.ProtectionConfig cfg() {
        return it.anticheat.core.AnticheatCore.get().protection();
    }

    private final Map<String, Deque<Long>> ipLogins = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> ipFails = new ConcurrentHashMap<>();
    private final Map<String, Deque<Long>> joinQuit = new ConcurrentHashMap<>();

    private static Deque<Long> deque(Map<String, Deque<Long>> map, String key) {
        return map.computeIfAbsent(key, k -> new ConcurrentLinkedDeque<>());
    }

    private static void prune(Deque<Long> dq, long windowMs) {
        long now = System.currentTimeMillis();
        while (!dq.isEmpty() && now - dq.peekFirst() > windowMs) dq.pollFirst();
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        String name = e.getName();
        String ip;
        try {
            ip = e.getAddress().getHostAddress();
        } catch (Throwable t) {
            return;
        }
        // vanilla never makes these
        if (name == null || name.length() > 16 || !name.matches("[a-zA-Z0-9_]+")) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "§cNome non valido.");
            return;
        }
        Deque<Long> logins = deque(ipLogins, ip);
        prune(logins, 10_000);
        logins.addLast(System.currentTimeMillis());
        if (logins.size() > cfg().maxLoginPer10s) {
            Deque<Long> fails = deque(ipFails, ip);
            prune(fails, 600_000);
            fails.addLast(System.currentTimeMillis());
            if (fails.size() >= cfg().loginStrikesForBan) {
                long banMs = (long) cfg().loginBanMinutes * 60_000;
                try {
                    Bukkit.getBanList(org.bukkit.BanList.Type.IP)
                        .addBan(ip, "§cTroppi tentativi di connessione.",
                            new java.util.Date(System.currentTimeMillis() + banMs), "AntiCheat");
                } catch (Throwable ignored) {}
                e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                    "§cTroppi tentativi. Riprova tra " + cfg().loginBanMinutes + " minuti.");
            } else {
                e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    "§cConnessioni troppo rapide. Aspetta qualche secondo.");
            }
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Deque<Long> jq = deque(joinQuit, p.getUniqueId().toString());
        prune(jq, 30_000);
        jq.addLast(System.currentTimeMillis());
        if (jq.size() > cfg().maxJoinQuitPer30s) {
            try {
                Bukkit.getScheduler().runTask(
                    Bukkit.getPluginManager().getPlugin("AntiCheat"),
                    () -> p.kickPlayer("§cRiconnessioni troppo rapide. Aspetta 30 secondi."));
            } catch (Throwable ignored) {}
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // quits stay logged for the join/quit count (30s prune cleans up)
    }
}
