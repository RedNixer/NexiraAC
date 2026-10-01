package it.anticheat.paper.listener;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * StaffMode: vanish + ispezione inventario dal vivo.
 * Vanish: invisibilità vera (pozione + hidePlayer per tutti i non-admin),
 * gamemode spettatore opzionale no — resta la sopravvivenza così puoi
 * prendere oggetti dall'inventario aperto senza limitazioni creative.
 * Click destro su un player mentre sei in vanish = apri il suo inventario
 * LIVE (condiviso: quello che prendi sparisce davvero a lui).
 */
public class StaffMode implements Listener {

    private static final Set<UUID> VANISHED = ConcurrentHashMap.newKeySet();

    public static boolean isVanished(UUID uuid) {
        return VANISHED.contains(uuid);
    }

    /** Toggle vanish. Ritorna il nuovo stato. */
    public static boolean toggle(Player p) {
        UUID id = p.getUniqueId();
        if (VANISHED.contains(id)) {
            VANISHED.remove(id);
            try {
                p.removePotionEffect(PotionEffectType.INVISIBILITY);
            } catch (Throwable ignored) {}
            try {
                for (Player other : Bukkit.getOnlinePlayers()) {
                    other.showPlayer(Bukkit.getPluginManager().getPlugin("AntiCheat"), p);
                }
            } catch (Throwable ignored) {}
            try {
                p.setGlowing(false);
            } catch (Throwable ignored) {}
            return false;
        }
        VANISHED.add(id);
        try {
            p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY,
                Integer.MAX_VALUE, 0, false, false, false));
        } catch (Throwable ignored) {}
        try {
            for (Player other : Bukkit.getOnlinePlayers()) {
                if (other.equals(p) || other.isOp()) continue;
                try {
                    if (it.anticheat.core.AnticheatCore.get().config().adminUuids
                            .contains(other.getUniqueId())) continue;
                } catch (Throwable ignored2) {}
                other.hidePlayer(Bukkit.getPluginManager().getPlugin("AntiCheat"), p);
            }
        } catch (Throwable ignored) {}
        return true;
    }

    /** Nascondi i vanished ai nuovi entrati (non-admin). */
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player joining = e.getPlayer();
        boolean joiningAdmin = joining.isOp();
        try {
            if (!joiningAdmin && it.anticheat.core.AnticheatCore.get().config()
                    .adminUuids.contains(joining.getUniqueId())) joiningAdmin = true;
        } catch (Throwable ignored) {}
        if (!joiningAdmin) {
            for (UUID id : VANISHED) {
                try {
                    Player v = Bukkit.getPlayer(id);
                    if (v != null) joining.hidePlayer(
                        Bukkit.getPluginManager().getPlugin("AntiCheat"), v);
                } catch (Throwable ignored) {}
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        VANISHED.remove(e.getPlayer().getUniqueId());
    }

    /** Freeze punizione: movimento annullato (l'evento e cancellabile). */
    @EventHandler(ignoreCancelled = false)
    public void onFrozenMove(org.bukkit.event.player.PlayerMoveEvent e) {
        try {
            if (it.anticheat.core.AnticheatCore.get()
                    .data(e.getPlayer().getUniqueId()).frozen) {
                e.setCancelled(true);
            }
        } catch (Throwable ignored) {}
    }

    /**
     * Click destro su player in vanish = inventario live del target.
     * Condiviso: prendi/sposti davvero (niente copia).
     */    @EventHandler(ignoreCancelled = false)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Player target)) return;
        Player staff = e.getPlayer();
        if (!VANISHED.contains(staff.getUniqueId())) return;
        if (staff.getGameMode() == GameMode.CREATIVE) return;
        e.setCancelled(true);
        try {
            staff.openInventory(target.getInventory());
            staff.sendMessage("§7Inventario di §f" + target.getName()
                + " §7(live: quello che prendi sparisce a lui).");
        } catch (Throwable t) {
            staff.sendMessage("§cApertura fallita: " + t.getMessage());
        }
    }
}
