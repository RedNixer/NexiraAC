package it.anticheat.fabric.gui;

import it.anticheat.core.AnticheatCore;
import it.anticheat.core.PlayerData;
import it.anticheat.core.config.AnticheatConfig;
import it.anticheat.core.model.Report;
import it.anticheat.core.model.Violation;
import it.anticheat.fabric.listener.FabricState;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

/**
 * GUI admin Fabric, copia della AdminGui Paper (Sospetti + Dettaglio +
 * Report + Impostazioni). Container vanilla 54 slot, niente dipendenze.
 */
public final class FabricAdminGui {
    private FabricAdminGui() {}

    public static final String TITLE_SUSPECTS = "AC - Sospetti";
    public static final String TITLE_REPORTS = "AC - Report";
    public static final String TITLE_SETTINGS = "AC - Impostazioni";
    private static final String TITLE_DETAIL = "AC: ";

    private static final int[] CONTENT_SLOTS = {
        10, 11, 12, 13, 14, 15, 16,
        19, 20, 21, 22, 23, 24, 25,
        28, 29, 30, 31, 32, 33, 34,
        37, 38, 39, 40, 41, 42, 43};
    private static final int[] PAGE1_SLOTS = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 37, 38, 39, 40, 41};
    private static final int[] PAGE2_SLOTS = {19, 20, 21, 22};

    /** Target dettaglio per admin (sostituisce metadata Bukkit). */
    private static final Map<UUID, UUID> detailTarget = new ConcurrentHashMap<>();
    /** Report id per slot (per admin: slot -> report id). */
    private static final Map<UUID, Map<Integer, Long>> reportSlots = new ConcurrentHashMap<>();
    /** Pagina impostazioni per admin. */
    private static final Map<UUID, Integer> settingsPage = new ConcurrentHashMap<>();

    private static List<PlayerData> sortedSuspects() {
        List<PlayerData> all = new ArrayList<>(AnticheatCore.get().allPlayers().values());
        try {
            if (FabricState.server() != null) {
                for (ServerPlayer p : FabricState.server().getPlayerList().getPlayers()) {
                    PlayerData d = AnticheatCore.get().data(p.getUUID());
                    d.name = p.getScoreboardName();
                    try { d.ping = p.connection.latency(); } catch (Throwable ignored) {}
                    if (!all.contains(d)) all.add(d);
                }
            }
        } catch (Throwable ignored) {}
        all.sort(Comparator.comparingInt(PlayerData::totalVl).reversed());
        return all;
    }

    // ---------- item helpers ----------

    private static ItemStack named(net.minecraft.world.item.Item item, String name, List<String> lore) {
        ItemStack st = new ItemStack(item);
        st.set(DataComponents.CUSTOM_NAME, Component.literal(name));
        if (lore != null && !lore.isEmpty()) {
            List<Component> lines = new ArrayList<>();
            for (String s : lore) lines.add(Component.literal(s));
            st.set(DataComponents.LORE, new ItemLore(lines));
        }
        return st;
    }

    private static ItemStack pane() {
        return named(Items.BLACK_STAINED_GLASS_PANE, " ", null);
    }

    private static void border(net.minecraft.world.Container inv) {
        ItemStack p = pane();
        for (int i = 0; i < 9; i++) { inv.setItem(i, p.copy()); inv.setItem(45 + i, p.copy()); }
        for (int r = 1; r < 5; r++) { inv.setItem(r * 9, p.copy()); inv.setItem(r * 9 + 8, p.copy()); }
    }

    private static String clientBadge(PlayerData d) {
        return switch (d.clientStatus) {
            case VERIFIED -> "§a✔ verificato";
            case UNVERIFIED -> "§c✘ versione vecchia";
            default -> "§7○ senza mod";
        };
    }

    private static ItemStack suspectSkull(PlayerData d) {
        String color = d.totalVl() == 0 ? "§a"
            : (d.totalVl() >= AnticheatCore.get().config().warnVl ? "§c" : "§e");        List<String> lore = new ArrayList<>();
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
        ItemStack skull = named(Items.PLAYER_HEAD, color + d.name + " §8· §7VL " + d.totalVl(), lore);
        // NOTA: testa Steve di default (profilo reale richiede firma Patch,
        // da risolvere con mixin accessor — icona comunque visibile col nome).
        return skull;
    }

    // ---------- apertura ----------

    public static void openOverview(ServerPlayer admin) {
        List<PlayerData> all = sortedSuspects();
        open(admin, TITLE_SUSPECTS, (inv, menu) -> {
            border(inv);
            long suspects = all.stream().filter(d -> d.totalVl() > 0).count();
            int reports = AnticheatCore.get().storage().openReports().size();
            int online = 0;
            try { online = FabricState.server().getPlayerList().getPlayers().size(); } catch (Throwable ignored) {}
            final int fOnline = online;
            inv.setItem(4, named(Items.NETHER_STAR, "§6§lAntiCheat §7· 1.21.11 §8v0.2.0", List.of(
                "§7Online: §f" + fOnline,
                "§7Sospetti: §c" + suspects,
                "§7Report aperti: §e" + reports,
                "",
                "§8F8 dal client apre questo pannello")));
            for (int i = 0; i < CONTENT_SLOTS.length && i < all.size(); i++) {
                inv.setItem(CONTENT_SLOTS[i], suspectSkull(all.get(i)));
            }
            inv.setItem(47, named(Items.BOOK, "§e§lReport §7(" + reports + ")",
                List.of("§7Click per vedere le segnalazioni")));
            inv.setItem(49, named(Items.COMPARATOR, "§b§lImpostazioni",
                List.of("§7Soglie warn/kick/ban, check on/off,",
                    "§7mod client, debug — live, senza restart")));
            inv.setItem(51, named(Items.BARRIER, "§cChiudi", null));
        }, (a, slot, left, right) -> {
            if (slot == 51) { a.closeContainer(); return; }
            if (slot == 47) { openReports(a); return; }
            if (slot == 49) { openSettings(a, 0); return; }
            List<PlayerData> cur = sortedSuspects();
            for (int i = 0; i < CONTENT_SLOTS.length && i < cur.size(); i++) {
                if (CONTENT_SLOTS[i] == slot) { openDetail(a, cur.get(i).uuid); return; }
            }
        });
    }

    public static void openDetail(ServerPlayer admin, UUID target) {
        detailTarget.put(admin.getUUID(), target);
        PlayerData d = AnticheatCore.get().data(target);
        open(admin, TITLE_DETAIL + d.name, (inv, menu) -> {
            border(inv);
            inv.setItem(4, suspectSkull(d));
            List<Violation> hist = AnticheatCore.get().storage().recentViolations(target, 14);
            int i = 0;
            for (Violation v : hist) {
                if (i >= CONTENT_SLOTS.length) break;
                inv.setItem(CONTENT_SLOTS[i++], named(Items.PAPER,
                    "§f" + v.check + " §8+§c" + v.vlAdded,
                    List.of("§7Totale: §f" + v.totalVl, "§7" + v.details)));
            }
            inv.setItem(39, named(Items.ENDER_PEARL, "§bOsserva", List.of("§7Teleportati dal giocatore")));
            inv.setItem(40, named(Items.GRAY_DYE, "§7Reset VL", List.of("§7Azzera tutte le violazioni")));
            inv.setItem(41, named(Items.ORANGE_WOOL, "§eKick", List.of("§7Espelli subito dal server")));
            inv.setItem(42, named(Items.RED_WOOL, "§cBan", List.of("§7Ban permanente del nome")));
            inv.setItem(49, named(Items.ARROW, "§7← Indietro", null));
        }, (a, slot, left, right) -> {
            UUID id = detailTarget.get(a.getUUID());
            if (id == null) { openOverview(a); return; }
            if (slot == 49) { openOverview(a); return; }
            a.closeContainer();
            try {
                if (FabricState.server() == null) return;
                if (slot == 39) {
                    ServerPlayer t = FabricState.server().getPlayerList().getPlayer(id);
                    if (t != null) a.teleportTo(t.getX(), t.getY(), t.getZ());
                    else a.sendSystemMessage(Component.literal("Giocatore offline."));
                } else if (slot == 40) {
                    AnticheatCore.get().resetVl(id);
                    a.sendSystemMessage(Component.literal("VL resettate."));
                } else if (slot == 41) {
                    ServerPlayer t = FabricState.server().getPlayerList().getPlayer(id);
                    if (t != null) t.connection.disconnect(Component.literal("AntiCheat: kick dallo staff"));
                } else if (slot == 42) {
                    PlayerData dd = AnticheatCore.get().data(id);
                    ServerPlayer t = FabricState.server().getPlayerList().getPlayer(id);
                    try {
                        FabricState.server().getPlayerList().getBans().add(
                            new net.minecraft.server.players.UserBanListEntry(
                                new net.minecraft.server.players.NameAndId(id, dd.name),
                                null, "AntiCheat", null, "Ban dallo staff (AC GUI)"));
                    } catch (Throwable ignored) {}
                    if (t != null) t.connection.disconnect(Component.literal("Sei stato bannato."));
                }
            } catch (Throwable ignored) {}
        });
    }

    public static void openReports(ServerPlayer admin) {
        List<Report> reps = AnticheatCore.get().storage().openReports();
        Map<Integer, Long> slots = new ConcurrentHashMap<>();
        reportSlots.put(admin.getUUID(), slots);
        open(admin, TITLE_REPORTS, (inv, menu) -> {
            border(inv);
            inv.setItem(4, named(Items.BOOK, "§e§lReport aperti §7(" + reps.size() + ")",
                List.of("§7Segnalazioni dei giocatori con /report")));
            int i = 0;
            for (Report r : reps) {
                if (i >= CONTENT_SLOTS.length) break;
                inv.setItem(CONTENT_SLOTS[i], named(Items.WRITABLE_BOOK,
                    "§e#" + r.id + " §f" + r.reportedName,
                    List.of("§7Da: §f" + r.reporterName, "§7Motivo: §f" + r.reason, "",
                        "§eClick per chiudere")));
                slots.put(CONTENT_SLOTS[i], r.id);
                i++;
            }
            inv.setItem(49, named(Items.ARROW, "§7← Indietro", null));
        }, (a, slot, left, right) -> {
            if (slot == 49) { openOverview(a); return; }
            Map<Integer, Long> m = reportSlots.get(a.getUUID());
            if (m != null && m.containsKey(slot)) {
                long rid = m.get(slot);
                try { AnticheatCore.get().storage().closeReport(rid); } catch (Throwable ignored) {}
                a.sendSystemMessage(Component.literal("Report #" + rid + " chiuso."));
                openReports(a);
            }
        });
    }

    public static void openSettings(ServerPlayer admin, int page) {
        settingsPage.put(admin.getUUID(), page);
        AnticheatConfig c = AnticheatCore.get().config();
        open(admin, TITLE_SETTINGS, (inv, menu) -> {
            border(inv);
            inv.setItem(4, named(Items.NETHER_STAR, "§6§lAntiCheat §8· §bImpostazioni §8v0.2.0", List.of(
                "§7Le modifiche si salvano subito in config.yml,",
                "§7senza riavviare il server.",
                "§7Test-mode: " + (c.testMode ? "§eATTIVO (nessuna punizione)" : "§aoperativo"))));
            if (page == 0) {
                inv.setItem(10, named(Items.YELLOW_WOOL, "§e§lWarn VL: §f" + c.warnVl, List.of(
                    "§7Sopra questa soglia avviso in chat.", "", "§aSX: +5   §cDX: -5")));
                inv.setItem(11, named(Items.ORANGE_WOOL, "§6§lKick VL: §f" + c.kickVl, List.of(
                    "§7Sopra questa soglia kick.", "", "§aSX: +5   §cDX: -5")));
                inv.setItem(12, named(Items.RED_WOOL, "§c§lBan VL: §f" + c.banVl, List.of(
                    "§7Sopra questa soglia ban.", "", "§aSX: +5   §cDX: -5")));
                inv.setItem(13, named(Items.REPEATER, "§d§lAutoClicker: §f" + c.autoClickerName(), List.of(
                    "§7CPS oltre §f" + c.cpsLimit, "", "§eClick per cambiare")));
                inv.setItem(14, named(c.clientRequired ? Items.ENDER_EYE : Items.GRAY_DYE,
                    "§b§lMod client obbligatoria: " + (c.clientRequired ? "§aON" : "§cOFF"),
                    List.of("§7Se ON, chi entra senza mod viene kickato.", "", "§eClick per cambiare")));
                inv.setItem(15, named(c.verbose ? Items.REDSTONE_TORCH : Items.GRAY_DYE,
                    "§b§lNotifiche dettagliate: " + (c.verbose ? "§aON" : "§cOFF"),
                    List.of("§7Se ON, lo staff vede ogni flag.", "", "§eClick per cambiare")));
                inv.setItem(32, named(c.testMode ? Items.BEACON : Items.GRAY_DYE,
                    "§d§lTest-mode: " + (c.testMode ? "§aON" : "§cOFF"),
                    List.of("§7Se ON: logga ma NON punisce.", "", "§eClick per cambiare")));
                inv.setItem(33, named(c.setbackEnabled ? Items.IRON_BOOTS : Items.GRAY_DYE,
                    "§b§lSetback: " + (c.setbackEnabled ? "§aON" : "§cOFF"),
                    List.of("§7Torna all'ultima terra sicura.", "", "§eClick per cambiare")));
                inv.setItem(34, named(Items.GOLDEN_BOOTS, "§b§lSetback min VL: §f" + c.setbackMinVl, List.of(
                    "§7Soglia VL per il setback.", "", "§aSX: +5   §cDX: -5")));
            }
            int[] grid = page == 0 ? PAGE1_SLOTS : PAGE2_SLOTS;
            int startIdx = page == 0 ? 0 : PAGE1_SLOTS.length;
            List<String> allChecks = AnticheatConfig.ALL_CHECKS;
            int i = 0;
            for (int ci = startIdx; ci < allChecks.size(); ci++) {
                String name = allChecks.get(ci);
                if (i >= grid.length) break;
                boolean on = c.isCheckEnabled(name);
                inv.setItem(grid[i++], named(on ? Items.LIME_WOOL : Items.GRAY_WOOL,
                    (on ? "§a§l" : "§c§l") + name,
                    List.of("§7Stato: " + (on ? "§aATTIVO" : "§cSPENTO"), "", "§eClick per cambiare")));
            }
            if (page == 1) {
                inv.setItem(25, named(c.experimentalChecks ? Items.EXPERIENCE_BOTTLE : Items.GRAY_DYE,
                    "§d§lSperimentali: " + (c.experimentalChecks ? "§aON" : "§cOFF"),
                    List.of("§7Master switch sperimentali.", "", "§eClick per cambiare")));
                inv.setItem(49, named(Items.ARROW, "§7← Pagina 1", null));
            } else {
                if (AnticheatConfig.ALL_CHECKS.size() > PAGE1_SLOTS.length) {
                    inv.setItem(43, named(Items.COMPASS, "§ePagina 2 ▶",
                        List.of("§7Check sperimentali e nuovi", "", "§eClick per aprire")));
                }
                inv.setItem(49, named(Items.ARROW, "§7← Indietro", null));
            }
            inv.setItem(53, named(Items.BARRIER, "§cChiudi", null));
        }, (a, slot, left, right) -> handleSettingsClick(a, slot, left, right));
    }

    private static void handleSettingsClick(ServerPlayer admin, int slot, boolean left, boolean right) {
        int page = settingsPage.getOrDefault(admin.getUUID(), 0);
        AnticheatConfig c = AnticheatCore.get().config();
        if (page == 0 && slot == 43 && AnticheatConfig.ALL_CHECKS.size() > PAGE1_SLOTS.length) {
            openSettings(admin, 1);
            return;
        }
        if (page == 1) {
            if (slot == 49) { openSettings(admin, 0); return; }
            if (slot == 53) { admin.closeContainer(); return; }
            if (slot == 25) {
                c.experimentalChecks = !c.experimentalChecks;
                persist(admin, c.experimentalChecks ? "Check sperimentali ATTIVI." : "Check sperimentali spenti.");
                openSettings(admin, 1);
                return;
            }
            for (int i = 0; i < PAGE2_SLOTS.length && i + PAGE1_SLOTS.length < AnticheatConfig.ALL_CHECKS.size(); i++) {
                if (PAGE2_SLOTS[i] == slot) {
                    String pname = AnticheatConfig.ALL_CHECKS.get(i + PAGE1_SLOTS.length);
                    if (c.isCheckEnabled(pname)) c.checkEnabled.put(pname, false);
                    else c.checkEnabled.remove(pname);
                    persist(admin, "Impostazioni salvate.");
                    openSettings(admin, 1);
                    return;
                }
            }
            return;
        }
        int step = 5;
        int delta = left ? step : (right ? -step : 0);
        if (slot == 10 || slot == 11 || slot == 12) {
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
        } else if (slot == 32) {
            c.testMode = !c.testMode;
            admin.sendSystemMessage(Component.literal(c.testMode
                ? "Test-mode ATTIVO: nessuna punizione, solo log."
                : "Test-mode spento: punizioni attive."));
        } else if (slot == 33) {
            c.setbackEnabled = !c.setbackEnabled;
        } else if (slot == 34) {
            if (delta == 0) return;
            c.setbackMinVl = Math.max(5, Math.min(100, c.setbackMinVl + delta));
        } else if (slot == 49) {
            openOverview(admin);
            return;
        } else if (slot == 53) {
            admin.closeContainer();
            return;
        } else {
            boolean found = false;
            for (int i = 0; i < PAGE1_SLOTS.length && i < AnticheatConfig.ALL_CHECKS.size(); i++) {
                if (PAGE1_SLOTS[i] == slot) {
                    String name = AnticheatConfig.ALL_CHECKS.get(i);
                    if (c.isCheckEnabled(name)) c.checkEnabled.put(name, false);
                    else c.checkEnabled.remove(name);
                    found = true;
                    break;
                }
            }
            if (!found) return;
        }
        persist(admin, "Impostazioni salvate.");
        openSettings(admin, 0);
    }

    private static void persist(ServerPlayer admin, String msg) {
        boolean ok = true;
        try {
            AnticheatCore.get().config().saveFull(FabricState.configFile);
        } catch (Exception e) {
            ok = false;
            System.out.println("[AC] salvataggio config fallito: " + e.getMessage());
        }
        admin.sendSystemMessage(Component.literal(ok ? msg : "Salvataggio fallito, vedi console."));
    }

    // ---------- container ----------

    private interface Filler {
        void fill(net.minecraft.world.SimpleContainer inv, AbstractContainerMenu menu);
    }

    private interface ClickHandler {
        void onClick(ServerPlayer admin, int slot, boolean left, boolean right);
    }

    private static final Map<UUID, ClickHandler> handlers = new ConcurrentHashMap<>();

    private static void open(ServerPlayer admin, String title, Filler filler, ClickHandler handler) {
        try {
            net.minecraft.world.SimpleContainer inv = new net.minecraft.world.SimpleContainer(54);
            // menu finto per riempire: il vero menu nasce nel provider
            filler.fill(inv, null);
            handlers.put(admin.getUUID(), handler);
            SimpleMenuProvider provider = new SimpleMenuProvider((syncId, playerInv, player) -> {
                ChestMenu menu = new ChestMenu(MenuType.GENERIC_9x6, syncId, playerInv, inv, 6) {
                    @Override
                    public boolean stillValid(Player p) {
                        return true;
                    }

                    @Override
                    public void clicked(int slot, int button, ClickType type, Player p) {
                        try {
                            if (!(p instanceof ServerPlayer sp)) return;
                            if (!FabricState.isAdmin(sp)) {
                                sp.closeContainer();
                                return;
                            }
                            // solo click dentro la chest (slot 0-53), mai inventario player
                            if (slot < 0 || slot >= 54) return;
                            ClickHandler h = handlers.get(sp.getUUID());
                            if (h == null) return;
                            boolean left = button == 0;
                            boolean right = button == 1;
                            h.onClick(sp, slot, left, right);
                        } catch (Throwable ignored) {}
                    }
                };
                return menu;
            }, Component.literal(title));
            admin.openMenu(provider);
        } catch (Throwable t) {
            System.out.println("[AC] apertura GUI fallita: " + t.getMessage());
            admin.sendSystemMessage(Component.literal("[AC] GUI non disponibile, uso testo: /ac suspects"));
        }
    }
}
