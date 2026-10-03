package it.anticheat.core.dashboard;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sessioni giocatori per la dashboard. Puro Java, nessun Bukkit.
 * Gli adapter chiamano join/quit/heartbeat; la dashboard legge snapshot.
 */
public final class SessionTracker {
    public static class Session {
        public volatile UUID uuid;
        public volatile String name = "?";
        public volatile String ip = "-";
        public volatile String brand = "-";
        public volatile String clientVersion = "-";
        public volatile long joinTime = System.currentTimeMillis();
        public volatile long quitTime = 0;
        public volatile long playtimeTotalMs = 0; // sessioni passate (db dopo)
        public volatile boolean online = true;
        public volatile int ping = 0;
    }

    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    /** Storico sessioni chiuse (max 300, le vecchie cadono). Per la tabella. */
    private final java.util.Deque<Session> history = new java.util.concurrent.ConcurrentLinkedDeque<>();
    /** uuid -> ip (ultimo visto, per /ac alts futuro). */
    private final Map<UUID, String> lastIp = new ConcurrentHashMap<>();
    /** ip -> uuid (tutti gli account visti, per /ac alts futuro). */
    private final Map<String, java.util.Set<UUID>> ipToUuids = new ConcurrentHashMap<>();

    public void join(UUID uuid, String name, String ip) {
        Session s = sessions.computeIfAbsent(uuid, k -> new Session());
        s.uuid = uuid;
        s.name = name != null ? name : "?";
        s.ip = ip != null ? ip : "-";
        s.joinTime = System.currentTimeMillis();
        s.quitTime = 0;
        s.online = true;
        if (ip != null && !ip.isEmpty() && !"-".equals(ip)) {
            lastIp.put(uuid, ip);
            ipToUuids.computeIfAbsent(ip, k -> ConcurrentHashMap.newKeySet()).add(uuid);
        }
    }

    public void quit(UUID uuid) {
        Session s = sessions.get(uuid);
        if (s == null) return;
        s.quitTime = System.currentTimeMillis();
        s.online = false;
        s.playtimeTotalMs += Math.max(0, s.quitTime - s.joinTime);
        // snapshot nello storico (copia: la live continua a vivere)
        Session h = new Session();
        h.uuid = uuid;
        h.name = s.name;
        h.ip = s.ip;
        h.brand = s.brand;
        h.clientVersion = s.clientVersion;
        h.joinTime = s.joinTime;
        h.quitTime = s.quitTime;
        h.online = false;
        history.addFirst(h);
        while (history.size() > 300) history.pollLast();
    }

    public void heartbeat(UUID uuid, int ping) {
        Session s = sessions.get(uuid);
        if (s != null) s.ping = ping;
    }

    public void brand(UUID uuid, String brand, String version) {
        Session s = sessions.get(uuid);
        if (s == null) return;
        if (brand != null) s.brand = brand;
        if (version != null) s.clientVersion = version;
    }

    public Session get(UUID uuid) {
        return sessions.get(uuid);
    }

    public List<Session> snapshot() {
        return new ArrayList<>(sessions.values());
    }

    public List<Session> history() {
        return new ArrayList<>(history);
    }

    public java.util.Set<UUID> alts(String ip) {
        java.util.Set<UUID> s = ipToUuids.get(ip);
        return s == null ? java.util.Set.of() : java.util.Set.copyOf(s);
    }

    public String ipOf(UUID uuid) {
        return lastIp.getOrDefault(uuid, "-");
    }
}
