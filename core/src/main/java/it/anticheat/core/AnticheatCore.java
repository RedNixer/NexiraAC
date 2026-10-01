package it.anticheat.core;

import it.anticheat.core.checks.AutoClickerCheck;
import it.anticheat.core.checks.AutoTotemCheck;
import it.anticheat.core.checks.AimLockCheck;
import it.anticheat.core.checks.AimSnapCheck;
import it.anticheat.core.checks.FastBreakCheck;
import it.anticheat.core.checks.FlyCheck;
import it.anticheat.core.checks.GUIMoveCheck;
import it.anticheat.core.checks.JesusCheck;
import it.anticheat.core.checks.KillAuraCheck;
import it.anticheat.core.checks.MultitaskCheck;
import it.anticheat.core.checks.NoFallCheck;
import it.anticheat.core.checks.NoSlowCheck;
import it.anticheat.core.checks.InteractCheck;
import it.anticheat.core.checks.PacketOrderCheck;
import it.anticheat.core.checks.PredictionCheck;
import it.anticheat.core.checks.ReachCheck;
import it.anticheat.core.checks.RotationStreamCheck;
import it.anticheat.core.checks.ScaffoldCheck;
import it.anticheat.core.checks.SpeedCheck;
import it.anticheat.core.checks.SpiderCheck;
import it.anticheat.core.checks.SprintCheck;
import it.anticheat.core.checks.StepCheck;
import it.anticheat.core.checks.TimerCheck;
import it.anticheat.core.checks.XrayCheck;
import it.anticheat.core.config.AnticheatConfig;
import it.anticheat.core.config.PunishmentConfig;
import it.anticheat.core.model.Report;
import it.anticheat.core.model.Violation;
import it.anticheat.core.storage.MemoryStorage;
import it.anticheat.core.storage.Storage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Singleton core: zero dipendenze Minecraft.
 * Paper e Fabric lo usano entrambi.
 */
public class AnticheatCore {
    private static final AnticheatCore INSTANCE = new AnticheatCore();
    public static AnticheatCore get() { return INSTANCE; }

    public enum Decision { NONE, NOTIFY, WARN, KICK, TEMPBAN, BAN, FREEZE }

    public interface ActionHandler {
        void warn(UUID player, String check, int totalVl);
        void kick(UUID player, String reason);
        void ban(UUID player, String reason);
        void setback(UUID player);
        void notifyStaff(String message);
        /** Ban a tempo: expiresAtMs -1 = permanente. Default = ban permanente. */
        default void tempban(UUID player, String reason, long expiresAtMs) {
            ban(player, reason);
        }
        /** Freeze: true blocca, false sblocca. Default = niente. */
        default void freeze(UUID player, boolean on) {}
    }

    /** Chi e esente dai controlli (gli adapter lo collegano ai permessi). */
    public interface ExemptChecker {
        boolean isExempt(UUID uuid);
    }

    /** Check che possono triggerare il setback (riporto a terra). */
    private static final Set<String> SETBACK_CHECKS = Set.of(
        "Speed", "Fly", "Step", "NoFall", "Scaffold", "Prediction");

    private final List<Check> checks = new ArrayList<>();
    private final Map<UUID, PlayerData> players = new ConcurrentHashMap<>();
    private AnticheatConfig config = new AnticheatConfig();
    private PunishmentConfig punishments = new PunishmentConfig();
    private Storage storage = new MemoryStorage();
    private ActionHandler actions = new ActionHandler() {
        @Override public void warn(UUID p, String c, int v) {}
        @Override public void kick(UUID p, String r) {}
        @Override public void ban(UUID p, String r) {}
        @Override public void setback(UUID p) {}
        @Override public void notifyStaff(String m) {}
    };
    private ExemptChecker exemptChecker = uuid -> false;

    private final java.util.Set<UUID> debugTargets = ConcurrentHashMap.newKeySet();

    /** Diagnostica live su console per un player (/ac debug). */
    public void setDebug(UUID uuid, boolean on) {
        if (on) debugTargets.add(uuid);
        else debugTargets.remove(uuid);
    }

    public boolean isDebug(UUID uuid) {
        return debugTargets.contains(uuid);
    }

    public void setExemptChecker(ExemptChecker checker) {
        if (checker != null) this.exemptChecker = checker;
    }

    public boolean isExempt(UUID uuid) {
        try {
            return exemptChecker.isExempt(uuid);
        } catch (Throwable t) {
            return false;
        }
    }

    private final SpeedCheck speed = new SpeedCheck();
    private final FlyCheck fly = new FlyCheck();
    private final NoFallCheck nofall = new NoFallCheck();
    private final StepCheck step = new StepCheck();
    private final ScaffoldCheck scaffold = new ScaffoldCheck();
    private final ReachCheck reach = new ReachCheck();
    private final KillAuraCheck killaura = new KillAuraCheck();
    private final AutoClickerCheck autoclicker = new AutoClickerCheck();
    private final FastBreakCheck fastbreak = new FastBreakCheck();
    private final AutoTotemCheck autototem = new AutoTotemCheck();
    private final XrayCheck xray = new XrayCheck();
    private final SprintCheck sprint = new SprintCheck();
    private final NoSlowCheck noslow = new NoSlowCheck();
    private final TimerCheck timer = new TimerCheck();
    private final JesusCheck jesus = new JesusCheck();
    private final GUIMoveCheck guimove = new GUIMoveCheck();
    private final MultitaskCheck multitask = new MultitaskCheck();
    private final SpiderCheck spider = new SpiderCheck();
    private final AimSnapCheck aimsnap = new AimSnapCheck();
    private final AimLockCheck aimlock = new AimLockCheck();
    private final PredictionCheck prediction = new PredictionCheck();
    private final PacketOrderCheck packetOrder = new PacketOrderCheck();
    private final InteractCheck interact = new InteractCheck();
    private final RotationStreamCheck rotationStream = new RotationStreamCheck();

    private final Map<String, Check> byName = new ConcurrentHashMap<>();

    /** Contatore tick server (avanzato dagli adapter ogni tick). */
    private long currentTick = 0;

    /**
     * Se true, i fight arrivano dal bridge pacchetti (misura precisa) e gli
     * adapter devono saltare la versione a eventi per non contare doppio.
     */
    private volatile boolean packetFightPrimary = false;

    public void setPacketFightPrimary(boolean on) {
        this.packetFightPrimary = on;
    }

    public boolean isPacketFightPrimary() {
        return packetFightPrimary;
    }

    private AnticheatCore() {
        checks.add(speed);
        checks.add(fly);
        checks.add(nofall);
        checks.add(step);
        checks.add(scaffold);
        checks.add(reach);
        checks.add(killaura);
        checks.add(autoclicker);
        checks.add(fastbreak);
        checks.add(autototem);
        checks.add(xray);
        checks.add(sprint);
        checks.add(noslow);
        checks.add(timer);
        checks.add(jesus);
        checks.add(guimove);
        checks.add(multitask);
        checks.add(spider);
        checks.add(aimsnap);
        checks.add(aimlock);
        checks.add(prediction);
        checks.add(packetOrder);
        checks.add(interact);
        checks.add(rotationStream);
        for (Check c : checks) byName.put(c.name(), c);
    }

    public boolean isEnabled(String check) {
        Check c = byName.get(check);
        if (c != null && c.experimental() && !config.experimentalChecks) return false;
        return config.isCheckEnabled(check);
    }

    public void init(AnticheatConfig config, Storage storage, ActionHandler actions) {
        this.config = config;
        if (storage != null) this.storage = storage;
        if (actions != null) this.actions = actions;
    }

    public AnticheatConfig config() { return config; }
    public PunishmentConfig punishments() { return punishments; }
    public void setPunishments(PunishmentConfig p) {
        if (p != null) this.punishments = p;
    }
    public Storage storage() { return storage; }
    public List<Check> checks() { return checks; }

    public PlayerData data(UUID uuid) {
        return players.computeIfAbsent(uuid, PlayerData::new);
    }

    public void remove(UUID uuid) {
        players.remove(uuid);
    }

    public Map<UUID, PlayerData> allPlayers() { return players; }

    // ---- API usata dagli adapter ----

    public void handleMove(UUID uuid, String name, Check.MoveContext ctx, double x, double y, double z, String worldKey) {
        if (isExempt(uuid)) return;
        PlayerData d = data(uuid);
        d.name = name;
        long now = System.currentTimeMillis();
        // tracking SEMPRE aggiornato prima dei controlli: cosi dopo un teleport
        // o un'esenzione non si crea un delta gigante al movimento successivo
        boolean first = !d.hasLastPos;
        d.lastX = x; d.lastY = y; d.lastZ = z;
        d.hasLastPos = true;
        d.lastMoveTime = now;
        if (ctx.onGround) {
            d.lastGroundX = x; d.lastGroundY = y; d.lastGroundZ = z;
            d.lastGroundWorld = worldKey;
            d.hasGroundPos = true;
        }
        if (first) return;
        if (now < d.exemptUntil || now < d.moveExemptUntil) return; // knockback, teleport, veicoli, perle...
        // tracking rotazioni (snap) sempre attivo: serve ad AimSnap e bow-snap
        if (!d.yawInit) {
            d.lastMoveYaw = ctx.yaw;
            d.lastMovePitch = ctx.pitch;
            d.yawInit = true;
        } else {
            double ySnap = Check.yawDiff(d.lastMoveYaw, ctx.yaw);
            double pSnap = Math.abs(d.lastMovePitch - ctx.pitch);
            d.lastMoveYaw = ctx.yaw;
            d.lastMovePitch = ctx.pitch;
            if (ySnap > 35 || pSnap > 25) {
                d.lastSnapTime = now;
                d.snapTimes.addLast(now);
                while (d.snapTimes.size() > 12) d.snapTimes.pollFirst();
            }
        }
        int add = 0;
        StringBuilder details = new StringBuilder();
        if (isEnabled("Speed")) {
            int s = speed.checkMove(d, ctx);
            if (s > 0) { add += s; details.append("Speed+").append(s).append(" "); }
        }
        if (isEnabled("Fly")) {
            int f = fly.checkMove(d, ctx);
            if (f > 0) { add += f; details.append("Fly+").append(f).append(" "); }
        }
        if (isEnabled("NoFall")) {
            int nf = nofall.checkMove(d, ctx);
            if (nf > 0) { add += nf; details.append("NoFall+").append(nf).append(" "); }
        }
        if (isEnabled("Step")) {
            int st = step.checkMove(d, ctx);
            if (st > 0) { add += st; details.append("Step+").append(st).append(" "); }
        }
        if (isEnabled("Sprint")) {
            int sp = sprint.checkMove(d, ctx);
            if (sp > 0) { add += sp; details.append("Sprint+").append(sp).append(" "); }
        }
        if (isEnabled("NoSlow")) {
            int ns = noslow.checkMove(d, ctx);
            if (ns > 0) { add += ns; details.append("NoSlow+").append(ns).append(" "); }
        }
        if (isEnabled("Timer")) {
            int tm = timer.onMove(d, ctx.distXZ > 0.001);
            if (tm > 0) { add += tm; details.append("Timer+").append(tm).append(" "); }
        }
        if (isEnabled("Jesus")) {
            int js = jesus.checkMove(d, ctx);
            if (js > 0) { add += js; details.append("Jesus+").append(js).append(" "); }
        }
        if (isEnabled("GUIMove")) {
            int gm = guimove.checkMove(d, ctx);
            if (gm > 0) { add += gm; details.append("GUIMove+").append(gm).append(" "); }
        }
        if (isEnabled("Spider")) {
            int sd = spider.checkMove(d, ctx);
            if (sd > 0) { add += sd; details.append("Spider+").append(sd).append(" "); }
        }
        if (isEnabled("AimSnap")) {
            int as = aimsnap.checkMove(d, ctx);
            if (as > 0) { add += as; details.append("AimSnap+").append(as).append(" "); }
        }
        if (isEnabled("Prediction")) {
            int pr = prediction.checkMove(d, ctx);
            if (pr > 0) { add += pr; details.append("Prediction+").append(pr).append(" "); }
        }
        if (add > 0) flag(uuid, "Movement", add, details.toString().trim());
    }

    /** Stato inventario aperto (per GUIMove). */
    public void setInventoryOpen(UUID uuid, boolean open) {
        data(uuid).invOpen = open;
    }

    /** Resetta lo stato movimento (teleport, respawn, veicoli): niente delta giganti. */
    public void resetMoveState(UUID uuid, double x, double y, double z, String worldKey) {
        PlayerData d = data(uuid);
        d.lastX = x; d.lastY = y; d.lastZ = z;
        d.hasLastPos = true;
        d.lastMoveTime = System.currentTimeMillis();
        d.speedPendingDist = 0;
        d.speedPendingMs = 0;
        d.speedStreak = 0;
        d.airTicks = 0;
        d.airMs = 0;
        d.flyUpStreak = 0;
        d.pendingFallDist = 0;
        d.groundSpoofStreak = 0;
        d.predHStreak = 0;
        d.predVStreak = 0;
        d.predHoverStreak = 0;
        d.predFallRefTicks = 0;
        d.pktOrderStreak = 0;
        d.pktNoSwingStreak = 0;
        d.pktGroundStreak = 0;
        d.rotSnapStreak = 0;
        d.rotLockStreak = 0;
        d.rotDupStreak = 0;
        d.rotGcdStreak = 0;
        d.rotGcdWindows = 0;
        if (worldKey != null && !worldKey.equals(d.lastGroundWorld)) {
            d.hasGroundPos = false; // mondo diverso: la vecchia safe-pos non vale piu
        }
    }

    /** Esenta dai controlli movimento per ms (danni, knockback, perle, riptide...). */
    public void exemptMove(UUID uuid, long ms) {
        long until = System.currentTimeMillis() + ms;
        PlayerData d = data(uuid);
        d.exemptUntil = until;
        d.moveExemptUntil = until;
    }

    /** Esenta dai controlli combat per ms (respawn, cambio mondo...). */
    public void exemptFight(UUID uuid, long ms) {
        data(uuid).fightExemptUntil = System.currentTimeMillis() + ms;
    }

    /**
     * Knockback atteso (Fase 3): il vettore viene sottratto al movimento
     * osservato invece di spegnere i check. Finestra = 1500ms o ping*3.
     */
    public void noteKnockback(UUID uuid, double vx, double vy, double vz) {
        PlayerData d = data(uuid);
        d.kbVX = vx;
        d.kbVY = vy;
        d.kbVZ = vz;
        d.kbTime = System.currentTimeMillis();
    }

    /** Vettore knockback ancora valido (altrimenti 0). Chiamato dai check move. */
    public static double[] consumeKnockback(PlayerData d, int pingMs) {
        long window = Math.max(1500, (long) pingMs * 3);
        if (d.kbTime == 0 || System.currentTimeMillis() - d.kbTime > window) {
            return new double[]{0, 0, 0};
        }
        // decade linearmente: il knockback vanilla perde ~40%/tick in aria
        double age = (System.currentTimeMillis() - d.kbTime) / 50.0;
        double f = Math.pow(0.6, age);
        return new double[]{d.kbVX * f, d.kbVY * f, d.kbVZ * f};
    }

    /** Avviso diretto allo staff (per alert tipo XRay che non devono bannare da soli). */
    public void alertStaff(String message) {
        actions.notifyStaff(message);
    }

    public void handleFight(UUID uuid, String name, Check.FightContext ctx) {
        if (isExempt(uuid)) return;
        PlayerData d = data(uuid);
        if (System.currentTimeMillis() < d.fightExemptUntil) return;
        d.name = name;
        long now = System.currentTimeMillis();
        if (d.lastAttackTime == 0) ctx.dtSinceLastAttackMillis = -1;
        else if (ctx.dtSinceLastAttackMillis < 0) ctx.dtSinceLastAttackMillis = now - d.lastAttackTime;
        // snap yaw tra attacchi consecutivi (killaura con rotazioni silenziose)
        if (d.lastAttackTime == 0) ctx.yawSnapDiff = -1;
        else ctx.yawSnapDiff = Check.yawDiff(d.lastAttackYaw, ctx.attackerYaw);
        int add = 0;
        StringBuilder details = new StringBuilder();
        if (isEnabled("Reach")) {
            int r = reach.checkFight(d, ctx);
            if (r > 0) { add += r; details.append("Reach+").append(r).append(" dist=").append(String.format("%.2f", ctx.distance)).append(" "); }
        }
        if (isEnabled("KillAura")) {
            int k = killaura.checkFight(d, ctx);
            if (k > 0) { add += k; details.append("KillAura+").append(k).append(" "); }
        }
        if (isEnabled("Multitask")) {
            int mt = multitask.checkFight(d, ctx);
            if (mt > 0) { add += mt; details.append("Multitask+").append(mt).append(" "); }
        }
        if (isEnabled("AimLock")) {
            int al = aimlock.checkFight(d, ctx);
            if (al > 0) { add += al; details.append("AimLock+").append(al).append(" "); }
        }
        if (add > 0) flag(uuid, "Combat", add, details.toString().trim());
        d.lastAttackTime = now;
        d.lastAttackYaw = ctx.attackerYaw;
        d.lastAttackDistance = ctx.distance;
    }

    public void handleClick(UUID uuid, String name) {
        if (!isEnabled("AutoClicker") || isExempt(uuid)) return;
        PlayerData d = data(uuid);
        d.name = name;
        // La regola CPS-limite vale ovunque; quelle di regolarita solo in combat
        // (colpo negli ultimi 4s) cosi scavare/costruire non rischiano nulla.
        boolean inCombat = System.currentTimeMillis() - d.lastAttackTime < 4000;
        int a = autoclicker.onClick(d, config.autoClickerStd, config.cpsLimit, inCombat);
        if (a > 0) flag(uuid, "AutoClicker", a, "cps/regolarita sospetti");
    }

    public void handleBlockPlace(UUID uuid, String name, boolean onGround, float pitch, double x, double z, boolean sneaking, float yawDeg) {
        if (!isEnabled("Scaffold") || isExempt(uuid)) return;
        PlayerData d = data(uuid);
        d.name = name;
        int s = scaffold.onPlace(d, onGround, pitch, x, z, sneaking, yawDeg);
        if (s > 0) flag(uuid, "Scaffold", s, "bridging impossibile");
    }

    public void handlePlaceRotation(UUID uuid, String name, double angleDeg, double dist) {
        if (!isEnabled("Scaffold") || isExempt(uuid)) return;
        PlayerData d = data(uuid);
        d.name = name;
        int s = scaffold.onPlaceRotation(d, angleDeg, dist);
        if (s > 0) flag(uuid, "Scaffold", s,
            dist > 6.0 ? "piazzamento oltre gittata" : "piazza senza guardare");
    }

    /** Inizio danneggiamento blocco (per durata danno->rottura). Chiave "mondo:x:y:z". */
    public void handleBlockDamage(UUID uuid, String name, String key) {
        if (!isEnabled("FastBreak") || isExempt(uuid)) return;
        PlayerData d = data(uuid);
        d.name = name;
        fastbreak.onDamage(d, key);
        // swap-timing (AutoTool): cambio hotbar appena prima di scavare
        long heldAgo = d.lastHeldChange == 0 ? -1 : System.currentTimeMillis() - d.lastHeldChange;
        int s = fastbreak.onSwapDig(d, heldAgo);
        if (s > 0) flag(uuid, "FastBreak", s, "swap hotbar istantaneo");
    }

    /** Tick server (gli adapter lo chiamano ogni tick): serve al multibreak. */
    public void serverTick() {
        currentTick++;
    }

    /** Pacchetto Flying ricevuto (ProtocolLib): timer preciso sui pacchetti reali. */
    public void handleFlyingPacket(UUID uuid) {
        if (isExempt(uuid)) return;
        PlayerData d = data(uuid);
        if (isEnabled("PacketOrder")) packetOrder.onFlying(d);
        if (!isEnabled("Timer")) return;
        int t = timer.onPacket(d);
        if (t > 0) flag(uuid, "Timer", t, "flying oltre 25/s");
    }

    /** Swing pacchetto (ARM_ANIMATION): alimenta il no-swing P2. */
    public void notePacketSwing(UUID uuid) {
        if (isExempt(uuid)) return;
        if (!isEnabled("PacketOrder")) return;
        packetOrder.onSwing(data(uuid));
    }

    /** ATTACK via pacchetto: ordine + swing (P2). Chiamare prima di handleFight. */
    public void handlePacketAttack(UUID uuid, String name) {
        if (!isEnabled("PacketOrder") || isExempt(uuid)) return;
        PlayerData d = data(uuid);
        d.name = name;
        int o = packetOrder.onPacketAttack(d);
        if (o > 0) flag(uuid, "PacketOrder", o, "colpo fuori sequenza/senza swing");
    }

    /** ATTACK via pacchetto: uso-item + self-hit (Passo 3 C1/F1). */
    public void handleInteractAttack(UUID uuid, String name, boolean usingItem, boolean selfHit, int entityId) {
        if (!isEnabled("Interact") || isExempt(uuid)) return;
        PlayerData d = data(uuid);
        d.name = name;
        int a = interact.onAttack(d, usingItem, selfHit);
        if (a > 0) flag(uuid, "Interact", a, selfHit ? "colpo contro se stesso" : "colpo mentre usa item");
        int m = interact.onInteract(d, entityId);
        if (m > 0) flag(uuid, "Interact", m, "doppia entita stesso tick");
    }

    /** USE non-attacco via pacchetto: range interazioni (Passo 3 F2). */
    public void handleInteractUse(UUID uuid, String name, int entityId, double dist) {
        if (!isEnabled("Interact") || isExempt(uuid)) return;
        PlayerData d = data(uuid);
        d.name = name;
        int m = interact.onInteract(d, entityId);
        if (m > 0) flag(uuid, "Interact", m, "doppia entita stesso tick");
        int r = interact.onUseRange(d, dist);
        if (r > 0) flag(uuid, "Interact", r, "interazione oltre gittata");
    }

    /** Flag ground del pacchetto Flying + dy reale: NoFall packet (P2). */
    public void handlePacketGround(UUID uuid, String name, boolean packetGround, double dy) {
        if (!isEnabled("PacketOrder") || isExempt(uuid)) return;
        PlayerData d = data(uuid);
        d.name = name;
        int g = packetOrder.onPacketGround(d, packetGround, dy);
        if (g > 0) flag(uuid, "PacketOrder", g, "ground-spoof da pacchetto");
    }

    /** Rotazione raw dal pacchetto LOOK (P3). distXZ = movimento orizzontale stesso tick. */
    public void handlePacketRotation(UUID uuid, String name, float yaw, float pitch, double distXZ) {
        if (!isEnabled("RotationStream") || isExempt(uuid)) return;
        PlayerData d = data(uuid);
        d.name = name;
        int r = rotationStream.onRotation(d, yaw, pitch, distXZ);
        if (r > 0) flag(uuid, "RotationStream", r, "rotazione raw impossibile");
    }

    public void noteHeldChange(UUID uuid) {
        data(uuid).lastHeldChange = System.currentTimeMillis();
    }

    public void noteInventoryClick(UUID uuid) {
        data(uuid).lastInvClick = System.currentTimeMillis();
    }

    public void handleBlockBreak(UUID uuid, String name, boolean hardBlock, String key) {
        if (!isEnabled("FastBreak") || isExempt(uuid)) return;
        // blocchi insta-break (torce, erba, fiori): legittimi anche velocissimi,
        // non toccano lo streak (e il dt si allunga da solo, resettandolo)
        if (!hardBlock) return;
        PlayerData d = data(uuid);
        d.name = name;
        int f = fastbreak.onBreak(d, key);
        if (f > 0) flag(uuid, "FastBreak", f, "rotture troppo veloci");
        // multibreak (Nuker): 2+ rotture dure nello stesso tick, x3 tick
        if (d.lastBreakTick == currentTick) {
            d.breakTickCount++;
            if (d.breakTickCount == 2) {
                d.multibreakStreak++;
                if (d.multibreakStreak >= 3) {
                    d.multibreakStreak = 0;
                    flag(uuid, "FastBreak", 5, "rotture multiple stesso tick");
                }
            }
        } else {
            if (d.breakTickCount < 2) d.multibreakStreak = 0;
            d.lastBreakTick = currentTick;
            d.breakTickCount = 1;
        }
    }

    /**
     * Mine-timing (Paper): blocco duro rotto in fretta senza Haste.
     * Chiamare PRIMA di handleBlockBreak (legge la mappa danni intatta).
     */
    public void handleMineTiming(UUID uuid, String name, float hardness, String key, boolean hasHaste) {
        if (!isEnabled("FastBreak") || isExempt(uuid)) return;
        PlayerData d = data(uuid);
        d.name = name;
        Long start = key == null ? null : d.breakDamage.get(key);
        long dt = start == null ? -1 : System.currentTimeMillis() - start;
        int m = fastbreak.onMineTiming(d, hardness, dt, hasHaste);
        if (m > 0) flag(uuid, "FastBreak", m, "duro in " + dt + "ms senza haste");
    }

    /**
     * DPS mining Fase 4 (Paper): dt danno->rottura vs tempo minimo vanilla
     * per attrezzo/incanti/effetti. Chiamare PRIMA di handleBlockBreak.
     */
    public void handleMineDps(UUID uuid, String name, float hardness, String key,
            String tool, int effLvl, int hasteAmp, int fatigueAmp,
            boolean inWater, boolean onGround) {
        if (!isEnabled("FastBreak") || isExempt(uuid)) return;
        PlayerData d = data(uuid);
        d.name = name;
        Long start = key == null ? null : d.breakDamage.get(key);
        long dt = start == null ? -1 : System.currentTimeMillis() - start;
        int v = fastbreak.onDps(d, hardness, dt, tool, effLvl,
            hasteAmp, fatigueAmp, inWater, onGround);
        if (v > 0) flag(uuid, "FastBreak", v, "scavo oltre DPS vanilla (" + dt + "ms)");
    }

    public void handleTotemPop(UUID uuid, String name) {
        if (!isEnabled("AutoTotem") || isExempt(uuid)) return;
        PlayerData d = data(uuid);
        d.name = name;
        // swap-timing: totem poppato subito dopo un click inventario (macro)
        long now = System.currentTimeMillis();
        if (d.lastInvClick != 0 && now - d.lastInvClick < 150) {
            d.totemSwapStreak++;
            if (d.totemSwapStreak >= 3) {
                d.totemSwapStreak = 0;
                flag(uuid, "AutoTotem", 4, "swap+pop istantanei");
                return;
            }
        } else {
            d.totemSwapStreak = 0;
        }
        int t = autototem.onPop(d);
        if (t > 0) flag(uuid, "AutoTotem", t, "totem troppo frequenti");
    }

    /** Refill post-pop (dallo scheduler 6 tick dopo il pop). */
    public void handleTotemRefill(UUID uuid, String name, boolean hadTotemAtPop, boolean hasTotemNow) {
        if (!isEnabled("AutoTotem") || isExempt(uuid)) return;
        PlayerData d = data(uuid);
        d.name = name;
        int r = autototem.onRefill(d, hadTotemAtPop, hasTotemNow);
        if (r > 0) flag(uuid, "AutoTotem", r, "refill totem istantaneo");
    }

    /** Scocco freccia (per bow-snap: tiro subito dopo uno snap di mira). */
    public void handleBowShoot(UUID uuid, String name) {
        if (!isEnabled("AimLock") || isExempt(uuid)) return;
        PlayerData d = data(uuid);
        d.name = name;
        if (System.currentTimeMillis() - d.lastSnapTime < 300) {
            d.bowStreak++;
            if (d.bowStreak >= 4) {
                d.bowStreak = 0;
                flag(uuid, "AimLock", 4, "tiro dopo snap di mira");
            }
        } else {
            d.bowStreak = 0;
        }
    }

    public void handleXrayBreak(UUID uuid, String name, boolean valuable, boolean stone) {
        if (!isEnabled("XRay") || isExempt(uuid)) return;
        PlayerData d = data(uuid);
        d.name = name;
        xray.configure(config.xrayMinStone, config.xrayMinOres, config.xrayRatio, config.xrayCooldownMin);
        String alert = xray.onBreak(d, valuable, stone);
        if (alert != null) {
            flag(uuid, "XRay", 2, alert);
            alertStaff("§c[XRay?] §f" + name + " §7" + alert + " §8(controlla con /ac vl " + name + ")");
        }
    }

    public void handleFallDamage(UUID uuid, String name, double fallDistance, double damageTaken) {
        if (!isEnabled("NoFall") || isExempt(uuid)) return;
        int n = nofall.checkFallDamage(data(uuid), fallDistance, damageTaken);
        if (n > 0) flag(uuid, "NoFall", n, "caduta " + String.format("%.1f", fallDistance) + " senza danno");
    }

    /** Flag centrale: somma VL, salva, decide punizione, notifica staff. Ritorna decisione. */
    public Decision flag(UUID uuid, String check, int vlAdd, String details) {
        if (isExempt(uuid)) return Decision.NONE;
        PlayerData d = data(uuid);
        int total = d.violations.merge(check, vlAdd, Integer::sum);
        int all = d.totalVl();
        storage.saveViolation(new Violation(uuid, d.name, check, vlAdd, all, details));
        if (config.testMode) {
            // test-mode: registra tutto ma non punire mai (ne setback)
            actions.notifyStaff("§7[TEST] §f" + d.name + " §7" + check + " +"
                + vlAdd + " (tot " + all + ") " + details);
            return Decision.NONE;
        }
        Decision dec = Decision.NONE;
        String reason = "AntiCheat: cheat rilevato (" + check + " VL " + all + ")";
        long expiresAt = -1;
        PunishmentConfig.Rule rule = punishments.match(check, all,
            config.warnVl, config.kickVl, config.banVl);
        if (rule != null) {
            if (!rule.reason.isEmpty()) reason = rule.reason + " (VL " + all + ")";
            else if (rule.action.equals("warn")) reason = "AntiCheat: sospetto cheat (" + check + ")";
            else if (rule.action.equals("kick")) reason = "AntiCheat: sospetto cheat (" + check + ")";
            switch (rule.action) {
                case "notify" -> dec = Decision.NOTIFY;
                case "warn" -> dec = Decision.WARN;
                case "kick" -> dec = Decision.KICK;
                case "tempban" -> {
                    dec = Decision.TEMPBAN;
                    long dur = PunishmentConfig.parseDurationMs(rule.duration);
                    expiresAt = dur < 0 ? -1 : System.currentTimeMillis() + dur;
                }
                case "ban" -> dec = Decision.BAN;
                case "freeze" -> dec = Decision.FREEZE;
                default -> dec = Decision.WARN;
            }
        }
        if (config.verbose || vlAdd >= 4) {
            actions.notifyStaff("§c[AC] §f" + d.name + " §7" + check + " +"
                + vlAdd + " (tot " + all + ") " + details);
        }
        switch (dec) {
            case NOTIFY -> {}
            case WARN -> actions.warn(uuid, check, all);
            case KICK -> actions.kick(uuid, reason);
            case TEMPBAN -> actions.tempban(uuid, reason, expiresAt);
            case BAN -> actions.ban(uuid, reason);
            case FREEZE -> actions.freeze(uuid, true);
            default -> {}
        }
        // setback: riporta a terra ai flag movimento sopra soglia (max 1 ogni 2s)
        long now = System.currentTimeMillis();
        if (config.setbackEnabled && all >= config.setbackMinVl
                && SETBACK_CHECKS.contains(check) && now - d.lastSetbackTime > 2000) {
            d.lastSetbackTime = now;
            try {
                actions.setback(uuid);
            } catch (Throwable ignored) {}
        }
        return dec;
    }

    public Report report(UUID reporter, String reporterName, UUID reported, String reportedName, String reason) {
        Report r = new Report(reporter, reporterName, reported, reportedName, reason);
        storage.saveReport(r);
        actions.notifyStaff("§e[Report] §f" + reporterName + " -> " + reportedName + ": " + reason);
        return r;
    }

    public void resetVl(UUID uuid) {
        PlayerData d = data(uuid);
        d.violations.clear();
    }
}
