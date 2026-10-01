package it.anticheat.paper;

import it.anticheat.core.AnticheatCore;
import it.anticheat.core.ClientStatus;
import it.anticheat.core.PlayerData;
import it.anticheat.core.config.AnticheatConfig;
import it.anticheat.core.storage.MemoryStorage;
import it.anticheat.paper.command.AcCommand;
import it.anticheat.paper.command.ReportCommand;
import it.anticheat.paper.gui.AdminGui;
import it.anticheat.paper.listener.ClientChannelListener;
import it.anticheat.paper.listener.CombatListener;
import it.anticheat.paper.listener.JoinQuitListener;
import it.anticheat.paper.listener.LoginGuard;
import it.anticheat.paper.listener.MovementListener;
import it.anticheat.paper.listener.SpamGuard;
import it.anticheat.paper.listener.StaffMode;
import it.anticheat.paper.listener.WorldListener;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.util.UUID;

public class AnticheatPaper extends JavaPlugin {

    /** Versione mostrata in console/GUI: se non vedi 0.2.0, stai usando un jar vecchio! */
    public static final String PLUGIN_VERSION = "0.2.0";

    private AdminGui gui;

    @Override
    public void onEnable() {
        saveDefaultConfig(); // crea config.yml se manca (quello di default e nella cartella resources? vedi sotto)
        Path configFile = getDataFolder().toPath().resolve("config.yml");
        AnticheatConfig cfg;
        try {
            cfg = AnticheatConfig.load(configFile);
        } catch (Exception e) {
            getLogger().warning("Config illeggibile, uso default: " + e.getMessage());
            cfg = new AnticheatConfig();
        }
        final AnticheatConfig config = cfg;
        // Punizioni per-check (punishments.json, creato col default se manca)
        try {
            AnticheatCore.get().setPunishments(
                it.anticheat.core.config.PunishmentConfig.load(
                    getDataFolder().toPath().resolve("punishments.json")));
        } catch (Exception e) {
            getLogger().warning("Punishments illeggibili, uso default: " + e.getMessage());
        }
        // Protezione server (protection.json, creato col default se manca)
        try {
            AnticheatCore.get().setProtection(
                it.anticheat.core.config.ProtectionConfig.load(
                    getDataFolder().toPath().resolve("protection.json")));
        } catch (Exception e) {
            getLogger().warning("Protection illeggibile, uso default: " + e.getMessage());
        }

        AnticheatCore.get().init(config, new MemoryStorage(), new AnticheatCore.ActionHandler() {
            @Override
            public void warn(UUID player, String check, int totalVl) {
                Player p = Bukkit.getPlayer(player);
                if (p != null) p.sendMessage("§e[AC] Comportamento sospetto (" + check + "). Rallenta o verrai punito.");
            }

            @Override
            public void kick(UUID player, String reason) {
                Bukkit.getScheduler().runTask(AnticheatPaper.this, () -> {
                    Player p = Bukkit.getPlayer(player);
                    if (p != null) p.kickPlayer(reason);
                });
            }

            @Override
            public void ban(UUID player, String reason) {
                tempban(player, reason, -1);
            }

            @Override
            public void tempban(UUID player, String reason, long expiresAtMs) {
                Bukkit.getScheduler().runTask(AnticheatPaper.this, () -> {
                    PlayerData d = AnticheatCore.get().data(player);
                    java.util.Date exp = expiresAtMs < 0 ? null : new java.util.Date(expiresAtMs);
                    Bukkit.getBanList(BanList.Type.NAME).addBan(d.name, reason, exp, "AntiCheat");
                    Player p = Bukkit.getPlayer(player);
                    if (p != null) p.kickPlayer(reason + (exp == null ? "" : " (fino al " + exp + ")"));
                });
            }

            @Override
            public void freeze(UUID player, boolean on) {
                PlayerData d = AnticheatCore.get().data(player);
                d.frozen = on;
                Bukkit.getScheduler().runTask(AnticheatPaper.this, () -> {
                    Player p = Bukkit.getPlayer(player);
                    if (p != null) p.sendMessage(on
                        ? "§cSei stato congelato dallo staff. Non muoverti."
                        : "§aScongelato, puoi muoverti.");
                });
            }

            @Override
            public void setback(UUID player) {
                Bukkit.getScheduler().runTask(AnticheatPaper.this, () -> {
                    Player p = Bukkit.getPlayer(player);
                    if (p == null) return;
                    PlayerData d = AnticheatCore.get().data(player);
                    if (!d.hasGroundPos) return;
                    if (!d.lastGroundWorld.equals(p.getWorld().getName())) return;
                    org.bukkit.Location safe = new org.bukkit.Location(p.getWorld(),
                        Math.floor(d.lastGroundX) + 0.5, d.lastGroundY,
                        Math.floor(d.lastGroundZ) + 0.5, p.getYaw(), p.getPitch());
                    p.teleport(safe);
                    p.sendMessage("§e[AC] Movimento irregolare: riposizionato.");
                });
            }

            @Override
            public void notifyStaff(String message) {
                // Fase A: console SEMPRE (parita Fabric).
                getLogger().info(message.replaceAll("§.", ""));
                for (Player p : Bukkit.getOnlinePlayers()) {
                    if (isAdmin(p)) p.sendMessage(message);
                }
            }
        });

        // Esenti dai controlli: permesso anticheat.exempt + lista exempt-uuids (Fase A).
        AnticheatCore.get().setExemptChecker(uuid -> {
            try {
                if (AnticheatCore.get().config().exemptUuids.contains(uuid)) return true;
            } catch (Throwable ignored) {}
            Player pl = Bukkit.getPlayer(uuid);
            return pl != null && pl.hasPermission("anticheat.exempt");
        });

        // ProtocolLib (opzionale): check pacchetto se installato e abilitato.
        // La classe bridge tocca l'API ProtocolLib: viene caricata SOLO qui dentro,
        // quindi senza ProtocolLib non succede niente (niente crash).
        if (AnticheatCore.get().config().packetChecks
                && getServer().getPluginManager().getPlugin("ProtocolLib") != null) {
            try {
                new it.anticheat.paper.packet.PacketBridge(this);
            } catch (Throwable t) {
                getLogger().warning("[AC] Hook ProtocolLib fallito: " + t.getMessage());
            }
            try {
                new it.anticheat.paper.packet.ExploitBridge(this);
            } catch (Throwable t) {
                getLogger().warning("[AC] ExploitBridge fallito: " + t.getMessage());
            }
        } else {
            getLogger().info("[AC] Modalita solo-eventi (ProtocolLib assente o disattivato).");
        }

        gui = new AdminGui(this);

        getServer().getPluginManager().registerEvents(new MovementListener(), this);
        getServer().getPluginManager().registerEvents(new CombatListener(), this);
        getServer().getPluginManager().registerEvents(new WorldListener(), this);
        getServer().getPluginManager().registerEvents(new StaffMode(), this);
        getServer().getPluginManager().registerEvents(new JoinQuitListener(this), this);
        getServer().getPluginManager().registerEvents(new LoginGuard(), this);
        getServer().getPluginManager().registerEvents(new SpamGuard(), this);
        getServer().getPluginManager().registerEvents(gui, this);

        ClientChannelListener channel = new ClientChannelListener(this);
        getServer().getMessenger().registerIncomingPluginChannel(this, "anticheat:hello", channel);
        getServer().getMessenger().registerIncomingPluginChannel(this, "anticheat:opengui", channel);
        // NOTA: nessun invio periodico verso il client. Paper manda byte raw mentre
        // il client Fabric si aspetta stringhe con prefisso VarInt: decodifica fallita = kick.
        // L'hello lo invia il client da solo al primo tick utile (canSend), lato server basta ricevere.

        AcCommand ac = new AcCommand(this, gui);
        getCommand("ac").setExecutor(ac);
        getCommand("ac").setTabCompleter(ac);
        getCommand("report").setExecutor(new ReportCommand());

        // Decay VL ogni 60s per ridurre falsi positivi accumulati
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, () -> {
            for (PlayerData d : AnticheatCore.get().allPlayers().values()) d.decay(1);
        }, 1200L, 1200L);

        // Tick server per il core (multibreak: rotture nello stesso tick)
        Bukkit.getScheduler().runTaskTimer(this, () -> AnticheatCore.get().serverTick(), 1L, 1L);

        getLogger().info("AntiCheat v" + PLUGIN_VERSION + " abilitato (Paper 1.21.11). Admin UUID: " + config.adminUuids.size());
    }

    public boolean isAdmin(Player p) {
        AnticheatConfig c = AnticheatCore.get().config();
        if (c.adminUuids.contains(p.getUniqueId())) return true;
        return c.usePermissionToo && p.hasPermission("anticheat.admin");
    }

    public boolean isAdminUuid(UUID uuid) {
        AnticheatConfig c = AnticheatCore.get().config();
        if (c.adminUuids.contains(uuid)) return true;
        if (!c.usePermissionToo) return false;
        Player p = Bukkit.getPlayer(uuid);
        return p != null && p.hasPermission("anticheat.admin");
    }

    public void markVerified(UUID uuid, String version, boolean ok) {
        PlayerData d = AnticheatCore.get().data(uuid);
        d.clientVersion = version;
        d.clientStatus = ok ? ClientStatus.VERIFIED : ClientStatus.UNVERIFIED;
    }

    /** Ricarica config.yml senza riavviare. Ritorna true se ok. */
    public boolean reloadAnticheatConfig() {
        try {
            AnticheatConfig cfg = AnticheatConfig.load(getDataFolder().toPath().resolve("config.yml"));
            AnticheatCore.get().init(cfg, AnticheatCore.get().storage(), null); // storage e azioni invariati
            getLogger().info("Config ricaricata. Admin UUID: " + cfg.adminUuids);
            return true;
        } catch (Exception e) {
            getLogger().warning("Reload config fallito: " + e.getMessage());
            return false;
        }
    }

    /**
     * Aggiunge un admin e lo salva subito in config.yml.
     * Ritorna false se era gia admin.
     */
    public boolean addAdmin(UUID uuid) {
        AnticheatConfig c = AnticheatCore.get().config();
        if (c.adminUuids.contains(uuid)) return false;
        c.adminUuids.add(uuid);
        try {
            c.save(getDataFolder().toPath().resolve("config.yml"));
            getLogger().info("Admin aggiunto: " + uuid + " (tot " + c.adminUuids.size() + ")");
        } catch (Exception e) {
            getLogger().warning("Salvataggio config fallito: " + e.getMessage());
        }
        return true;
    }
    public void openGui(Player admin) {
        if (gui != null) gui.openOverview(admin);
    }

    /** Scrive tutte le impostazioni attuali in config.yml (usato dalla GUI). */
    public boolean persistConfig() {
        try {
            AnticheatCore.get().config().saveFull(getDataFolder().toPath().resolve("config.yml"));
            return true;
        } catch (Exception e) {
            getLogger().warning("Salvataggio config fallito: " + e.getMessage());
            return false;
        }
    }
}
