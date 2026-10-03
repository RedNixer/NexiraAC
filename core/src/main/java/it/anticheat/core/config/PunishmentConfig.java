package it.anticheat.core.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Per-check punishments from punishments.json. Format documented in PUNISHMENTS.md. */
public class PunishmentConfig {

    public static class Rule {
        public int vl;
        public String action = "warn";
        public String duration = "";
        public String reason = "";
    }

    public int defaultWarnVl = 20;
    public int defaultKickVl = 50;
    public int defaultBanVl = 100;
    /** check -> regole ordinate per vl crescente. */
    public final Map<String, List<Rule>> checks = new LinkedHashMap<>();

    /** Highest rule with vl <= totalVl, or global-threshold fallback. */
    public Rule match(String check, int totalVl) {
        return match(check, totalVl, defaultWarnVl, defaultKickVl, defaultBanVl);
    }

    /** Same with external fallback thresholds. */
    public Rule match(String check, int totalVl, int fbWarn, int fbKick, int fbBan) {
        List<Rule> list = checks.get(check);
        Rule best = null;
        if (list != null) {
            for (Rule r : list) {
                if (totalVl >= r.vl) best = r;
            }
            if (best != null) return best;
        }
        // fallback: soglie classiche (dalla GUI/config)
        Rule f = new Rule();
        if (totalVl >= fbBan) { f.vl = fbBan; f.action = "ban"; }
        else if (totalVl >= fbKick) { f.vl = fbKick; f.action = "kick"; }
        else if (totalVl >= fbWarn) { f.vl = fbWarn; f.action = "warn"; }
        else return null;
        return f;
    }

    /** 30m, 12h, 7d, 12mo, perm. ms, -1 = permanent, -2 = invalid. */
    public static long parseDurationMs(String s) {
        if (s == null) return -1;
        s = s.trim().toLowerCase();
        if (s.isEmpty() || s.equals("perm") || s.equals("permanent")) return -1;
        try {
            if (s.endsWith("mo")) {
                return Long.parseLong(s.substring(0, s.length() - 2)) * 30L * 24 * 60 * 60 * 1000;
            }
            char u = s.charAt(s.length() - 1);
            long n = Long.parseLong(s.substring(0, s.length() - 1));
            switch (u) {
                case 'm': return n * 60 * 1000;
                case 'h': return n * 60 * 60 * 1000;
                case 'd': return n * 24 * 60 * 60 * 1000;
                default: return -2;
            }
        } catch (NumberFormatException e) {
            // numero puro = minuti
            try {
                return Long.parseLong(s) * 60 * 1000;
            } catch (NumberFormatException e2) {
                return -2;
            }
        }
    }

    public static String defaultJson() {
        return "{\n"
            + "  \"defaults\": { \"warnVl\": 20, \"kickVl\": 50, \"banVl\": 100 },\n"
            + "  \"checks\": {\n"
            + "    \"KillAura\": [\n"
            + "      { \"vl\": 30, \"action\": \"warn\", \"reason\": \"KillAura sospetta\" },\n"
            + "      { \"vl\": 60, \"action\": \"kick\", \"reason\": \"KillAura rilevata\" },\n"
            + "      { \"vl\": 100, \"action\": \"tempban\", \"duration\": \"7d\", \"reason\": \"KillAura\" }\n"
            + "    ],\n"
            + "    \"Reach\": [\n"
            + "      { \"vl\": 40, \"action\": \"warn\", \"reason\": \"Reach sospetta\" },\n"
            + "      { \"vl\": 80, \"action\": \"kick\", \"reason\": \"Reach impossibile\" },\n"
            + "      { \"vl\": 120, \"action\": \"tempban\", \"duration\": \"3d\", \"reason\": \"Reach\" }\n"
            + "    ],\n"
            + "    \"Speed\": [\n"
            + "      { \"vl\": 40, \"action\": \"warn\", \"reason\": \"Speed sospetta\" },\n"
            + "      { \"vl\": 80, \"action\": \"kick\", \"reason\": \"Speed impossibile\" },\n"
            + "      { \"vl\": 120, \"action\": \"tempban\", \"duration\": \"3d\", \"reason\": \"Speed\" }\n"
            + "    ],\n"
            + "    \"Fly\": [\n"
            + "      { \"vl\": 30, \"action\": \"warn\", \"reason\": \"Fly sospetto\" },\n"
            + "      { \"vl\": 60, \"action\": \"kick\", \"reason\": \"Volo impossibile\" },\n"
            + "      { \"vl\": 100, \"action\": \"tempban\", \"duration\": \"7d\", \"reason\": \"Fly\" }\n"
            + "    ]\n"
            + "  }\n"
            + "}\n";
    }

    /** Load from file, creating it with defaults when missing. */
    public static PunishmentConfig load(Path file) throws IOException {
        PunishmentConfig c = new PunishmentConfig();
        if (!Files.exists(file)) {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, defaultJson());
            return c;
        }
        c.parse(Files.readString(file));
        return c;
    }

    /** Hand-rolled parser, no dependencies. */
    public void parse(String json) {
        // defaults
        String defBlock = slice(json, "\"defaults\"", "{", "}");
        if (defBlock != null) {
            defaultWarnVl = parseInt(defBlock, "warnVl", defaultWarnVl);
            defaultKickVl = parseInt(defBlock, "kickVl", defaultKickVl);
            defaultBanVl = parseInt(defBlock, "banVl", defaultBanVl);
        }
        String checksBlock = slice(json, "\"checks\"", "{", null);
        if (checksBlock == null) return;
        // ogni "Nome": [ ... ] top-level dentro checks
        int i = 0;
        while (i < checksBlock.length()) {
            int q1 = checksBlock.indexOf('"', i);
            if (q1 < 0) break;
            int q2 = checksBlock.indexOf('"', q1 + 1);
            if (q2 < 0) break;
            String name = checksBlock.substring(q1 + 1, q2);
            int b1 = checksBlock.indexOf('[', q2);
            if (b1 < 0) break;
            int b2 = matchBracket(checksBlock, b1, '[', ']');
            if (b2 < 0) break;
            String arr = checksBlock.substring(b1 + 1, b2);
            List<Rule> rules = new ArrayList<>();
            int j = 0;
            while (j < arr.length()) {
                int o1 = arr.indexOf('{', j);
                if (o1 < 0) break;
                int o2 = matchBracket(arr, o1, '{', '}');
                if (o2 < 0) break;
                String obj = arr.substring(o1 + 1, o2);
                Rule r = new Rule();
                r.vl = parseInt(obj, "vl", -1);
                String a = parseStr(obj, "action");
                if (a != null) r.action = a.toLowerCase();
                String du = parseStr(obj, "duration");
                if (du != null) r.duration = du;
                String re = parseStr(obj, "reason");
                if (re != null) r.reason = re;
                if (r.vl >= 0) rules.add(r);
                j = o2 + 1;
            }
            rules.sort((x, y) -> Integer.compare(x.vl, y.vl));
            if (!rules.isEmpty()) checks.put(name, rules);
            i = b2 + 1;
        }
    }

    private static String slice(String s, String key, String open, String close) {
        int k = s.indexOf(key);
        if (k < 0) return null;
        int o = s.indexOf(open, k + key.length());
        if (o < 0) return null;
        if (close == null) return s.substring(o + 1);
        int c = matchBracket(s, o, open.charAt(0), close.charAt(0));
        if (c < 0) return null;
        return s.substring(o + 1, c);
    }

    private static int matchBracket(String s, int open, char o, char c) {
        int depth = 0;
        boolean inStr = false;
        for (int i = open; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '"' && (i == 0 || s.charAt(i - 1) != '\\')) inStr = !inStr;
            if (inStr) continue;
            if (ch == o) depth++;
            else if (ch == c) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }

    private static int parseInt(String obj, String key, int def) {
        int k = obj.indexOf('"' + key + '"');
        if (k < 0) k = obj.indexOf(key);
        if (k < 0) return def;
        int colon = obj.indexOf(':', k);
        if (colon < 0) return def;
        int start = colon + 1;
        while (start < obj.length() && !Character.isDigit(obj.charAt(start)) && obj.charAt(start) != '-') start++;
        int end = start;
        while (end < obj.length() && (Character.isDigit(obj.charAt(end)) || obj.charAt(end) == '-')) end++;
        if (start >= end) return def;
        try {
            return Integer.parseInt(obj.substring(start, end));
        } catch (NumberFormatException e) {
            return def;
        }
    }

    private static String parseStr(String obj, String key) {
        int k = obj.indexOf('"' + key + '"');
        if (k < 0) return null;
        int colon = obj.indexOf(':', k);
        if (colon < 0) return null;
        int q1 = obj.indexOf('"', colon + 1);
        if (q1 < 0) return null;
        int q2 = obj.indexOf('"', q1 + 1);
        if (q2 < 0) return null;
        return obj.substring(q1 + 1, q2);
    }
}
