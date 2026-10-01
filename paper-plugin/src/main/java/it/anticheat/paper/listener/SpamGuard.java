package it.anticheat.paper.listener;

import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerEditBookEvent;

/**
 * Livello 4 light — rate limiter gioco (Bukkit puro, niente ProtocolLib).
 * Chat/comandi/tab a raffica = bot, non persone. Libri giganti = book-ban
 * senza passare dalla creativa.
 *
 * - 6 messaggi in 3s: mute 30s (coda, non kick: i player veri litigano).
 * - 8 comandi in 3s: kick (i bot provano permutazioni di comandi).
 * - Libro oltre 20KB totali: droppato + kick (vanilla non ci arriva mai).
 */
@SuppressWarnings("deprecation")
public class SpamGuard implements Listener {

    private final Map<UUID, Deque<Long>> chats = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Long>> cmds = new ConcurrentHashMap<>();
    private final Map<UUID, Long> mutedUntil = new ConcurrentHashMap<>();

    private static boolean storm(Map<UUID, Deque<Long>> map, UUID uuid, long windowMs, int max) {
        Deque<Long> dq = map.computeIfAbsent(uuid, k -> new ConcurrentLinkedDeque<>());
        long now = System.currentTimeMillis();
        while (!dq.isEmpty() && now - dq.peekFirst() > windowMs) dq.pollFirst();
        dq.addLast(now);
        return dq.size() > max;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        Long mute = mutedUntil.get(id);
        if (mute != null && System.currentTimeMillis() < mute) {
            e.setCancelled(true);
            return;
        }
        if (storm(chats, id, 3000, 6)) {
            chats.get(id).clear();
            mutedUntil.put(id, System.currentTimeMillis() + 30_000);
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cStai scrivendo troppo in fretta. Muto 30 secondi.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        // /ac e /report dello staff non contano (test e debug a raffica)
        String msg = e.getMessage().toLowerCase();
        if (msg.startsWith("/ac ") || msg.startsWith("/ac:") || msg.startsWith("/report")) return;
        if (storm(cmds, e.getPlayer().getUniqueId(), 3000, 8)) {
            cmds.get(e.getPlayer().getUniqueId()).clear();
            e.setCancelled(true);
            final Player p = e.getPlayer();
            Bukkit.getScheduler().runTask(
                Bukkit.getPluginManager().getPlugin("AntiCheat"),
                () -> p.kickPlayer("§cTroppi comandi (flood)."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBook(PlayerEditBookEvent e) {
        try {
            int total = 0;
            for (String page : e.getNewBookMeta().getPages()) {
                if (page != null) total += page.length();
            }
            if (total > 20000) {
                e.setCancelled(true);
                final Player p = e.getPlayer();
                Bukkit.getScheduler().runTask(
                    Bukkit.getPluginManager().getPlugin("AntiCheat"),
                    () -> p.kickPlayer("§cLibro non valido (dati eccessivi)."));
            }
        } catch (Throwable ignored) {}
    }
}
