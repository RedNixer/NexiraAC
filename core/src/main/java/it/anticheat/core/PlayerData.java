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
    public volatile long airMs = 0;
    public volatile double fallStartY = 0;
    public volatile boolean wasOnGround = true;

    // combat
    public final Deque<Long> clickTimes = new ConcurrentLinkedDeque<>();
    public volatile long lastAttackTime = 0;
    public volatile double lastAttackDistance = 0;
    public volatile int fastAttackStreak = 0;
    public volatile int cooldownStreak = 0;
    public volatile int reachWindow = 0; // ultimi 8 colpi: bit=1 se oltre limite
    public volatile int wallHitStreak = 0;
    // Passo 3 interact: uso+colpo, multi-entita, range
    public volatile int useAttackStreak = 0;
    public volatile int lastInteractId = -1;
    public volatile long lastInteractTime = 0;
    public volatile int multiInteractStreak = 0;
    public volatile int useRangeStreak = 0;
    public volatile int multitaskStreak = 0;

    // world / extra
    public volatile long lastPlaceTime = 0;
    public volatile double lastPlaceX = 0;
    public volatile double lastPlaceZ = 0;
    public volatile int scaffoldStreak = 0;
    public volatile long lastBreakTime = 0;
    public volatile int fastBreakStreak = 0;
    public final Deque<Long> totemPops = new ConcurrentLinkedDeque<>();
    public volatile int totemRefillStreak = 0;
    /** Freeze staff: movimento bloccato (punizione, non check). */
    public volatile boolean frozen = false;

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
    public volatile int stepStreak = 0;
    public volatile int noSlowStreak = 0;
    public volatile int jesusStreak = 0;
    public volatile int spiderStreak = 0;

    // esenzione temporanea controlli movimento (knockback, teleport, veicoli, perle...)
    public volatile long exemptUntil = 0;
    // Fase 3: exempt tipizzati (move vs fight separati) + knockback atteso
    public volatile long moveExemptUntil = 0;
    public volatile long fightExemptUntil = 0;
    public volatile double kbVX = 0;
    public volatile double kbVY = 0;
    public volatile double kbVZ = 0;
    public volatile long kbTime = 0;

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
    public volatile long lastCobwebTime = 0; // ultima volta in ragnatela/neve (attutisce)
    public volatile String lastNoFallBranch = "-"; // debug: quale branch ha flaggato

    // fly: salita sostenuta (streak, non singolo picco)
    public volatile int flyUpStreak = 0;

    // autoclicker cps
    public volatile int clickCpsStreak = 0;
    public volatile long lastCpsFlag = 0;

    // scaffold yaw-snap
    public volatile double lastPlaceYaw = 0;
    public volatile int placeYawStreak = 0;

    // killaura yaw-snap
    public volatile double lastAttackYaw = 0;
    public volatile int auraSnapStreak = 0;
    // killaura anti-smooth (Meteor fluido): regolarita dt, cooldown perfetto, switch target
    public final Deque<Long> hitDts = new ConcurrentLinkedDeque<>();
    public volatile int perfectCdStreak = 0;
    public volatile int lastTargetId = -1;
    public volatile int targetSwitchStreak = 0;

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
    public volatile int dpsStreak = 0;
    public volatile long lastBreakTick = -1;
    public volatile int breakTickCount = 0;
    public volatile int multibreakStreak = 0;
    public volatile long lastInvClick = 0;
    public volatile int totemSwapStreak = 0;

    // prediction Fase 1: streak offset + riferimento caduta
    public volatile int predHStreak = 0;
    public volatile double predPendingDist = 0;
    public volatile long predPendingMs = 0;
    public volatile double lastPredWinSpeed = 0; // ultima finestra H giudicata
    public volatile double lastPredWinMax = 0;
    public volatile int predVStreak = 0;
    public volatile int predHoverStreak = 0;
    public volatile double predFallRefY = 0;
    public volatile int predFallRefTicks = 0;

    // Fase 2 P2 (packet order): timestamp ultimo flying/swing pacchetto
    public volatile long pktLastFlying = 0;
    public volatile long pktLastSwing = 0;
    public volatile long lastPacketAttack = 0; // ultimo ATTACK via PL (fallback eventi se morto)
    public volatile int pktOrderStreak = 0;
    public volatile int pktNoSwingStreak = 0;
    public volatile int pktGroundStreak = 0;

    // Fase 2 P3 (rotation stream raw dai LOOK)
    public volatile boolean rotInit = false;
    public volatile float rotLastYaw = 0;
    public volatile float rotLastPitch = 0;
    public volatile int rotSnapStreak = 0;
    public volatile int rotModStreak = 0;
    public volatile int rotLockStreak = 0;
    public volatile int rotDupStreak = 0;
    public volatile int rotGcdStreak = 0;
    public volatile int rotGcdWindows = 0;
    public volatile long rotExemptUntil = 0;
    public volatile long flyStateTime = 0; // ultima vista in creativa/volo (per i bridge pacchetti)
    /** Cache blocchi per TickEngine/solver (riempita dagli adapter quando serve). */
    public final it.anticheat.core.physics.BlockCache blockCache =
        new it.anticheat.core.physics.BlockCache();

    // violazioni: check -> VL
    public final ConcurrentHashMap<String, Integer> violations = new ConcurrentHashMap<>();

    // aim: tracking rotazioni e ultimi colpi
    public volatile double lastMoveYaw = 0;
    public volatile double lastMovePitch = 0;
    public volatile double lastMoveDistXZ = 0;
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
