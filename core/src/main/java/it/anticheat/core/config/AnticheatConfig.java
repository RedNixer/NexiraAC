package it.anticheat.core.config;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Config minimale senza dipendenze esterne.
 * Gli adapter (Paper/Fabric) la caricano da config.yml e la passano al core.
 */
public class AnticheatConfig {
    /** Nomi canonici dei check, nello stesso ordine mostrato in GUI. */
    public static final List<String> ALL_CHECKS = List.of(
        "Speed", "Fly", "NoFall", "Step", "Scaffold",
        "Reach", "KillAura", "AutoClicker", "FastBreak", "AutoTotem",
        "Sprint", "NoSlow", "Timer", "Jesus", "GUIMove",         "Multitask", "Spider",
        "AimSnap", "AimLock", "Prediction", "PacketOrder", "RotationStream", "Interact");

    public List<UUID> adminUuids = new ArrayList<>();
    public boolean usePermissionToo = true;
    /** UUID esenti dai controlli (tester): separati dagli admin. */
    public List<UUID> exemptUuids = new ArrayList<>();
    /** Se true, ogni flag vlAdd>=4 va in chat a tutti gli OP, non solo con verbose. */
    public boolean announceAll = false;
    public int warnVl = 20;
    public int kickVl = 50;
    public int banVl = 100;
    public boolean clientRequired = false;
    public String clientMinVersion = "0.1.0";
    /** Link appeal mostrato nella schermata ban (vuoto = riga nascosta). */
    public String appealUrl = "";
    public boolean verbose = false;
    /** Check disattivati = false. Assenti = attivi (default true). */
    public java.util.Map<String, Boolean> checkEnabled = new java.util.HashMap<>();
    /** AutoClicker: deviazione std max per flaggare. Piu basso = piu severo. */
    public double autoClickerStd = 4.0;
    /** Limite CPS: sopra questa soglia (sostenuta) scatta il flag, ovunque. Ummano max ~25. */
    public int cpsLimit = 25;
    /** Setback: riporta a terra ai flag movimento sopra min-vl. */
    public boolean setbackEnabled = true;
    public int setbackMinVl = 15;
    /** Test-mode: logga e avvisa ma non applica mai warn/kick/ban/setback. */
    public boolean testMode = false;
    /** Check sperimentali (Multitask, Spider): girano solo se true. */
    public boolean experimentalChecks = false;
    /** Check pacchetto (ProtocolLib su Paper): master switch, auto-off senza PL. */
    public boolean packetChecks = true;
    /** Dashboard web locale (sola lettura v1): bind localhost di default. */
    public boolean dashEnabled = true;
    public int dashPort = 25596;
    public String dashBind = "127.0.0.1";

    public boolean isAdmin(UUID uuid) {
        return adminUuids.contains(uuid);
    }

    public boolean isCheckEnabled(String check) {
        Boolean b = checkEnabled.get(check);
        return b == null || b;
    }

    public static String defaultYaml() {
        return "# AntiCheat config (Paper + Fabric condividono gli stessi nomi)\n"
            + "admins:\n"
            + "  uuids:\n"
            + "    - \"00000000-0000-0000-0000-000000000000\" # <-- metti qui gli UUID staff\n"
            + "  use-permission-too: true # fallback a permesso 'anticheat.admin' / LuckPerms\n"
            + "exempt:\n"
            + "  uuids: [] # tester esenti dai controlli (non admin, solo skip check)\n"
            + "announce-all: false # se true: ogni flag grave va in chat a tutti gli OP\n"
            + "punishments:\n"
            + "  warn-vl: 20\n"
            + "  kick-vl: 50\n"
            + "  ban-vl: 100\n"
            + "setback: # riporta a terra ai flag movimento (Speed/Fly/Step/NoFall/Scaffold)\n"
            + "  enabled: true\n"
            + "  min-vl: 15\n"
            + "test-mode: false # se true: logga tutto ma non kicka/banna e niente setback\n"
            + "packet-checks: true # solo Paper + ProtocolLib: timer preciso + range preciso\n"
            + "dashboard: # web locale sola lettura (http://127.0.0.1:25596 + token)\n"
            + "  enabled: true\n"
            + "  port: 25596\n"
            + "  bind: \"127.0.0.1\" # mai 0.0.0.0 senza reverse proxy\n"
            + "checks: # true = attivo, false = disattivo (modificabile anche dalla GUI con /ac)\n"
            + "  Speed: true\n"
            + "  Fly: true\n"
            + "  NoFall: true\n"
            + "  Step: true\n"
            + "  Scaffold: true\n"
            + "  Reach: true\n"
            + "  KillAura: true\n"
            + "  AutoClicker: true\n"
            + "  FastBreak: true\n"
            + "  AutoTotem: true # beta: tolleranza alta\n"
            + "  Sprint: true\n"
            + "  NoSlow: true\n"
            + "  Timer: true # di fatto solo Paper (su Fabric non scatta)\n"
            + "  Jesus: true\n"
            + "  GUIMove: true\n"
            + "  Multitask: true # sperimentale: gira solo con experimental-checks\n"
            + "  Spider: true # sperimentale: gira solo con experimental-checks\n"
            + "  AimSnap: true\n"
            + "  AimLock: true\n"
            + "  Prediction: true # simulatore vanilla (Fase 1): sostituisce le soglie fisse\n"
            + "  PacketOrder: true # solo Paper+PL (Fase 2 P2): ordine pacchetti combat\n"
            + "  RotationStream: true # solo Paper+PL (Fase 2 P3): rotazioni raw dai LOOK\n"
            + "  Interact: true # solo Paper+PL (Passo 3): uso+colpo, self-hit, multi-entita, range\n"
            + "experimental-checks: false # attiva i check sperimentali (piu FP)\n"
            + "tuning: # sensibilita (modificabile anche dalla GUI)\n"
            + "  autoclicker-std: 4.0 # deviazione max click: 2.5 rigido, 4.0 normale, 6.0 largo\n"
            + "  cps-limit: 25 # CPS massimi: sopra (sostenuti ~2s) = macro. Ummano ~25, /ac cps <n> per cambiarlo\n"
            + "client-mod:\n"
            + "  required: false # se true, chi non ha la mod viene kickato (sconsigliato)\n"
            + "  min-version: \"0.1.0\"\n"
            + "appeal-url: \"\" # link appeal nella schermata ban (es. discord.gg/tuoserver)\n"
            + "debug:\n"
            + "  verbose: false\n";
    }

    /** Parser YAML minimalista: legge solo le chiavi che ci servono, ignora il resto. */
    public static AnticheatConfig load(Path file) throws IOException {
        AnticheatConfig c = new AnticheatConfig();
        if (!Files.exists(file)) {
            Files.createDirectories(file.getParent());
            Files.writeString(file, defaultYaml());
            return c;
        }
        boolean inUuids = false;
        boolean inExemptUuids = false;
        boolean inChecks = false;
        String lastTopSection = "";
        for (String raw : Files.readAllLines(file)) {
            String line = raw.trim();
            if (line.startsWith("#") || line.isEmpty()) continue;
            if (line.equals("checks:")) { inChecks = true; inUuids = false; inExemptUuids = false; continue; }
            if (line.equals("exempt:")) { lastTopSection = "exempt"; inExemptUuids = false; inUuids = false; inChecks = false; continue; }
            if (!raw.isEmpty() && raw.charAt(0) != ' ' && line.contains(":") && !line.startsWith("-")) {
                inChecks = false; // nuova sezione top-level
                int ci = line.indexOf(':');
                if (ci > 0) lastTopSection = line.substring(0, ci).trim();
            }
            if (line.startsWith("uuids:")) {
                String rest = line.substring("uuids:".length()).trim();
                // exempt/uuids (sotto sezione exempt:) vs admins/uuids: l'indentazione
                // decide — ma semplice: se la riga "exempt:" e apparsa dopo l'ultimo
                // "admins:", siamo in exempt. Tracciamo con sezione corrente.
                boolean isExempt = lastTopSection.equals("exempt");
                if (rest.startsWith("[")) {
                    // formato inline: uuids: ["uuid1", "uuid2"]
                    for (String part : rest.replaceAll("[\\[\\]]", "").split(",")) {
                        try {
                            UUID u = UUID.fromString(part.trim().replace("\"", "").replace("'", ""));
                            if (isExempt) c.exemptUuids.add(u); else c.adminUuids.add(u);
                        } catch (IllegalArgumentException ignored) {}
                    }
                    inUuids = false;
                    inExemptUuids = false;
                } else {
                    if (isExempt) { inExemptUuids = true; inUuids = false; }
                    else { inUuids = true; inExemptUuids = false; }
                }
                continue;
            }
            if (line.equals("admins:")) { lastTopSection = "admins"; continue; }
            if ((inUuids || inExemptUuids) && line.startsWith("-")) {
                String id = line.substring(1).trim().replace("\"", "").replace("'", "");
                try {
                    UUID u = UUID.fromString(id);
                    if (inExemptUuids) c.exemptUuids.add(u); else c.adminUuids.add(u);
                } catch (IllegalArgumentException ignored) {}
                continue;
            }
            if ((inUuids || inExemptUuids) && !line.startsWith("-") && line.contains(":")) {
                inUuids = false;
                inExemptUuids = false;
            }
            if (inChecks && line.contains(":")) {
                String[] kv = line.split(":", 2);
                if (kv.length == 2) {
                    String k = kv[0].trim();
                    String v = kv[1].trim();
                    if (ALL_CHECKS.contains(k) && (v.equals("true") || v.equals("false"))) {
                        c.checkEnabled.put(k, Boolean.parseBoolean(v));
                    }
                }
                continue;
            }
            if (line.startsWith("warn-vl:")) c.warnVl = parseInt(line, c.warnVl);
            else if (line.startsWith("kick-vl:")) c.kickVl = parseInt(line, c.kickVl);
            else if (line.startsWith("ban-vl:")) c.banVl = parseInt(line, c.banVl);
            else if (line.startsWith("autoclicker-std:")) c.autoClickerStd = parseDouble(line, c.autoClickerStd);
            else if (line.startsWith("cps-limit:")) c.cpsLimit = parseInt(line, c.cpsLimit);
            else if (line.startsWith("enabled:") && lastTopSection.equals("dashboard")) c.dashEnabled = !line.contains("false");
            else if (line.startsWith("enabled:")) c.setbackEnabled = !line.contains("false");
            else if (line.startsWith("min-vl:")) c.setbackMinVl = parseInt(line, c.setbackMinVl);
            else if (line.startsWith("test-mode:")) c.testMode = line.contains("true");
            else if (line.startsWith("packet-checks:")) c.packetChecks = !line.contains("false");
            else if (line.startsWith("port:")) c.dashPort = parsePort(line, c.dashPort);
            else if (line.startsWith("bind:")) c.dashBind = parseHost(line, c.dashBind);
            else if (line.startsWith("experimental-checks:")) c.experimentalChecks = line.contains("true");
            else if (line.startsWith("required:")) c.clientRequired = line.contains("true");
            else if (line.startsWith("min-version:")) c.clientMinVersion = line.split(":", 2)[1].trim().replace("\"", "").replace("'", "");
            else if (line.startsWith("appeal-url:")) {
                // URL con :// : prendi tutto dopo la chiave, poi pulisci
                String u = line.substring("appeal-url:".length()).trim()
                    .replace("\"", "").replace("'", "");
                int hash = u.indexOf('#');
                if (hash >= 0) u = u.substring(0, hash);
                u = u.trim();
                if (!u.isEmpty()) c.appealUrl = u;
            }
            else if (line.startsWith("verbose:")) c.verbose = line.contains("true");
            else if (line.startsWith("use-permission-too:")) c.usePermissionToo = !line.contains("false");
            else if (line.startsWith("announce-all:")) c.announceAll = line.contains("true");
        }
        return c;
    }

    private static int parseInt(String line, int def) {
        try { return Integer.parseInt(line.split(":", 2)[1].trim()); }
        catch (Exception e) { return def; }
    }

    /** Porta: primo token numerico dopo ':', ignora commenti inline. */
    private static int parsePort(String line, int def) {
        try {
            String rest = line.split(":", 2)[1].trim();
            StringBuilder num = new StringBuilder();
            for (char ch : rest.toCharArray()) {
                if (Character.isDigit(ch)) num.append(ch);
                else if (num.length() > 0) break;
            }
            if (num.length() == 0) return def;
            int p = Integer.parseInt(num.toString());
            return (p > 0 && p < 65536) ? p : def;
        } catch (Exception e) {
            return def;
        }
    }

    /** Host: primo token (niente virgolette, niente commenti inline). */
    private static String parseHost(String line, String def) {
        try {
            String rest = line.split(":", 2)[1].trim()
                .replace("\"", "").replace("'", "");
            int hash = rest.indexOf('#');
            if (hash >= 0) rest = rest.substring(0, hash);
            rest = rest.trim();
            if (rest.isEmpty()) return def;
            return rest.split("\\s+")[0];
        } catch (Exception e) {
            return def;
        }
    }

    private static double parseDouble(String line, double def) {
        try { return Double.parseDouble(line.split(":", 2)[1].trim()); }
        catch (Exception e) { return def; }
    }

    public String autoClickerName() {
        if (autoClickerStd <= 2.5) return "Rigido";
        if (autoClickerStd <= 4.0) return "Normale";
        return "Largo";
    }

    public void cycleAutoClicker() {
        if (autoClickerStd <= 2.5) autoClickerStd = 4.0;
        else if (autoClickerStd <= 4.0) autoClickerStd = 6.0;
        else autoClickerStd = 2.5;
    }

    /**
     * Riscrive solo la lista admins.uuids nel file, lasciando intatto il resto
     * (soglie, commenti, altre sezioni). Se la sezione manca, la crea.
     */
    public void save(Path file) throws IOException {
        List<String> lines;
        if (Files.exists(file)) lines = new ArrayList<>(Files.readAllLines(file));
        else lines = new ArrayList<>(Arrays.asList(defaultYaml().split("\n")));

        int uuidsIdx = -1;
        String uuidsIndent = "";
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            if (line.trim().startsWith("uuids:")) {
                uuidsIdx = i;
                uuidsIndent = line.substring(0, line.indexOf("uuids:"));
                // forma inline (uuids: ["a", "b"]) -> converti in blocco
                lines.set(i, uuidsIndent + "uuids:");
                break;
            }
        }

        String itemIndent;
        if (uuidsIdx >= 0) {
            itemIndent = uuidsIndent + "  ";
            // rimuovi vecchie voci "- ..." (salta righe vuote/commenti, fermati alla prossima chiave)
            int j = uuidsIdx + 1;
            while (j < lines.size()) {
                String t = lines.get(j).trim();
                if (t.isEmpty() || t.startsWith("#")) { j++; continue; }
                if (t.startsWith("-")) { lines.remove(j); continue; }
                break;
            }
            List<String> items = new ArrayList<>();
            for (UUID u : adminUuids) items.add(itemIndent + "- \"" + u + "\"");
            lines.addAll(j, items);
        } else {
            int adminsIdx = -1;
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).trim().startsWith("admins:")) { adminsIdx = i; break; }
            }
            List<String> block = new ArrayList<>();
            if (adminsIdx >= 0) {
                String ind = lines.get(adminsIdx).substring(0, lines.get(adminsIdx).indexOf("admins:"));
                block.add(ind + "  uuids:");
                for (UUID u : adminUuids) block.add(ind + "    - \"" + u + "\"");
                lines.addAll(adminsIdx + 1, block);
            } else {
                block.add("admins:");
                block.add("  uuids:");
                for (UUID u : adminUuids) block.add("    - \"" + u + "\"");
                lines.addAll(block);
            }
        }

        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Files.write(file, lines);
    }

    /**
     * Riscrive l'intero file dai valori attuali (usato dalla GUI impostazioni).
     * Formato canonico identico a defaultYaml().
     */
    public void saveFull(Path file) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# AntiCheat config (Paper + Fabric condividono gli stessi nomi)\n");
        sb.append("# Modificabile a mano oppure dalla GUI con /ac (pannello Impostazioni)\n");
        sb.append("admins:\n  uuids:\n");
        if (adminUuids.isEmpty()) sb.append("    - \"00000000-0000-0000-0000-000000000000\"\n");
        for (UUID u : adminUuids) sb.append("    - \"").append(u).append("\"\n");
        sb.append("  use-permission-too: ").append(usePermissionToo).append("\n");
        sb.append("exempt:\n  uuids:");
        if (exemptUuids.isEmpty()) sb.append(" []\n");
        else {
            sb.append("\n");
            for (UUID u : exemptUuids) sb.append("    - \"").append(u).append("\"\n");
        }
        sb.append("announce-all: ").append(announceAll).append("\n");
        sb.append("punishments:\n");
        sb.append("  warn-vl: ").append(warnVl).append("\n");
        sb.append("  kick-vl: ").append(kickVl).append("\n");
        sb.append("  ban-vl: ").append(banVl).append("\n");
        sb.append("setback:\n");
        sb.append("  enabled: ").append(setbackEnabled).append("\n");
        sb.append("  min-vl: ").append(setbackMinVl).append("\n");
        sb.append("test-mode: ").append(testMode).append("\n");
        sb.append("packet-checks: ").append(packetChecks).append("\n");
        sb.append("dashboard:\n");
        sb.append("  enabled: ").append(dashEnabled).append("\n");
        sb.append("  port: ").append(dashPort).append("\n");
        sb.append("  bind: \"").append(dashBind).append("\"\n");
        sb.append("checks:\n");
        for (String name : ALL_CHECKS) sb.append("  ").append(name).append(": ").append(isCheckEnabled(name)).append("\n");
        sb.append("experimental-checks: ").append(experimentalChecks).append("\n");
        sb.append("tuning:\n");
        sb.append("  autoclicker-std: ").append(autoClickerStd).append("\n");
        sb.append("  cps-limit: ").append(cpsLimit).append("\n");
        sb.append("client-mod:\n");
        sb.append("  required: ").append(clientRequired).append("\n");
        sb.append("  min-version: \"").append(clientMinVersion).append("\"\n");
        sb.append("appeal-url: \"").append(appealUrl).append("\"\n");
        sb.append("debug:\n");
        sb.append("  verbose: ").append(verbose).append("\n");
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Files.writeString(file, sb.toString());
    }
}
