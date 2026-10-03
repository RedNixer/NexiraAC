package it.anticheat.core.dashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * Storia punizioni (dashboard + futura GUI). Ultime 100 globali,
 * intera storia per player (cap 50). Persistente col db dopo.
 */
public final class PunishmentLog {
    public static class Entry {
        public long time;
        public UUID uuid;
        public String name;
        public String action; // WARN, KICK, TEMPBAN, BAN, UNBAN, UNMUTE, UNFREEZE
        public String check;
        public String reason;
        public String staff; // chi l'ha data (console, dashboard, nome)
        public long expiresAt; // -1 = permanente, 0 = n/a
        public String banId = ""; // #XXXXXXXX per ban/tempban (appeal)
    }

    private final java.util.Deque<Entry> global = new ConcurrentLinkedDeque<>();
    private final Map<UUID, java.util.Deque<Entry>> byPlayer = new ConcurrentHashMap<>();

    public synchronized void add(UUID uuid, String name, String action, String check,
                                 String reason, String staff, long expiresAt) {
        add(uuid, name, action, check, reason, staff, expiresAt, "");
    }

    public synchronized void add(UUID uuid, String name, String action, String check,
                                 String reason, String staff, long expiresAt, String banId) {
        Entry e = new Entry();
        e.time = System.currentTimeMillis();
        e.uuid = uuid;
        e.name = name == null ? "?" : name;
        e.action = action;
        e.check = check == null ? "-" : check;
        e.reason = reason == null ? "" : reason;
        e.staff = staff == null ? "console" : staff;
        e.expiresAt = expiresAt;
        e.banId = banId == null ? "" : banId;
        global.addFirst(e);
        while (global.size() > 100) global.pollLast();
        java.util.Deque<Entry> mine =
            byPlayer.computeIfAbsent(uuid, k -> new ConcurrentLinkedDeque<>());
        mine.addFirst(e);
        while (mine.size() > 50) mine.pollLast();
    }

    public List<Entry> forPlayer(UUID uuid) {
        java.util.Deque<Entry> mine = byPlayer.get(uuid);
        return mine == null ? List.of() : new ArrayList<>(mine);
    }

    public List<Entry> recent(int n) {
        List<Entry> out = new ArrayList<>();
        int i = 0;
        for (Entry e : global) {
            if (i++ >= n) break;
            out.add(e);
        }
        return out;
    }
}
