package it.anticheat.paper.listener;

import it.anticheat.core.AnticheatCore;
import it.anticheat.paper.AnticheatPaper;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;

/** Riceve dalla mod client: "anticheat:hello" (verifica) e "anticheat:opengui" (tasto F8). */
public class ClientChannelListener implements PluginMessageListener {
    private final AnticheatPaper plugin;

    public ClientChannelListener(AnticheatPaper plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (channel.equals("anticheat:opengui")) {
            // Tasto F8 premuto: apri GUI solo se admin (il client non e mai fidato)
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (plugin.isAdmin(player)) plugin.openGui(player);
                else player.sendMessage("§cNon hai accesso all'anticheat.");
            });
            return;
        }
        if (!channel.equals("anticheat:hello")) return;
        String s = new String(message, StandardCharsets.UTF_8);
        String ver = "?";
        for (String part : s.split("[;\\n]")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && kv[0].trim().equalsIgnoreCase("ver")) ver = kv[1].trim();
        }
        String min = AnticheatCore.get().config().clientMinVersion;
        boolean ok = compareVersions(ver, min) >= 0;
        AnticheatCore.get().data(player.getUniqueId()).name = player.getName();
        // brand dashboard: mod presente + versione
        AnticheatCore.get().sessions().brand(player.getUniqueId(), "nexira-client", ver);
        // segna verificato
        for (org.bukkit.plugin.Plugin pl : Bukkit.getPluginManager().getPlugins()) {
            if (pl instanceof AnticheatPaper paper) {
                paper.markVerified(player.getUniqueId(), ver, ok);
                break;
            }
        }
        if (AnticheatCore.get().config().verbose) {
            Bukkit.getLogger().info("[AC] hello da " + player.getName() + " ver=" + ver + " ok=" + ok);
        }
    }

    static int compareVersions(String a, String b) {
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
