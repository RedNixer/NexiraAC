package it.anticheat.core.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Soglie protezione server (login flood, exploit, spam) da protection.json.
 * Parser manuale, zero dipendenze. Creato col default al primo avvio.
 */
public class ProtectionConfig {
    // login flood
    public int maxLoginPer10s = 5;
    public int loginStrikesForBan = 3;
    public int loginBanMinutes = 10;
    public int maxJoinQuitPer30s = 3;
    // exploit pacchetti
    public int creativeNbtMaxChars = 60000;
    public int payloadMaxBytes = 32768;
    public int maxClickPerSec = 12;
    public int maxTabPerSec = 5;
    // spam gioco
    public int maxChatPer3s = 6;
    public int chatMuteSeconds = 30;
    public int maxCmdPer3s = 8;
    public int bookMaxChars = 20000;

    public static String defaultJson() {
        return "{\n"
            + "  \"login\": { \"max-per-10s\": 5, \"strikes-for-ban\": 3, \"ban-minutes\": 10, \"max-joinquit-per-30s\": 3 },\n"
            + "  \"exploit\": { \"creative-nbt-max\": 60000, \"payload-max-bytes\": 32768, \"max-click-per-sec\": 12, \"max-tab-per-sec\": 5 },\n"
            + "  \"spam\": { \"max-chat-per-3s\": 6, \"mute-seconds\": 30, \"max-cmd-per-3s\": 8, \"book-max-chars\": 20000 }\n"
            + "}\n";
    }

    public static ProtectionConfig load(Path file) throws IOException {
        ProtectionConfig c = new ProtectionConfig();
        if (!Files.exists(file)) {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.writeString(file, defaultJson());
            return c;
        }
        String j = Files.readString(file);
        c.maxLoginPer10s = num(j, "max-per-10s", c.maxLoginPer10s);
        c.loginStrikesForBan = num(j, "strikes-for-ban", c.loginStrikesForBan);
        c.loginBanMinutes = num(j, "ban-minutes", c.loginBanMinutes);
        c.maxJoinQuitPer30s = num(j, "max-joinquit-per-30s", c.maxJoinQuitPer30s);
        c.creativeNbtMaxChars = num(j, "creative-nbt-max", c.creativeNbtMaxChars);
        c.payloadMaxBytes = num(j, "payload-max-bytes", c.payloadMaxBytes);
        c.maxClickPerSec = num(j, "max-click-per-sec", c.maxClickPerSec);
        c.maxTabPerSec = num(j, "max-tab-per-sec", c.maxTabPerSec);
        c.maxChatPer3s = num(j, "max-chat-per-3s", c.maxChatPer3s);
        c.chatMuteSeconds = num(j, "mute-seconds", c.chatMuteSeconds);
        c.maxCmdPer3s = num(j, "max-cmd-per-3s", c.maxCmdPer3s);
        c.bookMaxChars = num(j, "book-max-chars", c.bookMaxChars);
        return c;
    }

    private static int num(String json, String key, int def) {
        int k = json.indexOf('"' + key + '"');
        if (k < 0) return def;
        int colon = json.indexOf(':', k);
        if (colon < 0) return def;
        int s = colon + 1;
        while (s < json.length() && !Character.isDigit(json.charAt(s)) && json.charAt(s) != '-') s++;
        int e = s;
        while (e < json.length() && (Character.isDigit(json.charAt(e)) || json.charAt(e) == '-')) e++;
        if (s >= e) return def;
        try {
            return Integer.parseInt(json.substring(s, e));
        } catch (NumberFormatException ex) {
            return def;
        }
    }
}
