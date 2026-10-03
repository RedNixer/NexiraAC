package it.anticheat.core.dashboard;

import it.anticheat.core.AnticheatCore;
import it.anticheat.core.PlayerData;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;

/**
 * Dashboard HTTP embedded (JDK HttpServer, zero dipendenze).
 * Sola lettura in v1: live, dettaglio player, alts. Azioni dopo.
 * Auth: Bearer token o ?token=. Bind localhost di default.
 */
public final class DashboardServer {
    private final DashboardAuth auth;
    private final SessionTracker sessions;
    private com.sun.net.httpserver.HttpServer http;
    /** logo.png servito a /logo (impostato dal plugin se presente). Null = solo testo. */
    private volatile java.nio.file.Path logoFile;

    public DashboardServer(DashboardAuth auth, SessionTracker sessions) {
        this.auth = auth;
        this.sessions = sessions;
    }

    public synchronized void start(String bind, int port) throws IOException {
        stop();
        http = com.sun.net.httpserver.HttpServer.create(new InetSocketAddress(bind, port), 0);
        http.createContext("/", this::handleIndex);
        http.createContext("/logo", this::handleLogo);
        http.createContext("/api/players", this::handlePlayers);
        http.createContext("/api/player", this::handlePlayer);
        http.createContext("/api/alts", this::handleAlts);
        http.createContext("/api/sessions", this::handleSessions);
        http.createContext("/api/history", this::handleHistory);
        http.createContext("/api/punish", this::handlePunish);
        http.createContext("/api/unpunish", this::handleUnpunish);
        http.setExecutor(Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "nexira-dash");
            t.setDaemon(true);
            return t;
        }));
        http.start();
    }

    public synchronized void stop() {
        if (http != null) {
            try { http.stop(0); } catch (Throwable ignored) {}
            http = null;
        }
    }

    public synchronized boolean running() {
        return http != null;
    }

    // ---- handlers ----

    private void handleIndex(com.sun.net.httpserver.HttpExchange ex) throws IOException {
        if (!"GET".equals(ex.getRequestMethod())) { send(ex, 405, "method"); return; }
        byte[] b = PAGE.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, b.length);
        try (OutputStream o = ex.getResponseBody()) { o.write(b); }
    }

    private boolean authed(com.sun.net.httpserver.HttpExchange ex) {
        String h = ex.getRequestHeaders().getFirst("Authorization");
        String q = query(ex, "token");
        return auth.check(h, q);
    }

    public void setLogo(java.nio.file.Path p) {
        logoFile = p;
    }

    private void handleLogo(com.sun.net.httpserver.HttpExchange ex) throws IOException {
        // 1) logo nel jar (paper resources), 2) file esterno impostato dal plugin
        try {
            java.io.InputStream in = DashboardServer.class.getResourceAsStream("/logo.png");
            if (in != null) {
                byte[] b = in.readAllBytes();
                in.close();
                ex.getResponseHeaders().set("Content-Type", "image/png");
                ex.getResponseHeaders().set("Cache-Control", "max-age=3600");
                ex.sendResponseHeaders(200, b.length);
                try (OutputStream o = ex.getResponseBody()) { o.write(b); }
                return;
            }
        } catch (Throwable ignored) {}
        java.nio.file.Path p = logoFile;
        if (p == null || !java.nio.file.Files.exists(p)) {
            send(ex, 404, "no logo");
            return;
        }
        try {
            byte[] b = java.nio.file.Files.readAllBytes(p);
            ex.getResponseHeaders().set("Content-Type", "image/png");
            ex.getResponseHeaders().set("Cache-Control", "max-age=3600");
            ex.sendResponseHeaders(200, b.length);
            try (OutputStream o = ex.getResponseBody()) { o.write(b); }
        } catch (Throwable t) {
            send(ex, 404, "no logo");
        }
    }

    private void handlePlayers(com.sun.net.httpserver.HttpExchange ex) throws IOException {
        if (!authed(ex)) { send(ex, 401, "{\"error\":\"auth\"}"); return; }
        List<SessionTracker.Session> all = sessions.snapshot();
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (SessionTracker.Session s : all) {
            if (!first) sb.append(",");
            first = false;
            UUID uuid = uuidOf(s);
            PlayerData d = uuid == null ? null : AnticheatCore.get().allPlayers().get(uuid);
            int vl = d == null ? 0 : d.totalVl();
            sb.append("{");
            sb.append("\"uuid\":\"").append(uuid == null ? "" : uuid).append("\",");
            sb.append("\"name\":").append(js(s.name)).append(",");
            sb.append("\"ip\":").append(js(s.ip)).append(",");
            sb.append("\"brand\":").append(js(s.brand)).append(",");
            sb.append("\"client\":").append(js(s.clientVersion)).append(",");
            sb.append("\"online\":").append(s.online).append(",");
            sb.append("\"ping\":").append(s.ping).append(",");
            sb.append("\"vl\":").append(vl).append(",");
            sb.append("\"playtimeMs\":").append(playtime(s));
            sb.append("}");
        }
        sb.append("]");
        sendJson(ex, sb.toString());
    }

    private void handlePlayer(com.sun.net.httpserver.HttpExchange ex) throws IOException {
        if (!authed(ex)) { send(ex, 401, "{\"error\":\"auth\"}"); return; }
        UUID uuid;
        try {
            uuid = UUID.fromString(query(ex, "uuid"));
        } catch (Throwable t) {
            send(ex, 400, "{\"error\":\"uuid\"}");
            return;
        }
        SessionTracker.Session s = sessions.get(uuid);
        PlayerData d = AnticheatCore.get().allPlayers().get(uuid);
        StringBuilder sb = new StringBuilder("{");
        sb.append("\"uuid\":\"").append(uuid).append("\",");
        sb.append("\"name\":").append(js(s == null ? "?" : s.name)).append(",");
        sb.append("\"ip\":").append(js(s == null ? "-" : s.ip)).append(",");
        sb.append("\"brand\":").append(js(s == null ? "-" : s.brand)).append(",");
        sb.append("\"client\":").append(js(s == null ? "-" : s.clientVersion)).append(",");
        sb.append("\"online\":").append(s != null && s.online).append(",");
        sb.append("\"ping\":").append(s == null ? 0 : s.ping).append(",");
        sb.append("\"vl\":").append(d == null ? 0 : d.totalVl()).append(",");
        sb.append("\"violations\":").append(js(d == null ? "{}" : d.violations.toString())).append(",");
        sb.append("\"playtimeMs\":").append(s == null ? 0 : playtime(s));
        sb.append("}");
        sendJson(ex, sb.toString());
    }

    private void handleAlts(com.sun.net.httpserver.HttpExchange ex) throws IOException {
        if (!authed(ex)) { send(ex, 401, "{\"error\":\"auth\"}"); return; }
        UUID uuid;
        try {
            uuid = UUID.fromString(query(ex, "uuid"));
        } catch (Throwable t) {
            send(ex, 400, "{\"error\":\"uuid\"}");
            return;
        }
        String ip = sessions.ipOf(uuid);
        StringBuilder sb = new StringBuilder("{\"ip\":");
        sb.append(js(ip)).append(",\"accounts\":[");
        boolean first = true;
        for (UUID u : sessions.alts(ip)) {
            if (!first) sb.append(",");
            first = false;
            SessionTracker.Session s = sessions.get(u);
            PlayerData d = AnticheatCore.get().allPlayers().get(u);
            sb.append("{\"uuid\":\"").append(u).append("\",");
            sb.append("\"name\":").append(js(s == null ? "?" : s.name)).append(",");
            sb.append("\"vl\":").append(d == null ? 0 : d.totalVl()).append(",");
            sb.append("\"online\":").append(s != null && s.online);
            sb.append("}");
        }
        sb.append("]}");
        sendJson(ex, sb.toString());
    }

    /** Storico sessioni chiuse: username, connect, disconnect, playtime, brand, ip, version. */
    private void handleSessions(com.sun.net.httpserver.HttpExchange ex) throws IOException {
        if (!authed(ex)) { send(ex, 401, "{\"error\":\"auth\"}"); return; }
        StringBuilder sb = new StringBuilder("[");
        boolean first = true;
        for (SessionTracker.Session s : sessions.history()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("{");
            sb.append("\"uuid\":\"").append(s.uuid == null ? "" : s.uuid).append("\",");
            sb.append("\"name\":").append(js(s.name)).append(",");
            sb.append("\"connect\":").append(s.joinTime).append(",");
            sb.append("\"disconnect\":").append(s.quitTime).append(",");
            sb.append("\"playtimeMs\":").append(Math.max(0, s.quitTime - s.joinTime)).append(",");
            sb.append("\"brand\":").append(js(s.brand)).append(",");
            sb.append("\"ip\":").append(js(s.ip)).append(",");
            sb.append("\"version\":").append(js(s.clientVersion));
            sb.append("}");
        }
        sb.append("]");
        sendJson(ex, sb.toString());
    }

    // ---- helpers ----

    /** Storia punizioni di un player + stato ban. */
    private void handleHistory(com.sun.net.httpserver.HttpExchange ex) throws IOException {
        if (!authed(ex)) { send(ex, 401, "{\"error\":\"auth\"}"); return; }
        UUID uuid;
        try {
            uuid = UUID.fromString(query(ex, "uuid"));
        } catch (Throwable t) {
            send(ex, 400, "{\"error\":\"uuid\"}");
            return;
        }
        StringBuilder sb = new StringBuilder("{\"entries\":[");
        boolean first = true;
        for (PunishmentLog.Entry e : AnticheatCore.get().punishLog().forPlayer(uuid)) {
            if (!first) sb.append(",");
            first = false;
            sb.append(entryJson(e));
        }
        sb.append("]}");
        sendJson(ex, sb.toString());
    }

    private static String entryJson(PunishmentLog.Entry e) {
        return "{\"time\":" + e.time
            + ",\"action\":" + js(e.action)
            + ",\"check\":" + js(e.check)
            + ",\"reason\":" + js(e.reason)
            + ",\"staff\":" + js(e.staff)
            + ",\"banId\":" + js(e.banId == null ? "" : e.banId)
            + ",\"expiresAt\":" + e.expiresAt + "}";
    }

    /** Azione staff: WARN/KICK/TEMPBAN/BAN/FREEZE/UNFREEZE. POST JSON. */
    private void handlePunish(com.sun.net.httpserver.HttpExchange ex) throws IOException {
        if (!authed(ex)) { send(ex, 401, "{\"error\":\"auth\"}"); return; }
        if (!"POST".equals(ex.getRequestMethod())) { send(ex, 405, "{\"error\":\"post\"}"); return; }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String suuid = jstr(body, "uuid");
        String action = jstr(body, "action");
        String reason = jstr(body, "reason");
        String dur = jstr(body, "duration");
        UUID uuid;
        try {
            uuid = UUID.fromString(suuid);
        } catch (Throwable t) {
            send(ex, 400, "{\"error\":\"uuid\"}");
            return;
        }
        if (action == null || action.isEmpty()) { send(ex, 400, "{\"error\":\"action\"}"); return; }
        long durMs = parseDuration(dur);
        SessionTracker.Session s = sessions.get(uuid);
        String name = s == null ? uuid.toString() : s.name;
        try {
            AnticheatCore.get().punishManual(uuid, name, action, reason == null ? "" : reason,
                durMs, "dashboard");
        } catch (Throwable t) {
            send(ex, 500, "{\"error\":" + js(t.getMessage()) + "}");
            return;
        }
        sendJson(ex, "{\"ok\":true}");
    }

    /** Revoca ban. POST {"uuid"}. */
    private void handleUnpunish(com.sun.net.httpserver.HttpExchange ex) throws IOException {
        if (!authed(ex)) { send(ex, 401, "{\"error\":\"auth\"}"); return; }
        if (!"POST".equals(ex.getRequestMethod())) { send(ex, 405, "{\"error\":\"post\"}"); return; }
        String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        UUID uuid;
        try {
            uuid = UUID.fromString(jstr(body, "uuid"));
        } catch (Throwable t) {
            send(ex, 400, "{\"error\":\"uuid\"}");
            return;
        }
        SessionTracker.Session s = sessions.get(uuid);
        String name = s == null ? uuid.toString() : s.name;
        boolean ok = AnticheatCore.get().unbanManual(uuid, name, "dashboard");
        sendJson(ex, "{\"ok\":" + ok + "}");
    }

    /** "30m/12h/7d/perm" -> ms (-1 perm, 0 n/a per warn/kick). */
    private static long parseDuration(String d) {
        if (d == null || d.isEmpty()) return 0;
        String t = d.trim().toLowerCase();
        if (t.equals("perm") || t.equals("permanent")) return -1;
        try {
            if (t.endsWith("mo")) return Long.parseLong(t.substring(0, t.length() - 2)) * 30L * 86400000L;
            if (t.endsWith("m")) return Long.parseLong(t.substring(0, t.length() - 1)) * 60000L;
            if (t.endsWith("h")) return Long.parseLong(t.substring(0, t.length() - 1)) * 3600000L;
            if (t.endsWith("d")) return Long.parseLong(t.substring(0, t.length() - 1)) * 86400000L;
            return Long.parseLong(t) * 60000L;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    /** Estrae "key":"value" da JSON piatto. */
    private static String jstr(String json, String key) {
        try {
            int k = json.indexOf("\"" + key + "\"");
            if (k < 0) return null;
            int c = json.indexOf(':', k);
            if (c < 0) return null;
            int q1 = json.indexOf('"', c);
            if (q1 < 0) return null;
            int q2 = json.indexOf('"', q1 + 1);
            if (q2 < 0) return null;
            return json.substring(q1 + 1, q2);
        } catch (Throwable t) {
            return null;
        }
    }

    private static long playtime(SessionTracker.Session s) {
        long total = s.playtimeTotalMs;
        if (s.online) total += Math.max(0, System.currentTimeMillis() - s.joinTime);
        return total;
    }

    private UUID uuidOf(SessionTracker.Session s) {
        return s == null ? null : s.uuid;
    }

    private static String query(com.sun.net.httpserver.HttpExchange ex, String key) {
        try {
            String q = ex.getRequestURI().getRawQuery();
            if (q == null) return null;
            for (String p : q.split("&")) {
                int i = p.indexOf('=');
                if (i > 0 && p.substring(0, i).equals(key)) {
                    return java.net.URLDecoder.decode(p.substring(i + 1), StandardCharsets.UTF_8);
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String js(String s) {
        if (s == null) return "\"\"";
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private static void send(com.sun.net.httpserver.HttpExchange ex, int code, String body)
            throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.sendResponseHeaders(code, b.length);
        try (OutputStream o = ex.getResponseBody()) { o.write(b); }
    }

    private static void sendJson(com.sun.net.httpserver.HttpExchange ex, String json)
            throws IOException {
        byte[] b = json.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        ex.sendResponseHeaders(200, b.length);
        try (OutputStream o = ex.getResponseBody()) { o.write(b); }
    }

    // ---- pagina (singolo file, niente framework) ----
    // Regole applicate: sidebar 240px, max 3 KPI in riga, base 14px,
    // radius 8px, gap 16px, righe 48px, colore solo semantico,
    // un accento (verde creeper), empty state con CTA.

    private static final String PAGE =
        "<!doctype html><html lang=it><head><meta charset=utf-8>" +
        "<meta name=viewport content='width=device-width,initial-scale=1'>" +
        "<title>NexiraAC</title>" +
        "<style>" +
        ".logoimg{width:120px;height:auto;margin-bottom:8px}" +
        ":root{--bg:#0a0f0a;--side:#0d130d;--card:#111811;--line:#223322;" +
        "--txt:#e9f2e9;--dim:#8aa88a;--acc:#39ff14;--warn:#d29922;--bad:#f85149}" +
        "*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--txt);" +
        "font:14px/1.5 -apple-system,'Segoe UI',Roboto,sans-serif;letter-spacing:-.2px}" +
        "#login{max-width:400px;margin:12vh auto;padding:32px;text-align:center;" +
        "background:var(--card);border-radius:12px}" +
        "#login .logo{font-size:28px;font-weight:800;letter-spacing:2px}" +
        "#login .logo b{color:var(--acc)}" +
        "#login p{color:var(--dim)}" +
        "#login input{width:100%;margin:12px 0}" +
        "#app{display:none}" +
        ".side{position:fixed;left:0;top:0;bottom:0;width:240px;background:var(--side);" +
        "padding:20px 12px;display:flex;flex-direction:column;gap:4px}" +
        ".logo{font-size:20px;font-weight:800;letter-spacing:2px;padding:4px 12px 16px}" +
        ".logo b{color:var(--acc)}" +
        ".nav{padding:10px 12px;border-radius:8px;color:var(--dim);cursor:pointer;" +
        "font-weight:500}" +
        ".nav.on{background:#1a2a1a;color:var(--txt)}" +
        ".nav small{display:block;font-size:11px;font-weight:400}" +
        ".side .foot{margin-top:auto;font-size:11px;color:var(--dim);padding:0 12px}" +
        ".side button{width:100%}" +
        ".main{margin-left:240px;padding:24px;max-width:1200px}" +
        "h2{font-size:24px;font-weight:600;margin:0 0 4px}" +
        ".sub{color:var(--dim);margin:0 0 24px}" +
        ".kpis{display:grid;grid-template-columns:repeat(3,1fr);gap:16px;margin-bottom:24px}" +
        ".kpi{background:var(--card);border-radius:8px;padding:20px 24px}" +
        ".kpi span{font-size:12px;color:var(--dim);text-transform:uppercase;" +
        "letter-spacing:1px;font-weight:500}" +
        ".kpi b{font-size:32px;font-weight:600;display:block;font-variant-numeric:tabular-nums}" +
        ".kpi small{font-size:13px;color:var(--dim)}" +
        ".bar{display:flex;gap:8px;margin-bottom:16px}" +
        "input{background:#0a0f0a;color:var(--txt);border:1px solid var(--line);" +
        "border-radius:8px;padding:9px 14px;font-size:14px}" +
        "input:focus{outline:none;border-color:var(--acc)}" +
        "input[type=text]{flex:1}" +
        "select{background:#0a0f0a;color:var(--txt);border:1px solid var(--line);" +
        "border-radius:8px;padding:9px 12px;font-size:14px}" +
        "button{background:#145214;color:#eaffea;border:1px solid #1f7a1f;border-radius:8px;" +
        "padding:9px 16px;font-size:14px;font-weight:500;cursor:pointer}" +
        "button:hover{background:#1a8a1a}button.ghost{background:transparent;border-color:var(--line);color:var(--txt)}" +
        "table{width:100%;border-collapse:collapse;background:var(--card);border-radius:8px;" +
        "overflow:hidden}" +
        "th,td{text-align:left;padding:0 16px;height:52px;border-bottom:1px solid var(--line)}" +
        "th{font-size:12px;color:var(--dim);text-transform:uppercase;letter-spacing:.5px;" +
        "font-weight:600;height:40px}" +
        "tr:last-child td{border-bottom:0}tbody tr{cursor:pointer}" +
        "tbody tr:hover{background:#162016}" +
        ".vl{display:inline-block;min-width:36px;text-align:center;padding:2px 10px;" +
        "border-radius:12px;font-weight:500;font-size:12px;font-variant-numeric:tabular-nums}" +
        ".v0{background:#12260f;color:var(--acc)}.v1{background:#3a2f10;color:var(--warn)}" +
        ".v2{background:#4a1a1a;color:var(--bad)}" +
        ".dot{display:inline-block;width:9px;height:9px;border-radius:50%;background:#3a4a3a}" +
        ".dot.on{background:var(--acc);box-shadow:0 0 8px var(--acc)}" +
        ".mono{font-family:ui-monospace,Consolas,monospace;font-size:13px}" +
        "#detail{background:var(--card);border-radius:8px;padding:20px 24px;margin-top:24px;display:none}" +
        "#detail h3{margin:0 0 12px;font-size:18px}" +
        ".grid{display:grid;grid-template-columns:repeat(auto-fit,minmax(200px,1fr));gap:16px}" +
        ".grid span{font-size:11px;color:var(--dim);text-transform:uppercase;letter-spacing:1px}" +
        ".empty{text-align:center;padding:48px;color:var(--dim)}" +
        ".empty b{display:block;color:var(--txt);font-size:16px;margin-bottom:8px}" +
        "@media(max-width:800px){.side{display:none}.main{margin:0}.kpis{grid-template-columns:1fr}}" +
        "</style></head><body>" +
        "<div id=login><img src=/logo class=logoimg onerror='this.remove()' alt=''>" +
        "<div class=logo>NEXIRA<b>AC</b></div>" +
        "<p>token dashboard dalla console o da <span class=mono>dashboard.token</span></p>" +
        "<input id=t type=password placeholder='incolla il token'>" +
        "<button onclick=go() style='width:100%'>entra</button>" +
        "<p id=err style='color:var(--bad)'></p></div>" +
        "<div id=app>" +
        "<nav class=side>" +
        "<img src=/logo class='logoimg' style='width:64px;margin:0 12px 8px' onerror='this.remove()' alt=''>" +
        "<div class=logo>NEXIRA<b>AC</b></div>" +
        "<div class='nav on' id=nvP onclick=\"view('P')\">Giocatori<small>live, violazioni, alt account</small></div>" +
        "<div class=nav id=nvS onclick=\"view('S')\">Sessioni<small>connect, disconnect, playtime, ip</small></div>" +
        "<div class=foot><div id=v>—</div>" +
        "<button class=ghost onclick='logout()' style='margin-top:8px'>esci</button></div></nav>" +
        "<div class=main id=viewP><h2>Giocatori</h2>" +
        "<p class=sub id=sub>—</p>" +
        "<div class=kpis>" +
        "<div class=kpi><span>online</span><b id=cOn>0</b><small id=cOnS>—</small></div>" +
        "<div class=kpi><span>VL totali</span><b id=cVl>0</b><small id=cVlS>—</small></div>" +
        "<div class=kpi><span>flaggati</span><b id=cFl>0</b><small>VL &gt; 0 adesso</small></div>" +
        "</div>" +
        "<div class=bar><input id=q type=text placeholder='cerca giocatore...  (filtra mentre scrivi)' oninput=render()>" +
        "<button class=ghost onclick=load()>aggiorna</button></div>" +
        "<table><thead><tr><th></th><th>giocatore</th><th>ip</th><th>client</th>" +
        "<th>ping</th><th>vl</th><th>playtime</th></tr></thead><tbody id=tb></tbody></table>" +
        "<div class=empty id=empty style='display:none'><b>Nessun giocatore trovato</b>" +
        "prova a cambiare ricerca o aspetta che qualcuno entri</div>" +
        "<div id=detail></div></div>" +
        "<div class=main id=viewS style='display:none'><h2>Sessioni</h2>" +
        "<p class=sub id=subS>—</p>" +
        "<div class=bar><input id=q2 type=text placeholder='cerca per nome o ip...' oninput=renderS()>" +
        "<button class=ghost onclick=loadS()>aggiorna</button></div>" +
        "<table><thead><tr>" +
        "<th onclick=\"sortS('name')\">username <span id=s_name></span></th>" +
        "<th onclick=\"sortS('connect')\">connect <span id=s_connect></span></th>" +
        "<th onclick=\"sortS('disconnect')\">disconnect <span id=s_disconnect></span></th>" +
        "<th onclick=\"sortS('playtimeMs')\">playtime <span id=s_playtimeMs></span></th>" +
        "<th onclick=\"sortS('brand')\">client brand <span id=s_brand></span></th>" +
        "<th onclick=\"sortS('ip')\">ip <span id=s_ip></span></th>" +
        "<th onclick=\"sortS('version')\">version <span id=s_version></span></th>" +
        "</tr></thead><tbody id=tbS></tbody></table>" +
        "<div class=empty id=emptyS style='display:none'><b>Nessuna sessione</b>" +
        "le sessioni chiuse compaiono qui dopo il primo quit</div>" +
        "</div></div>" +
        "<script>" +
        "let T=localStorage.getItem('nx')||'',D=[],S=[],sk='connect',sd=-1;" +
        "function view(v){document.getElementById('viewP').style.display=v==='P'?'block':'none';" +
        "document.getElementById('viewS').style.display=v==='S'?'block':'none';" +
        "document.getElementById('nvP').className='nav'+(v==='P'?' on':'');" +
        "document.getElementById('nvS').className='nav'+(v==='S'?' on':'');" +
        "if(v==='S')loadS()}" +
        "if(T){document.getElementById('t').value=T;enter()}" +
        "async function go(){T=document.getElementById('t').value.trim();" +
        "localStorage.setItem('nx',T);enter()}" +
        "function logout(){localStorage.removeItem('nx');location.reload()}" +
        "async function enter(){const r=await fetch('/api/players?token='+encodeURIComponent(T));" +
        "if(!r.ok){document.getElementById('err').textContent=" +
        "'token errato — prendilo da dashboard.token o con ac dashboard token';return}" +
        "document.getElementById('login').style.display='none';" +
        "document.getElementById('app').style.display='block';load();setInterval(()=>{load();" +
        "if(document.getElementById('viewS').style.display!=='none')loadS()},10000)}" +
        "function esc(s){return String(s??'').replace(/&/g,'&amp;').replace(/</g,'&lt;')}" +
        "function fmt(ms){const m=Math.floor(ms/60000);if(m<60)return m+' min';" +
        "return Math.floor(m/60)+' h '+(m%60)+' min'}" +
        "async function load(){const r=await fetch('/api/players?token='+encodeURIComponent(T));" +
        "if(!r.ok)return;D=await r.json();render()}" +
        "function fmtT(ts){if(!ts)return '—';const d=new Date(ts);" +
        "const p=n=>String(n).padStart(2,'0');" +
        "return p(d.getDate())+'/'+p(d.getMonth()+1)+'/'+d.getFullYear()+', '+p(d.getHours())+':'+p(d.getMinutes())+':'+p(d.getSeconds())}" +
        "async function loadS(){const r=await fetch('/api/sessions?token='+encodeURIComponent(T));" +
        "if(!r.ok)return;S=await r.json();renderS()}" +
        "function sortS(k){if(sk===k)sd=-sd;else{sk=k;sd=1}renderS()}" +
        "function renderS(){const q=document.getElementById('q2').value.toLowerCase();" +
        "let rows=S.filter(s=>(s.name+' '+s.ip).toLowerCase().includes(q));" +
        "rows.sort((a,b)=>{const x=a[sk]??'',y=b[sk]??'';" +
        "return (typeof x==='number'?x-y:String(x).localeCompare(String(y)))*sd});" +
        "document.getElementById('subS').textContent=S.length+' sessioni chiuse';" +
        "document.getElementById('emptyS').style.display=rows.length?'none':'block';" +
        "for(const k of ['name','connect','disconnect','playtimeMs','brand','ip','version']){" +
        "document.getElementById('s_'+k).textContent=sk===k?(sd>0?'▲':'▼'):''}" +
        "document.getElementById('tbS').innerHTML=rows.map(s=>" +
        "'<tr><td><b>'+esc(s.name)+'</b></td>'+" +
        "'<td class=mono>'+fmtT(s.connect)+'</td><td class=mono>'+fmtT(s.disconnect)+'</td>'+" +
        "'<td>'+fmt(s.playtimeMs)+'</td><td>'+esc(s.brand)+'</td>'+" +
        "'<td class=mono>'+esc(s.ip)+'</td><td class=mono>'+esc(s.version)+'</td></tr>').join('')}" +
        "function render(){const q=document.getElementById('q').value.toLowerCase();" +
        "const rows=D.filter(p=>p.name.toLowerCase().includes(q));" +
        "const on=D.filter(p=>p.online);" +
        "const fl=D.filter(p=>p.vl>0);" +
        "document.getElementById('cOn').textContent=on.length;" +
        "document.getElementById('cOnS').textContent=D.length+' sessioni totali';" +
        "document.getElementById('cVl').textContent=D.reduce((a,p)=>a+p.vl,0);" +
        "document.getElementById('cVlS').textContent=" +
        "D.filter(p=>p.brand&&p.brand!=='-').length+' con mod client';" +
        "document.getElementById('cFl').textContent=fl.length;" +
        "document.getElementById('sub').textContent=" +
        "on.length+' online · aggiornato ora';" +
        "document.getElementById('empty').style.display=rows.length?'none':'block';" +
        "document.getElementById('tb').innerHTML=rows.map(p=>" +
        "'<tr onclick=\"det(\\''+p.uuid+'\\')\">'+'<td><span class=\"dot'+(p.online?' on':'')+'\"></span></td>'+" +
        "'<td><b>'+esc(p.name)+'</b></td><td class=mono>'+esc(p.ip)+'</td>'+" +
        "'<td>'+esc(p.brand)+(p.client&&p.client!=='-'?' '+esc(p.client):'')+'</td>'+" +
        "'<td>'+p.ping+' ms</td>'+" +
        "'<td><span class=\"vl '+(p.vl==0?'v0':p.vl<20?'v1':'v2')+'\">'+p.vl+'</span></td>'+" +
        "'<td>'+fmt(p.playtimeMs)+'</td></tr>').join('')}" +
        "async function det(u){const r=await fetch('/api/player?uuid='+u+'&token='+encodeURIComponent(T));" +
        "const p=await r.json();" +
        "const a=await (await fetch('/api/alts?uuid='+u+'&token='+encodeURIComponent(T))).json();" +
        "const h=await (await fetch('/api/history?uuid='+u+'&token='+encodeURIComponent(T))).json();" +
        "const el=document.getElementById('detail');el.style.display='block';" +
        "el.innerHTML='<h3>'+esc(p.name)+'</h3>'+" +
        "'<div class=grid><div><span>uuid</span><div class=mono>'+esc(p.uuid)+'</div></div>'+" +
        "'<div><span>ip</span><div class=mono>'+esc(p.ip)+'</div></div>'+" +
        "'<div><span>client</span><div>'+esc(p.brand)+' '+esc(p.client)+'</div></div>'+" +
        "'<div><span>violazioni</span><div class=mono>'+esc(p.violations)+'</div></div></div>'+" +
        "'<h3 style=margin-top:16px>provvedimento</h3>'+" +
        "'<div class=bar><select id=pa>'+" +
        "'<option value=WARN>warn</option><option value=KICK>kick</option>'+" +
        "'<option value=TEMPBAN>tempban</option><option value=BAN>ban</option></select>'+" +
        "'<select id=pd><option value=\"\">—</option><option>30m</option><option>12h</option>'+" +
        "'<option>7d</option><option>30d</option><option>perm</option></select>'+" +
        "'<input id=pr type=text placeholder=motivo>'+" +
        "'<button onclick=\"punish(\\''+u+'\\')\">applica</button>'+" +
        "'<button class=ghost onclick=\"unpunish(\\''+u+'\\')\">unban</button></div>'+" +
        "'<p id=pm style=color:var(--dim)></p>'+" +
        "'<h3 style=margin-top:16px>storia ('+h.entries.length+')</h3>'+(h.entries.length?" +
        "h.entries.map(e=>'<div>'+fmtT(e.time)+' — <b>'+esc(e.action)+'</b> '+esc(e.check)+' '+esc(e.reason)+' '+(e.banId?'Ban ID '+esc(e.banId)+' ':'')+'<span style=color:var(--dim)>('+esc(e.staff)+')</span></div>').join(''):" +
        "'<p style=color:var(--dim)>nessun provvedimento registrato</p>')+" +
        "'<h3 style=margin-top:16px>stesso ip ('+a.accounts.length+')</h3>'+(a.accounts.length?" +
        "a.accounts.map(x=>'<div>'+esc(x.name)+' — VL '+x.vl+(x.online?' (online)':'')+'</div>').join(''):" +
        "'<p style=color:var(--dim)>nessun altro account da questo ip</p>');" +
        "el.scrollIntoView()}" +
        "async function punish(u){const b={uuid:u," +
        "action:document.getElementById('pa').value," +
        "duration:document.getElementById('pd').value," +
        "reason:document.getElementById('pr').value};" +
        "const r=await fetch('/api/punish?token='+encodeURIComponent(T)," +
        "{method:'POST',body:JSON.stringify(b)});" +
        "document.getElementById('pm').textContent=r.ok?'applicato':'errore';" +
        "load();det(u)}" +
        "async function unpunish(u){" +
        "const r=await fetch('/api/unpunish?token='+encodeURIComponent(T)," +
        "{method:'POST',body:JSON.stringify({uuid:u})});" +
        "const j=await r.json();" +
        "document.getElementById('pm').textContent=j.ok?'unbannato':'non era bannato';" +
        "det(u)}" +
        "</script></body></html>";
}
