package it.anticheat.paper.gui;

import it.anticheat.core.AnticheatCore;
import it.anticheat.core.Check;
import it.anticheat.core.PlayerData;
import it.anticheat.core.config.AnticheatConfig;
import it.anticheat.core.model.Report;
import it.anticheat.core.model.Violation;
import it.anticheat.paper.AnticheatPaper;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * GUI admin: Sospetti + Dettaglio + Report + Impostazioni (soglie e check on/off live).
 */
public class AdminGui implements Listener {
    public static final String TITLE_SUSPECTS = "AC - Sospetti";
    public static final String TITLE_REPORTS = "AC - Report";
    public static final String TITLE_SETTINGS = "AC - Impostazioni";
    private static final String TITLE_DETAIL = "AC: ";

    private final AnticheatPaper plugin;

    /** Materiali per ogni check nel pannello impostazioni. */
    private static final Map<String, Material> CHECK_ICON = new LinkedHashMap<>();
    static {
        CHECK_ICON.put("Speed", Material.SUGAR);
        CHECK_ICON.put("Fly", Material.FEATHER);
        CHECK_ICON.put("NoFall", Material.HAY_BLOCK);
        CHECK_ICON.put("Step", Material.OAK_STAIRS);
        CHECK_ICON.put("Scaffold", Material.SCAFFOLDING);
        CHECK_ICON.put("Reach", Material.BOW);
        CHECK_ICON.put("KillAura", Material.DIAMOND_SWORD);
        CHECK_ICON.put("AutoClicker", Material.NOTE_BLOCK);
        CHECK_ICON.put("FastBreak", Material.DIAMOND_PICKAXE);
        CHECK_ICON.put("AutoTotem", Material.TOTEM_OF_UNDYING);
        CHECK_ICON.put("XRay", Material.DIAMOND_ORE);
        CHECK_ICON.put("Sprint", Material.RABBIT_FOOT);
        CHECK_ICON.put("NoSlow", Material.SOUL_SAND);
        CHECK_ICON.put("Timer", Material.CLOCK);
        CHECK_ICON.put("Jesus", Material.WATER_BUCKET);
        CHECK_ICON.put("GUIMove", Material.CHEST);
        CHECK_ICON.put("Multitask", Material.SHIELD);
        CHECK_ICON.put("Spider", Material.SPIDER_EYE);
        CHECK_ICON.put("AimSnap", Material.SPECTRAL_ARROW);
        CHECK_ICON.put("AimLock", Material.CROSSBOW);
    }

    private static final int[] PAGE1_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 37, 38, 39, 40, 41};
    private static final int[] PAGE2_SLOTS = {19, 20, 21, 22};
    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43};

    public AdminGui(AnticheatPaper plugin) {
        this.plugin = plugin;
    }

    // ---------- helpers ----------

    private ItemStack pane() {
        return pane(Material.BLACK_STAINED_GLASS_PANE);
    }

    private ItemStack pane(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(" ");
        it.setItemMeta(meta);
        return it;
    }

    private void border(Inventory inv) {
        border(inv, Material.BLACK_STAINED_GLASS_PANE);
    }

    private void border(Inventory inv, Material edge) {
        ItemStack p = pane(edge);
        for (int i = 0; i < 9; i++) { inv.setItem(i, p); inv.setItem(45 + i, p); }
        for (int r = 1; r < 5; r++) { inv.setItem(r * 9, p); inv.setItem(r * 9 + 8, p); }
    }

    private ItemStack button(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null && !lore.isEmpty()) meta.setLore(lore);
        it.setItemMeta(meta);
        return it;
    }

    private void clickSound(Player p) {
        try {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.2f);
        } catch (Throwable ignored) {}
    }

    private List<PlayerData> sortedSuspects() {
        List<PlayerData> all = new ArrayList<>(AnticheatCore.get().allPlayers().values());
        for (Player p : Bukkit.getOnlinePlayers()) {
            PlayerData d = AnticheatCore.get().data(p.getUniqueId());
            d.name = p.getName();
            try { d.ping = p.getPing(); } catch (Throwable ignored) {}
            if (!all.contains(d)) all.add(d);
        }
        all.sort(Comparator.comparingInt(PlayerData::totalVl).reversed());
        return all;
    }

    // ---------- overview ----------

    public void openOverview(Player admin) {
        List<PlayerData> all = sortedSuspects();
        Inventory inv = Bukkit.createInventory(admin, 54, TITLE_SUSPECTS);
        border(inv);

        long suspects = all.stream().filter(d -> d.totalVl() > 0).count();
        int reports = AnticheatCore.get().storage().openReports().size();
        inv.setItem(4, button(Material.NETHER_STAR, "§6§lAntiCheat §7· 1.21.11 §8v" + AnticheatPaper.PLUGIN_VERSION, List.of(
            "§7Online: §f" + Bukkit.getOnlinePlayers().size(),
            "§7Sospetti: §c" + suspects,
            "§7Report aperti: §e" + reports,
            "",
            "§8F8 dal client apre questo pannello")));

        for (int i = 0; i < CONTENT_SLOTS.length && i < all.size(); i++) {
            inv.setItem(CONTENT_SLOTS[i], suspectSkull(all.get(i)));
        }

        inv.setItem(47, button(Material.BOOK, "§e§lReport §7(" + reports + ")",
            List.of("§7Click per vedere le segnalazioni")));
        inv.setItem(49, button(Material.COMPARATOR, "§b§lImpostazioni",
            List.of("§7Soglie warn/kick/ban, check on/off,",
                "§7mod client, debug — live, senza restart")));
        inv.setItem(51, button(Material.BARRIER, "§cChiudi", null));
        admin.openInventory(inv);
    }

    private ItemStack suspectSkull(PlayerData d) {
        ItemStack skull = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) skull.getItemMeta();
        String color = d.totalVl() == 0 ? "§a" : (d.totalVl() >= AnticheatCore.get().config().warnVl ? "§c" : "§e");
        meta.setDisplayName(color + d.name + " §8· §7VL " + d.totalVl());
        List<String> lore = new ArrayList<>();
        lore.add("§7Client: " + clientBadge(d) + " §8" + d.clientVersion);
        lore.add("§7Ping: §f" + d.ping);
        if (d.violations.isEmpty()) {
            lore.add("§7Nessuna violazione");
        } else {
            lore.add("§7Violazioni:");
            d.violations.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(4)
                .forEach(e -> lore.add("  §8- §f" + e.getKey() + " §7x" + e.getValue()));
        }
        lore.add("");
        lore.add("§eClick per dettagli e azioni");
        meta.setLore(lore);
        try {
            Player p = Bukkit.getPlayer(d.uuid);
            if (p != null) meta.setOwningPlayer(p);
        } catch (Throwable ignored) {}
        skull.setItemMeta(meta);
        return skull;
    }

    private String clientBadge(PlayerData d) {
        return switch (d.clientStatus) {
            case VERIFIED -> "§a✔ verificato";
            case UNVERIFIED -> "§c✘ versione vecchia";
            default -> "§7○ senza mod";
        };
    }

    // ---------- detail ----------

    public void openDetail(Player admin, UUID target) {
        PlayerData d = AnticheatCore.get().data(target);
        Inventory inv = Bukkit.createInventory(admin, 54, TITLE_DETAIL + d.name);
        border(inv);
        inv.setItem(4, suspectSkull(d));

        List<Violation> hist = AnticheatCore.get().storage().recentViolations(target, 14);
        int i = 0;
        for (Violation v : hist) {
            if (i >= CONTENT_SLOTS.length) break;
            inv.setItem(CONTENT_SLOTS[i++], button(Material.PAPER, "§f" + v.check + " §8+§c" + v.vlAdded,
                List.of("§7Totale: §f" + v.totalVl, "§7" + v.details)));
        }

        inv.setItem(39, button(Material.ENDER_PEARL, "§bOsserva", List.of("§7Teleportati dal giocatore")));
        inv.setItem(40, button(Material.GRAY_DYE, "§7Reset VL", List.of("§7Azzera tutte le violazioni")));
        inv.setItem(41, button(Material.ORANGE_WOOL, "§eKick", List.of("§7Espelli subito dal server")));
        inv.setItem(42, button(Material.RED_WOOL, "§cBan", List.of("§7Ban permanente del nome")));
        inv.setItem(49, button(Material.ARROW, "§7← Indietro", null));
        admin.openInventory(inv);
        admin.setMetadata("ac-target", new org.bukkit.metadata.FixedMetadataValue(plugin, target.toString()));
    }

    // ---------- reports ----------

    public void openReports(Player admin) {
        List<Report> reps = AnticheatCore.get().storage().openReports();
        Inventory inv = Bukkit.createInventory(admin, 54, TITLE_REPORTS);
        border(inv);
        inv.setItem(4, button(Material.BOOK, "§e§lReport aperti §7(" + reps.size() + ")",
            List.of("§7Segnalazioni dei giocatori con /report")));
        int i = 0;
        for (Report r : reps) {
            if (i >= CONTENT_SLOTS.length) break;
            inv.setItem(CONTENT_SLOTS[i], button(Material.WRITABLE_BOOK, "§e#" + r.id + " §f" + r.reportedName,
                List.of("§7Da: §f" + r.reporterName, "§7Motivo: §f" + r.reason, "", "§eClick per chiudere")));
            admin.setMetadata("ac-report-" + CONTENT_SLOTS[i],
                new org.bukkit.metadata.FixedMetadataValue(plugin, r.id));
            i++;
        }
        inv.setItem(49, button(Material.ARROW, "§7← Indietro", null));
        admin.openInventory(inv);
    }

    // ---------- settings ----------

    public void openSettings(Player admin) {
        openSettings(admin, 0);
    }

    /**
     * Pagina 0: soglie, toggle e check standard (idx 0-15).
     * Pagina 1: sperimentali e nuovi (idx 16+, es. futuri AimSnap/AimLock).
     */
    public void openSettings(Player admin, int page) {
        AnticheatConfig c = AnticheatCore.get().config();
        admin.setMetadata("ac-settings-page", new org.bukkit.metadata.FixedMetadataValue(plugin, page));
        Inventory inv = Bukkit.createInventory(admin, 54, TITLE_SETTINGS);
        border(inv, Material.GRAY_STAINED_GLASS_PANE);
        inv.setItem(4, button(Material.NETHER_STAR, "§6§lAntiCheat §8· §bImpostazioni §8v" + AnticheatPaper.PLUGIN_VERSION, List.of(
            "§7Le modifiche si salvano subito in config.yml,",
            "§7senza riavviare il server.",
            "§7Test-mode: " + (c.testMode ? "§eATTIVO (nessuna punizione)" : "§aoperativo"))));

        inv.setItem(10, button(Material.YELLOW_WOOL, "§e§lWarn VL: §f" + c.warnVl, List.of(
            "§8[§eSOGLIA VL§8]",
            "§7Sopra questa soglia il giocatore", "§7riceve un avviso in chat.",
            "", "§aSX: +5   §cDX: -5")));
        inv.setItem(11, button(Material.ORANGE_WOOL, "§6§lKick VL: §f" + c.kickVl, List.of(
            "§8[§eSOGLIA VL§8]",
            "§7Sopra questa soglia scatta il kick.",
            "", "§aSX: +5   §cDX: -5")));
        inv.setItem(12, button(Material.RED_WOOL, "§c§lBan VL: §f" + c.banVl, List.of(
            "§8[§eSOGLIA VL§8]",
            "§7Sopra questa soglia scatta il ban.",
            "", "§aSX: +5   §cDX: -5")));
        inv.setItem(13, button(Material.REPEATER, "§d§lAutoClicker: §f" + c.autoClickerName(), List.of(
            "§7CPS oltre §f" + c.cpsLimit + " §7(flaggati ovunque) + regolarita in combat.",
            "§7Scavare/costruire non contano mai.",
            "§7Limite: §f/ac cps <10-100> §7· sensibilita: click",
            "", "§eClick per cambiare (Rigido→Normale→Largo)")));

        inv.setItem(14, button(c.clientRequired ? Material.ENDER_EYE : Material.GRAY_DYE,
            "§b§lMod client obbligatoria: " + (c.clientRequired ? "§aON" : "§cOFF"), List.of(
                "§7Se ON, chi entra senza mod client", "§7viene kickato. §cSconsigliato.",
                "", "§eClick per cambiare")));
        inv.setItem(15, button(c.verbose ? Material.REDSTONE_TORCH : Material.GRAY_DYE,
            "§b§lNotifiche dettagliate: " + (c.verbose ? "§aON" : "§cOFF"), List.of(
                "§7Se ON, lo staff vede ogni flag,", "§7altrimenti solo quelli gravi.",
                "", "§eClick per cambiare")));
        inv.setItem(16, button(Material.SPYGLASS, "§d§lXRay: §f" + c.xrayPresetName(), List.of(
            "§7Profilo rilevazione statistica diamanti.",
            "§7Pietra≥§f" + c.xrayMinStone + " §7diamanti≥§f" + c.xrayMinOres
                + " §7rapporto>§f" + (c.xrayRatio * 100) + "%",
            "§7Cooldown: §f" + c.xrayCooldownMin + " min",
            "", "§eClick per cambiare (Tollerante→Normale→Severo)")));

        inv.setItem(32, button(c.testMode ? Material.BEACON : Material.GRAY_DYE,
            "§d§lTest-mode: " + (c.testMode ? "§aON" : "§cOFF"), List.of(
                "§7Se ON: logga e avvisa ma NON kicka,",
                "§7NON banna e niente setback.",
                "§7Ideale per tarare i check.",
                "", "§eClick per cambiare")));
        inv.setItem(33, button(c.setbackEnabled ? Material.IRON_BOOTS : Material.GRAY_DYE,
            "§b§lSetback: " + (c.setbackEnabled ? "§aON" : "§cOFF"), List.of(
                "§7Se ON: ai flag movimento sopra §f" + c.setbackMinVl + " VL",
                "§7il player torna all'ultima terra sicura.",
                "", "§eClick per cambiare")));
        inv.setItem(34, button(Material.GOLDEN_BOOTS, "§b§lSetback min VL: §f" + c.setbackMinVl, List.of(
            "§7Soglia VL per il setback.",
            "", "§aSX: +5   §cDX: -5")));

        int[] grid = page == 0 ? PAGE1_SLOTS : PAGE2_SLOTS;
        int startIdx = page == 0 ? 0 : PAGE1_SLOTS.length;
        java.util.List<String> allChecks = AnticheatConfig.ALL_CHECKS;
        int i = 0;
        for (int ci = startIdx; ci < allChecks.size(); ci++) {
            String name = allChecks.get(ci);
            if (i >= grid.length) break;
            boolean on = c.isCheckEnabled(name);
            String desc = "";
            boolean exp = false;
            for (Check ch : AnticheatCore.get().checks()) {
                if (ch.name().equals(name)) { desc = ch.description(); exp = ch.experimental(); break; }
            }
            inv.setItem(grid[i++], button(
                on ? CHECK_ICON.getOrDefault(name, Material.LIME_WOOL) : Material.GRAY_WOOL,
                (on ? "§a§l" : "§c§l") + name, List.of(
                    "§8[§aCHECK§8]" + (exp ? " §8[§dSPERIMENTALE§8]" : ""),
                    "§7" + desc,
                    "§7Stato: " + (on ? "§aATTIVO" : "§cSPENTO"),
                    "", "§eClick per attivare/disattivare")));
        }

        if (page == 1) {
            // pagina 2: solo check, niente soglie/toggle (stanno in pagina 1)
            for (int s : new int[]{10, 11, 12, 13, 14, 15, 16, 32, 33, 34}) inv.setItem(s, null);
            inv.setItem(25, button(c.experimentalChecks ? Material.EXPERIENCE_BOTTLE : Material.GRAY_DYE,
                "§d§lSperimentali: " + (c.experimentalChecks ? "§aON" : "§cOFF"), List.of(
                    "§7Master switch dei check sperimentali",
                    "§7(Multitask, Spider...).",
                    "", "§eClick per cambiare")));
            inv.setItem(49, button(Material.ARROW, "§7← Pagina 1", null));
        } else {
            if (AnticheatConfig.ALL_CHECKS.size() > PAGE1_SLOTS.length) {
                inv.setItem(43, button(Material.COMPASS, "§ePagina 2 ▶", List.of(
                    "§7Check sperimentali e nuovi",
                    "", "§eClick per aprire")));
            }
            inv.setItem(49, button(Material.ARROW, "§7← Indietro", null));
        }
        inv.setItem(53, button(Material.BARRIER, "§cChiudi", null));
        admin.openInventory(inv);
    }

    // ---------- click routing ----------

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        String title = e.getView().getTitle();
        boolean suspects = title.equals(TITLE_SUSPECTS);
        boolean reports = title.equals(TITLE_REPORTS);
        boolean settings = title.equals(TITLE_SETTINGS);
        boolean detail = title.startsWith(TITLE_DETAIL);
        if (!suspects && !reports && !settings && !detail) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player admin)) return;
        if (e.getCurrentItem() == null) return;
        if (!plugin.isAdmin(admin)) {
            admin.closeInventory();
            return;
        }
        clickSound(admin);
        int slot = e.getRawSlot();

        if (suspects) {
            if (slot == 51) { admin.closeInventory(); return; }
            if (slot == 47) { openReports(admin); return; }
            if (slot == 49) { openSettings(admin); return; }
            List<PlayerData> all = sortedSuspects();
            for (int i = 0; i < CONTENT_SLOTS.length && i < all.size(); i++) {
                if (CONTENT_SLOTS[i] == slot) { openDetail(admin, all.get(i).uuid); return; }
            }
            return;
        }

        if (reports) {
            if (slot == 49) { openOverview(admin); return; }
            if (admin.hasMetadata("ac-report-" + slot)) {
                long rid = admin.getMetadata("ac-report-" + slot).get(0).asLong();
                AnticheatCore.get().storage().closeReport(rid);
                admin.sendMessage("§aReport #" + rid + " chiuso.");
                openReports(admin);
            }
            return;
        }

        if (settings) {
            handleSettingsClick(admin, slot, e.isLeftClick(), e.isRightClick());
            return;
        }

        // detail
        UUID target = null;
        if (admin.hasMetadata("ac-target")) {
            try { target = UUID.fromString(admin.getMetadata("ac-target").get(0).asString()); }
            catch (Exception ignored) {}
        }
        if (target == null) { openOverview(admin); return; }
        final UUID id = target;
        if (slot == 49) { openOverview(admin); return; }
        admin.closeInventory();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (slot == 39) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) admin.teleport(p.getLocation());
                else admin.sendMessage("§cGiocatore offline.");
            }
            else if (slot == 40) { AnticheatCore.get().resetVl(id); admin.sendMessage("§aVL resettate."); }
            else if (slot == 41) {
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.kickPlayer("AntiCheat: kick dallo staff");
            }
            else if (slot == 42) {
                PlayerData d = AnticheatCore.get().data(id);
                Bukkit.getBanList(BanList.Type.NAME).addBan(d.name, "Ban dallo staff (AC GUI)", null, admin.getName());
                Player p = Bukkit.getPlayer(id);
                if (p != null) p.kickPlayer("Sei stato bannato.");
            }
        });
    }

    private void handleSettingsClick(Player admin, int slot, boolean left, boolean right) {
        int page = 0;
        if (admin.hasMetadata("ac-settings-page")) {
            try {
                page = admin.getMetadata("ac-settings-page").get(0).asInt();
            } catch (Exception ignored) {}
        }
        AnticheatConfig c = AnticheatCore.get().config();
        // navigazione pagine
        if (page == 0 && slot == 43 && AnticheatConfig.ALL_CHECKS.size() > PAGE1_SLOTS.length) {
            openSettings(admin, 1);
            return;
        }
        if (page == 1) {
            if (slot == 49) {
                openSettings(admin, 0);
                return;
            }
            if (slot == 53) {
                admin.closeInventory();
                return;
            }
            if (slot == 25) {
                c.experimentalChecks = !c.experimentalChecks;
                admin.sendMessage(c.experimentalChecks
                    ? "§eCheck sperimentali ATTIVI (occhio ai falsi positivi)."
                    : "§aCheck sperimentali spenti.");
                if (plugin.persistConfig()) admin.sendMessage("§aImpostazioni salvate.");
                else admin.sendMessage("§cSalvataggio fallito, vedi console.");
                openSettings(admin, 1);
                return;
            }
            boolean found = false;
            for (int i = 0; i < PAGE2_SLOTS.length && i + PAGE1_SLOTS.length < AnticheatConfig.ALL_CHECKS.size(); i++) {
                if (PAGE2_SLOTS[i] == slot) {
                    String pname = AnticheatConfig.ALL_CHECKS.get(i + PAGE1_SLOTS.length);
                    if (c.isCheckEnabled(pname)) c.checkEnabled.put(pname, false);
                    else c.checkEnabled.remove(pname);
                    found = true;
                    break;
                }
            }
            if (!found) return;
            if (plugin.persistConfig()) admin.sendMessage("§aImpostazioni salvate.");
            else admin.sendMessage("§cSalvataggio fallito, vedi console.");
            openSettings(admin, 1);
            return;
        }
        int step = 5;
        if (slot == 10 || slot == 11 || slot == 12) {
            int delta = left ? step : (right ? -step : 0);
            if (delta == 0) return;
            if (slot == 10) c.warnVl = Math.max(5, Math.min(c.kickVl - 5, c.warnVl + delta));
            else if (slot == 11) c.kickVl = Math.max(c.warnVl + 5, Math.min(c.banVl - 5, c.kickVl + delta));
            else c.banVl = Math.max(c.kickVl + 5, Math.min(1000, c.banVl + delta));
        } else if (slot == 14) {
            c.clientRequired = !c.clientRequired;
        } else if (slot == 15) {
            c.verbose = !c.verbose;
        } else if (slot == 13) {
            c.cycleAutoClicker();
        } else if (slot == 16) {
            c.cycleXrayPreset();
        } else if (slot == 32) {
            c.testMode = !c.testMode;
            admin.sendMessage(c.testMode
                ? "§eTest-mode ATTIVO: nessuna punizione, solo log."
                : "§aTest-mode spento: punizioni attive.");
        } else if (slot == 33) {
            c.setbackEnabled = !c.setbackEnabled;
        } else if (slot == 34) {
            int delta = left ? step : (right ? -step : 0);
            if (delta == 0) return;
            c.setbackMinVl = Math.max(5, Math.min(100, c.setbackMinVl + delta));
        } else if (slot == 49) {
            openOverview(admin);
            return;
        } else if (slot == 53) {
            admin.closeInventory();
            return;
        } else {
            boolean found = false;
            for (int i = 0; i < PAGE1_SLOTS.length && i < AnticheatConfig.ALL_CHECKS.size(); i++) {
                if (PAGE1_SLOTS[i] == slot) {
                    String name = AnticheatConfig.ALL_CHECKS.get(i);
                    if (c.isCheckEnabled(name)) c.checkEnabled.put(name, false);
                    else c.checkEnabled.remove(name); // assente = attivo
                    found = true;
                    break;
                }
            }
            if (!found) return;
        }
        if (plugin.persistConfig()) admin.sendMessage("§aImpostazioni salvate.");
        else admin.sendMessage("§cSalvataggio fallito, vedi console.");
        openSettings(admin);
    }
}
