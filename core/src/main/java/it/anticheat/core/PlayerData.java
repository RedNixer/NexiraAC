package it.anticheat.core;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.Deque;

/** Dati runtime per giocatore. Tenuto in memoria dagli adapter (Paper/Fabric). */
public class PlayerData {
    public final UUID uuid;
    public volatile String name = "?";
    public volatile int ping = 0;
    public volatile ClientStatus clientStatus = ClientStatus.MISSING;
    public volatile String clientVersion = "-";
    public volatile long joinTime = System.currentTimeMillis();

    // movimento
    public volatile double lastX, lastY, lastZ;
    public volatile boolean hasLastPos = false;
    public volatile long lastMoveTime = 0;
    public volatile int airTicks = 0;
    public volatile double fallStartY = 0;
    public volatile boolean wasOnGround = true;

    // combat
    public final Deque<Long> clickTimes = new ConcurrentLinkedDeque<>();
    public volatile long lastAttackTime = 0;
    public volatile double lastAttackDistance = 0;
    public volatile int fastAttackStreak = 0;
    public volatile int reachWindow = 0; // ultimi 6 colpi: bit=1 se oltre limite
    public volatile int multitaskStreak = 0;

    // world / extra
    public volatile long lastPlaceTime = 0;
    public volatile double lastPlaceX = 0;
    public volatile double lastPlaceZ = 0;
    public volatile int scaffoldStreak = 0;
    public volatile long lastBreakTime = 0;
    public volatile int fastBreakStreak = 0;
    public final Deque<Long> totemPops = new ConcurrentLinkedDeque<>();

    // speed: valutazione a finestre (anti-packet-split) + streak
    public volatile double speedPendingDist = 0;
    public volatile long speedPendingMs = 0;
    public volatile int speedStreak = 0;

    // speed finestre 250ms per metronomo + hop rhythm
    public volatile double speedWinDist = 0;
    public volatile long speedWinMs = 0;
    public volatile long speedWinStart = 0;
    public final Deque<Double> speedBuckets = new ConcurrentLinkedDeque<>();
    public volatile boolean speedWasGround = true;
    public final Deque<Long> hopTimes = new ConcurrentLinkedDeque<>();

    // timer: conteggio position-packet/sec
    public volatile int moveWinCount = 0;
    public volatile long moveWinStart = 0;
    public volatile int timerStreak = 0;

    // timer via pacchetti (ProtocolLib): contatori separati dagli eventi
    public volatile int pktWinCount = 0;
    public volatile long pktWinStart = 0;
    public volatile int pktStreak = 0;

    // guimove / sprint / noslow streak
    public volatile boolean invOpen = false;
    public volatile int guiMoveStreak = 0;
    public volatile int sprintStreak = 0;
    public volatile int noSlowStreak = 0;
    public volatile int jesusStreak = 0;
    public volatile int spiderStreak = 0;

    // esenzione temporanea controlli movimento (knockback, teleport, veicoli, perle...)
    public volatile long exemptUntil = 0;

    // ultima posizione sicura a terra (per il setback)
    public volatile double lastGroundX, lastGroundY, lastGroundZ;
    public volatile String lastGroundWorld = "";
    public volatile boolean hasGroundPos = false;
    public volatile long lastSetbackTime = 0;

    // nofall v2
    public volatile double pendingFallDist = 0;
    public volatile long pendingFallTime = 0;
    public volatile long lastFallDamageTime = 0;
    public volatile int groundSpoofStreak = 0;

    // autoclicker cps
    public volatile int clickCpsStreak = 0;
    public volatile long lastCpsFlag = 0;

    // xray statistico
    public volatile int xrayStone = 0;
    public volatile int xrayOres = 0;
    public volatile long xrayLastAlert = 0;

    // scaffold yaw-snap
    public volatile double lastPlaceYaw = 0;
    public volatile int placeYawStreak = 0;

    // killaura yaw-snap
    public volatile double lastAttackYaw = 0;
    public volatile int auraSnapStreak = 0;

    // fastbreak no-swing
    public volatile int noSwingStreak = 0;

    // scaffold rotation/far-place
    public volatile int farPlaceStreak = 0;
    public volatile int rotPlaceStreak = 0;

    // nofall ground-support
    public volatile int noGroundStreak = 0;

    // fastbreak durata danno->rottura (per-blocco)
    public final ConcurrentHashMap<String, Long> breakDamage = new ConcurrentHashMap<>();
    public volatile int fastBreakDurStreak = 0;

    // B3: swap-timing, mine-timing, multibreak, totem-timing
    public volatile long lastHeldChange = 0;
    public volatile int swapStreak = 0;
    public volatile int mineStreak = 0;
    public volatile long lastBreakTick = -1;
    public volatile int breakTickCount = 0;
    public volatile int multibreakStreak = 0;
    public volatile long lastInvClick = 0;
    public volatile int totemSwapStreak = 0;

    // violazioni: check -> VL
    public final ConcurrentHashMap<String, Integer> violations = new ConcurrentHashMap<>();

    // aim: tracking rotazioni e ultimi colpi
    public volatile double lastMoveYaw = 0;
    public volatile double lastMovePitch = 0;
    public volatile boolean yawInit = false;
    public final Deque<Long> snapTimes = new ConcurrentLinkedDeque<>();
    public volatile long lastSnapTime = 0;
    public volatile double lastHitX = 0;
    public volatile double lastHitY = 0;
    public volatile double lastHitZ = 0;
    public volatile double lastHitYaw = 0;
    public volatile double lastHitPitch = 0;
    public volatile boolean hitInit = false;
    public volatile int lockStreak = 0;
    public volatile int bowStreak = 0;

    public PlayerData(UUID uuid) {
        this.uuid = uuid;
    }

    public int totalVl() {
        int t = 0;
        for (int v : violations.values()) t += v;
        return t;
    }

    public void decay(int amount) {
        violations.replaceAll((k, v) -> Math.max(0, v - amount));
    }
}
