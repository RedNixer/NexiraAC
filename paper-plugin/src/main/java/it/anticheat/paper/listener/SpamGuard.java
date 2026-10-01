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

/** Chat/command rate limits plus oversized-book kick. Bukkit only, no ProtocolLib. */
@SuppressWarnings("deprecation")
public class SpamGuard implements Listener {

    private static it.anticheat.core.config.ProtectionConfig cfg() {
        return it.anticheat.core.AnticheatCore.get().protection();
    }

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
        if (storm(chats, id, 3000, cfg().maxChatPer3s)) {
            chats.get(id).clear();
            mutedUntil.put(id, System.currentTimeMillis() + (long) cfg().chatMuteSeconds * 1000);
            e.setCancelled(true);
            e.getPlayer().sendMessage("§cStai scrivendo troppo in fretta. Muto "
                + cfg().chatMuteSeconds + " secondi.");
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        // staff /ac traffic doesn't count
        String msg = e.getMessage().toLowerCase();
        if (msg.startsWith("/ac ") || msg.startsWith("/ac:") || msg.startsWith("/report")) return;
        if (storm(cmds, e.getPlayer().getUniqueId(), 3000, cfg().maxCmdPer3s)) {
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
            if (total > cfg().bookMaxChars) {
                e.setCancelled(true);
                final Player p = e.getPlayer();
                Bukkit.getScheduler().runTask(
                    Bukkit.getPluginManager().getPlugin("AntiCheat"),
                    () -> p.kickPlayer("§cLibro non valido (dati eccessivi)."));
            }
        } catch (Throwable ignored) {}
    }
}
