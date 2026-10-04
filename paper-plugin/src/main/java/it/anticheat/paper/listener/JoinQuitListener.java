package it.anticheat.paper.listener;

import it.anticheat.core.AnticheatCore;
import it.anticheat.core.PlayerData;
import it.anticheat.paper.AnticheatPaper;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.Vehicle;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;

public class JoinQuitListener implements Listener {
    private final AnticheatPaper plugin;

    public JoinQuitListener(AnticheatPaper plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        PlayerData d = AnticheatCore.get().data(p.getUniqueId());
        d.name = p.getName();
        d.joinTime = System.currentTimeMillis();
        // sessione dashboard (IP per alts/playtime)
        String ip = "-";
        try {
            if (p.getAddress() != null && p.getAddress().getAddress() != null) {
                ip = p.getAddress().getAddress().getHostAddress();
            }
        } catch (Throwable ignored) {}
        AnticheatCore.get().handleJoin(p.getUniqueId(), p.getName(), ip);
        // Diagnostica admin: visibile in console, cosi si confronta con config.yml
        plugin.getLogger().info("[AC] join " + p.getName()
            + " uuid=" + p.getUniqueId()
            + " admin=" + plugin.isAdmin(p)
            + " (uuid in config: " + AnticheatCore.get().config().adminUuids + ")");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        AnticheatCore.get().setInventoryOpen(e.getPlayer().getUniqueId(), false);
        AnticheatCore.get().handleQuit(e.getPlayer().getUniqueId());
        // teniamo i dati per lo storico (VL + report); rimozione solo con restart.
        // AnticheatCore.get().remove(e.getPlayer().getUniqueId());
    }

    // GUIMove: inventario aperto + movimento = cheat (vanilla sta fermo)
    @EventHandler
    public void onInventoryOpen(InventoryOpenEvent e) {
        if (e.getPlayer() instanceof Player p) {
            AnticheatCore.get().setInventoryOpen(p.getUniqueId(), true);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) {
            AnticheatCore.get().setInventoryOpen(p.getUniqueId(), false);
        }
    }

    // AutoTool: cambio hotbar istantaneo prima di scavare
    @EventHandler
    public void onHeldChange(PlayerItemHeldEvent e) {
        AnticheatCore.get().noteHeldChange(e.getPlayer().getUniqueId());
    }

    // Riptide: il tridente ti spara a 15+ b/s per ~2s (sarebbe Speed sicuro).
    // Tregua solo con incanto Riptide e acqua/pioggia: il lancio normale no.
    @EventHandler
    public void onTridentUse(PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_AIR
                && e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        try {
            if (e.getItem() == null || e.getItem().getType() != org.bukkit.Material.TRIDENT) return;
            if (e.getItem().getEnchantmentLevel(org.bukkit.enchantments.Enchantment.RIPTIDE) <= 0) return;
            if (!p.isInWater() && !p.getWorld().hasStorm()) return;
            AnticheatCore.get().exemptMove(p.getUniqueId(), 2500);
        } catch (Throwable ignored) {}
    }

    // AutoTotem timing: click inventario subito prima del pop
    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p) {
            AnticheatCore.get().noteInventoryClick(p.getUniqueId());
            // GUIMove: anche il click da solo prova l'inventario aperto
            // (copre GUI che non mandano OpenEvent)
            AnticheatCore.get().setInventoryOpen(p.getUniqueId(), true);
        }
    }

    // Anti-falsi-positivi: dopo teleport/respawn/veicoli la posizione salta,
    // il movimento successivo non deve sembrare un cheat.
    @EventHandler
    public void onTeleport(PlayerTeleportEvent e) {
        Location to = e.getTo();
        if (to == null) return;
        AnticheatCore.get().resetMoveState(e.getPlayer().getUniqueId(),
            to.getX(), to.getY(), to.getZ(), to.getWorld().getName());
        AnticheatCore.get().exemptMove(e.getPlayer().getUniqueId(), 2000);
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        Location loc = e.getRespawnLocation();
        AnticheatCore.get().resetMoveState(e.getPlayer().getUniqueId(),
            loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getName());
        AnticheatCore.get().exemptMove(e.getPlayer().getUniqueId(), 2000);
    }

    @EventHandler
    public void onVehicleEnter(VehicleEnterEvent e) {
        if (e.getEntered() instanceof Player p) {
            AnticheatCore.get().exemptMove(p.getUniqueId(), 2000);
        }
    }

    @EventHandler
    public void onVehicleExit(VehicleExitEvent e) {
        if (e.getExited() instanceof Player p) {
            Location loc = p.getLocation();
            AnticheatCore.get().resetMoveState(p.getUniqueId(),
                loc.getX(), loc.getY(), loc.getZ(), loc.getWorld().getName());
            AnticheatCore.get().exemptMove(p.getUniqueId(), 2000);
        }
    }
}
