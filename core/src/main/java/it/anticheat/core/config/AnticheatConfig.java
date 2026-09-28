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
        "Reach", "KillAura", "AutoClicker", "FastBreak", "AutoTotem", "XRay",
        "Sprint", "NoSlow", "Timer", "Jesus", "GUIMove",         "Multitask", "Spider",
        "AimSnap", "AimLock", "Prediction", "PacketOrder", "RotationStream", "Interact");

    public List<UUID> adminUuids = new ArrayList<>();
    public boolean usePermissionToo = true;
    public int warnVl = 20;
    public int kickVl = 50;
    public int banVl = 100;
    public boolean clientRequired = false;
    public String clientMinVersion = "0.1.0";
    public boolean verbose = false;
    /** Check disattivati = false. Assenti = attivi (default true). */
    public java.util.Map<String, Boolean> checkEnabled = new java.util.HashMap<>();
    /** AutoClicker: deviazione std max per flaggare. Piu basso = piu severo. */
    public double autoClickerStd = 4.0;
    /** Limite CPS: sopra questa soglia (sostenuta) scatta il flag, ovunque. Ummano max ~25. */
    public int cpsLimit = 25;
    /** XRay statistico: soglie (piu alte = piu tollerante). */
    public int xrayMinStone = 500;
    public int xrayMinOres = 10;
    public double xrayRatio = 0.03;
    public int xrayCooldownMin = 30;
    /** Setback: riporta a terra ai flag movimento sopra min-vl. */
    public boolean setbackEnabled = true;
    public int setbackMinVl = 15;
    /** Test-mode: logga e avvisa ma non applica mai warn/kick/ban/setback. */
    public boolean testMode = false;
    /** Check sperimentali (Multitask, Spider): girano solo se true. */
    public boolean experimentalChecks = false;
    /** Check pacchetto (ProtocolLib su Paper): master switch, auto-off senza PL. */
    public boolean packetChecks = true;

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
            + "punishments:\n"
            + "  warn-vl: 20\n"
            + "  kick-vl: 50\n"
            + "  ban-vl: 100\n"
            + "setback: # riporta a terra ai flag movimento (Speed/Fly/Step/NoFall/Scaffold)\n"
            + "  enabled: true\n"
            + "  min-vl: 15\n"
            + "test-mode: false # se true: logga tutto ma non kicka/banna e niente setback\n"
            + "packet-checks: true # solo Paper + ProtocolLib: timer preciso + range preciso\n"
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
            + "  XRay: true # statistico: avvisa lo staff, non banna da solo\n"
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
            + "xray:\n"
            + "  min-stone: 500 # pietra minima scavata prima di giudicare\n"
            + "  min-ores: 10 # diamanti minimi prima di giudicare\n"
            + "  ratio: 0.03 # rapporto diamanti/pietra sospetto (0.03 = 3%)\n"
            + "  cooldown-min: 30 # minuti tra un alert e l'altro (stesso player)\n"
            + "client-mod:\n"
            + "  required: false # se true, chi non ha la mod viene kickato (sconsigliato)\n"
            + "  min-version: \"0.1.0\"\n"
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
        boolean inChecks = false;
        for (String raw : Files.readAllLines(file)) {
            String line = raw.trim();
            if (line.startsWith("#") || line.isEmpty()) continue;
            if (line.equals("checks:")) { inChecks = true; inUuids = false; continue; }
            if (!raw.isEmpty() && raw.charAt(0) != ' ' && line.contains(":") && !line.startsWith("-")) {
                inChecks = false; // nuova sezione top-level
            }
            if (line.startsWith("uuids:")) {
                String rest = line.substring("uuids:".length()).trim();
                if (rest.startsWith("[")) {
                    // formato inline: uuids: ["uuid1", "uuid2"]
                    for (String part : rest.replaceAll("[\\[\\]]", "").split(",")) {
                        try { c.adminUuids.add(UUID.fromString(part.trim().replace("\"", "").replace("'", ""))); }
                        catch (IllegalArgumentException ignored) {}
                    }
                    inUuids = false;
                } else {
                    inUuids = true;
                }
                continue;
            }
            if (inUuids && line.startsWith("-")) {
                String id = line.substring(1).trim().replace("\"", "").replace("'", "");
                try { c.adminUuids.add(UUID.fromString(id)); } catch (IllegalArgumentException ignored) {}
                continue;
            }
            if (inUuids && !line.startsWith("-") && line.contains(":")) inUuids = false;
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
            else if (line.startsWith("min-stone:")) c.xrayMinStone = parseInt(line, c.xrayMinStone);
            else if (line.startsWith("min-ores:")) c.xrayMinOres = parseInt(line, c.xrayMinOres);
            else if (line.startsWith("ratio:")) c.xrayRatio = parseDouble(line, c.xrayRatio);
            else if (line.startsWith("cooldown-min:")) c.xrayCooldownMin = parseInt(line, c.xrayCooldownMin);
            else if (line.startsWith("enabled:")) c.setbackEnabled = !line.contains("false");
            else if (line.startsWith("min-vl:")) c.setbackMinVl = parseInt(line, c.setbackMinVl);
            else if (line.startsWith("test-mode:")) c.testMode = line.contains("true");
            else if (line.startsWith("packet-checks:")) c.packetChecks = !line.contains("false");
            else if (line.startsWith("experimental-checks:")) c.experimentalChecks = line.contains("true");
            else if (line.startsWith("required:")) c.clientRequired = line.contains("true");
            else if (line.startsWith("min-version:")) c.clientMinVersion = line.split(":", 2)[1].trim().replace("\"", "").replace("'", "");
            else if (line.startsWith("verbose:")) c.verbose = line.contains("true");
            else if (line.startsWith("use-permission-too:")) c.usePermissionToo = !line.contains("false");
        }
        return c;
    }

    private static int parseInt(String line, int def) {
        try { return Integer.parseInt(line.split(":", 2)[1].trim()); }
        catch (Exception e) { return def; }
    }

    private static double parseDouble(String line, double def) {
        try { return Double.parseDouble(line.split(":", 2)[1].trim()); }
        catch (Exception e) { return def; }
    }

    /** Preset XRay: 0 tollerante, 1 normale, 2 severo. */
    public void applyXrayPreset(int level) {
        if (level == 0) { xrayMinStone = 800; xrayMinOres = 14; xrayRatio = 0.05; xrayCooldownMin = 60; }
        else if (level == 2) { xrayMinStone = 300; xrayMinOres = 8; xrayRatio = 0.02; xrayCooldownMin = 15; }
        else { xrayMinStone = 500; xrayMinOres = 10; xrayRatio = 0.03; xrayCooldownMin = 30; }
    }

    public String xrayPresetName() {
        if (xrayMinStone == 800 && xrayMinOres == 14 && xrayRatio == 0.05 && xrayCooldownMin == 60) return "Tollerante";
        if (xrayMinStone == 300 && xrayMinOres == 8 && xrayRatio == 0.02 && xrayCooldownMin == 15) return "Severo";
        if (xrayMinStone == 500 && xrayMinOres == 10 && xrayRatio == 0.03 && xrayCooldownMin == 30) return "Normale";
        return "Personalizzato";
    }

    public void cycleXrayPreset() {
        String n = xrayPresetName();
        if (n.equals("Tollerante")) applyXrayPreset(1);
        else if (n.equals("Normale")) applyXrayPreset(2);
        else applyXrayPreset(0);
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
        sb.append("punishments:\n");
        sb.append("  warn-vl: ").append(warnVl).append("\n");
        sb.append("  kick-vl: ").append(kickVl).append("\n");
        sb.append("  ban-vl: ").append(banVl).append("\n");
        sb.append("setback:\n");
        sb.append("  enabled: ").append(setbackEnabled).append("\n");
        sb.append("  min-vl: ").append(setbackMinVl).append("\n");
        sb.append("test-mode: ").append(testMode).append("\n");
        sb.append("packet-checks: ").append(packetChecks).append("\n");
        sb.append("checks:\n");
        for (String name : ALL_CHECKS) sb.append("  ").append(name).append(": ").append(isCheckEnabled(name)).append("\n");
        sb.append("experimental-checks: ").append(experimentalChecks).append("\n");
        sb.append("tuning:\n");
        sb.append("  autoclicker-std: ").append(autoClickerStd).append("\n");
        sb.append("  cps-limit: ").append(cpsLimit).append("\n");
        sb.append("xray:\n");
        sb.append("  min-stone: ").append(xrayMinStone).append("\n");
        sb.append("  min-ores: ").append(xrayMinOres).append("\n");
        sb.append("  ratio: ").append(xrayRatio).append("\n");
        sb.append("  cooldown-min: ").append(xrayCooldownMin).append("\n");
        sb.append("client-mod:\n");
        sb.append("  required: ").append(clientRequired).append("\n");
        sb.append("  min-version: \"").append(clientMinVersion).append("\"\n");
        sb.append("debug:\n");
        sb.append("  verbose: ").append(verbose).append("\n");
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        Files.writeString(file, sb.toString());
    }
}
