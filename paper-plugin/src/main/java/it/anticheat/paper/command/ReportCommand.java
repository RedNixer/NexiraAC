package it.anticheat.paper.command;

import it.anticheat.core.AnticheatCore;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ReportCommand implements CommandExecutor {
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) return true;
        if (args.length < 2) {
            p.sendMessage("§eUso: /report <giocatore> <motivo>");
            return true;
        }
        Player t = Bukkit.getPlayer(args[0]);
        if (t == null) {
            p.sendMessage("§cGiocatore non trovato.");
            return true;
        }
        if (t.getUniqueId().equals(p.getUniqueId())) {
            p.sendMessage("§cNon puoi segnalare te stesso.");
            return true;
        }
        String reason = String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length));
        AnticheatCore.get().report(p.getUniqueId(), p.getName(), t.getUniqueId(), t.getName(), reason);
        p.sendMessage("§aReport inviato. Grazie!");
        return true;
    }
}
