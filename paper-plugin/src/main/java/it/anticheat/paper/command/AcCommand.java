package it.anticheat.paper.command;

import it.anticheat.core.AnticheatCore;
import it.anticheat.core.PlayerData;
import it.anticheat.core.config.AnticheatConfig;
import it.anticheat.paper.AnticheatPaper;
import it.anticheat.paper.gui.AdminGui;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class AcCommand implements CommandExecutor, TabCompleter {
    private final AnticheatPaper plugin;
    private final AdminGui gui;

    public AcCommand(AnticheatPaper plugin, AdminGui gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        // /ac add <giocatore>: da console sempre, in gioco solo OP o admin esistente.
        // Aggiunge l'UUID e lo salva subito in config.yml (niente restart).
        if (args.length >= 1 && args[0].equalsIgnoreCase("add")) {
            if (sender instanceof Player p && !p.isOp() && !plugin.isAdmin(p)) {
                p.sendMessage("§cSolo OP o admin.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("§eUso: /ac add <giocatore>");
                return true;
            }
            Player online = Bukkit.getPlayerExact(args[1]);
            java.util.UUID uuid;
            String name;
            if (online != null) {
                uuid = online.getUniqueId();
                name = online.getName();
            } else {
                OfflinePlayer off = Bukkit.getOfflinePlayer(args[1]);
                if (!off.hasPlayedBefore() && !off.isOnline()) {
                    sender.sendMessage("§cGiocatore mai entrato nel server: " + args[1]);
                    return true;
                }
                uuid = off.getUniqueId();
                name = off.getName() != null ? off.getName() : args[1];
            }
            if (plugin.addAdmin(uuid)) sender.sendMessage("§a" + name + " aggiunto come admin (" + uuid + ").");
            else sender.sendMessage("§e" + name + " era già admin.");
            return true;
        }
        // /ac reload da console sempre permesso (serve proprio quando nessuno e riconosciuto admin)
        if (args.length == 1 && args[0].equalsIgnoreCase("reload") && !(sender instanceof Player)) {
            sender.sendMessage(plugin.reloadAnticheatConfig() ? "§aConfig ricaricata." : "§cReload fallito, vedi console.");
            return true;
        }
        // ... (add e reload gestiti sopra)
        if (!(sender instanceof Player)) {
            // console: vl/reset/cps/testmode oltre ad add/reload
            if (args.length >= 1 && args[0].equalsIgnoreCase("testmode")) {
                AnticheatConfig cfg = AnticheatCore.get().config();
                cfg.testMode = !cfg.testMode;
                sender.sendMessage(plugin.persistConfig()
                    ? (cfg.testMode ? "Test-mode ATTIVO: nessuna punizione." : "Test-mode spento.")
                    : "Salvataggio fallito, vedi log.");
                return true;
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("cps")) {
                if (args.length < 2) {
                    sender.sendMessage("Limite CPS attuale: " + AnticheatCore.get().config().cpsLimit);
                    return true;
                }
                try {
                    int n = Integer.parseInt(args[1]);
                    if (n < 10 || n > 100) { sender.sendMessage("Range valido: 10-100."); return true; }
                    AnticheatCore.get().config().cpsLimit = n;
                    sender.sendMessage(plugin.persistConfig()
                        ? "Limite CPS impostato a " + n + "."
                        : "Salvataggio fallito, vedi log.");
                } catch (NumberFormatException ex) {
                    sender.sendMessage("Numero non valido. Uso: ac cps <10-100>");
                }
                return true;
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("stats")) {
                sendStats(sender, args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : null);
                return true;
            }
            if (args.length >= 1 && args[0].equalsIgnoreCase("experimental")) {
                AnticheatConfig cfg = AnticheatCore.get().config();
                cfg.experimentalChecks = !cfg.experimentalChecks;
                sender.sendMessage(plugin.persistConfig()
                    ? ("Check sperimentali " + (cfg.experimentalChecks ? "ATTIVI." : "spenti."))
                    : "Salvataggio fallito, vedi log.");
                return true;
            }
            if (args.length >= 2 && args[0].equalsIgnoreCase("debug")) {
                toggleDebug(sender, args[1]);
                return true;
            }
            if (args.length >= 2
                    && (args[0].equalsIgnoreCase("vl") || args[0].equalsIgnoreCase("reset"))) {
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) { sender.sendMessage("Player offline."); return true; }
                if (args[0].equalsIgnoreCase("vl")) {
                    PlayerData d = AnticheatCore.get().data(t.getUniqueId());
                    sender.sendMessage(d.name + " VL=" + d.totalVl()
                        + " client=" + d.clientStatus + " " + d.clientVersion + " " + d.violations);
                } else {
                    AnticheatCore.get().resetVl(t.getUniqueId());
                    sender.sendMessage("VL resettate per " + t.getName());
                }
                return true;
            }
            sender.sendMessage("Solo in-game (oppure ac <add|reload|vl|reset|stats|debug|cps|testmode|experimental>).");
            return true;
        }
        Player p = (Player) sender;
        // /ac reload in-game: basta essere OP (non serve essere gia admin del plugin)
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) {
            if (!p.isOp() && !plugin.isAdmin(p)) {
                p.sendMessage("§cSolo OP o admin.");
                return true;
            }
            p.sendMessage(plugin.reloadAnticheatConfig() ? "§aConfig ricaricata." : "§cReload fallito, vedi console.");
            return true;
        }
        if (!plugin.isAdmin(p)) {
            p.sendMessage("§cNon hai accesso all'anticheat.");
            p.sendMessage("§7Il tuo UUID: §f" + p.getUniqueId());
            p.sendMessage("§7UUID in config: §f" + AnticheatCore.get().config().adminUuids);
            p.sendMessage("§7Chiedi in console: §fac add " + p.getName() + " §7(oppure /ac reload da OP).");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            gui.openOverview(p);
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "vl" -> {
                if (args.length < 2) { p.sendMessage("§eUso: /ac vl <player>"); return true; }
                Player t = Bukkit.getPlayer(args[1]);
                if (t == null) { p.sendMessage("§cPlayer offline."); return true; }
                PlayerData d = AnticheatCore.get().data(t.getUniqueId());
                p.sendMessage("§6" + d.name + " §7VL=" + d.totalVl() + " client=" + d.clientStatus + " " + d.clientVersion + " viol=" + d.violations);
            }
            case "reset" -> {
                if (args.length < 2) { p.sendMessage("§eUso: /ac reset <player>"); return true; }
                Player t = Bukkit.getPlayer(args[1]);
                if (t == null) { p.sendMessage("§cPlayer offline."); return true; }
                AnticheatCore.get().resetVl(t.getUniqueId());
                p.sendMessage("§aVL resettate per " + t.getName());
            }
            case "reports" -> gui.openReports(p);
            case "settings" -> gui.openSettings(p);
            case "testmode" -> {
                AnticheatConfig cfg = AnticheatCore.get().config();
                cfg.testMode = !cfg.testMode;
                if (plugin.persistConfig())
                    p.sendMessage(cfg.testMode
                        ? "§eTest-mode ATTIVO: nessuna punizione, solo log."
                        : "§aTest-mode spento: punizioni attive.");
                else p.sendMessage("§cSalvataggio fallito, vedi console.");
            }
            case "experimental" -> {
                AnticheatConfig cfg = AnticheatCore.get().config();
                cfg.experimentalChecks = !cfg.experimentalChecks;
                if (plugin.persistConfig())
                    p.sendMessage(cfg.experimentalChecks
                        ? "§eCheck sperimentali ATTIVI (occhio ai falsi positivi)."
                        : "§aCheck sperimentali spenti.");
                else p.sendMessage("§cSalvataggio fallito, vedi console.");
            }
            case "debug" -> toggleDebug(p, args.length >= 2 ? args[1] : p.getName());
            case "cps" -> {
                if (args.length < 2) {
                    p.sendMessage("§dLimite CPS attuale: §f" + AnticheatCore.get().config().cpsLimit
                        + " §7(uso: /ac cps <10-100>)");
                    return true;
                }
                try {
                    int n = Integer.parseInt(args[1]);
                    if (n < 10 || n > 100) { p.sendMessage("§cRange valido: 10-100."); return true; }
                    AnticheatCore.get().config().cpsLimit = n;
                    if (plugin.persistConfig()) p.sendMessage("§aLimite CPS impostato a " + n + ".");
                    else p.sendMessage("§cSalvataggio fallito, vedi console.");
                } catch (NumberFormatException ex) {
                    p.sendMessage("§cNumero non valido. Uso: /ac cps <10-100>");
                }
            }
            case "download" -> p.sendMessage("§aScarica la mod client da Modrinth (anticheat-client) e mettila in mods/. Poi rientra per il badge verificato.");
            case "stats" -> sendStats(p, args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : p);
            case "reload" -> p.sendMessage("§eUso in-game: /ac reload (serve OP). Da console: ac reload");
            case "add" -> p.sendMessage("§eUso: /ac add <giocatore> (serve OP o admin). Da console: ac add <giocatore>");
            default -> p.sendMessage("§e/ac [gui|settings|vl|reset|stats|debug|reports|cps|testmode|experimental|download|reload|add]");
        }
        return true;
    }

    /** Diagnostica live su console per un player (place/break/click/move). */
    public static void toggleDebug(CommandSender to, String name) {
        Player t = Bukkit.getPlayerExact(name);
        java.util.UUID id;
        String nm;
        if (t != null) {
            id = t.getUniqueId();
            nm = t.getName();
        } else {
            OfflinePlayer off = Bukkit.getOfflinePlayer(name);
            if (!off.hasPlayedBefore() && !off.isOnline()) {
                to.sendMessage("§cPlayer mai entrato: " + name);
                return;
            }
            id = off.getUniqueId();
            nm = off.getName() != null ? off.getName() : name;
        }
        boolean on = !AnticheatCore.get().isDebug(id);
        AnticheatCore.get().setDebug(id, on);
        to.sendMessage(on
            ? "§eDebug ON per " + nm + ": guarda la console mentre gioca."
            : "§aDebug OFF per " + nm + ".");
    }

    /** Diagnostica live: CPS misurati, streak, contatori. Null-safe sul target. */
    public static void sendStats(CommandSender to, Player t) {
        if (t == null) {
            to.sendMessage("§cPlayer offline (uso: /ac stats <player>).");
            return;
        }
        PlayerData d = AnticheatCore.get().data(t.getUniqueId());
        long now = System.currentTimeMillis();
        int cps = 0;
        for (long ts : d.clickTimes) if (now - ts <= 1000) cps++;
        to.sendMessage("§6§l" + d.name + " §7VL tot §f" + d.totalVl()
            + " §7ping §f" + d.ping
            + " §7client §f" + d.clientStatus + " " + d.clientVersion);
        to.sendMessage("§7CPS(1s): §f" + cps + " §8(limite " + AnticheatCore.get().config().cpsLimit + ")"
            + " §7viol: §f" + (d.violations.isEmpty() ? "-" : d.violations));
        to.sendMessage("§7streak spd/fly-step/reach/kb: §f" + d.speedStreak + "/" + d.airTicks
            + "/" + Integer.bitCount(d.reachWindow) + "x/6/" + d.groundSpoofStreak
            + " §7scaf/brk: §f" + d.scaffoldStreak + "/" + d.fastBreakStreak
            + " §7xray: §f" + d.xrayOres + "/" + d.xrayStone
            + " §7exempt: §f" + AnticheatCore.get().isExempt(t.getUniqueId()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("gui", "settings", "vl", "reset", "stats", "debug", "reports", "cps", "testmode", "experimental", "download", "reload", "add"))
                if (s.startsWith(args[0].toLowerCase())) out.add(s);
        } else if (args.length == 2
                && (args[0].equalsIgnoreCase("vl") || args[0].equalsIgnoreCase("reset") || args[0].equalsIgnoreCase("add") || args[0].equalsIgnoreCase("stats") || args[0].equalsIgnoreCase("debug"))) {
            for (Player pl : Bukkit.getOnlinePlayers())
                if (pl.getName().toLowerCase().startsWith(args[1].toLowerCase())) out.add(pl.getName());
        }
        return out;
    }
}
