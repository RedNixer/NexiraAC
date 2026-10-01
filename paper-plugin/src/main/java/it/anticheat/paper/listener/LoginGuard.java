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

/**
 * Livello 2 — connection/login flood guard.
 * Bot join/quit, nomi impossibili, stesso IP che martella.
 * Niente VL: qui dentro o entri o no.
 *
 * - Max 5 login dallo stesso IP in 10s, poi kick (streak: ban-ip 10 min).
 * - Nomi oltre 16 char o fuori [a-zA-Z0-9_]: kick diretto (vanilla non li fa).
 * - Join/quit 3 volte in 30s dallo stesso account: kick con attesa.
 */
public class LoginGuard implements Listener {

    private static final int MAX_LOGIN_10S = 5;
    private static final int MAX_JQ_30S = 3;

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
        // nomi impossibili in vanilla
        if (name == null || name.length() > 16 || !name.matches("[a-zA-Z0-9_]+")) {
            e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER, "§cNome non valido.");
            return;
        }
        Deque<Long> logins = deque(ipLogins, ip);
        prune(logins, 10_000);
        logins.addLast(System.currentTimeMillis());
        if (logins.size() > MAX_LOGIN_10S) {
            Deque<Long> fails = deque(ipFails, ip);
            prune(fails, 600_000);
            fails.addLast(System.currentTimeMillis());
            if (fails.size() >= 3) {
                // 3 raffiche in 10 min: ban-ip 10 minuti
                try {
                    Bukkit.getBanList(org.bukkit.BanList.Type.IP)
                        .addBan(ip, "§cTroppi tentativi di connessione.",
                            new java.util.Date(System.currentTimeMillis() + 600_000), "AntiCheat");
                } catch (Throwable ignored) {}
                e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, "§cTroppi tentativi. Riprova tra 10 minuti.");
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
        if (jq.size() > MAX_JQ_30S) {
            try {
                Bukkit.getScheduler().runTask(
                    Bukkit.getPluginManager().getPlugin("AntiCheat"),
                    () -> p.kickPlayer("§cRiconnessioni troppo rapide. Aspetta 30 secondi."));
            } catch (Throwable ignored) {}
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        // il quit resta nel log per il conteggio join/quit (prune a 30s lo pulisce)
    }
}
