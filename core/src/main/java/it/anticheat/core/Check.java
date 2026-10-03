package it.anticheat.core;

/** Base class per tutti i check. Ritorna vlAdd (0 = pass). */
public abstract class Check {
    public abstract String name();
    public abstract CheckType type();
    public abstract String description();

    /** Contesto movimento. Gli adapter riempiono solo i campi che servono. */
    public static class MoveContext {
        public double dx, dy, dz;
        public double distXZ;
        public double y; // quota assoluta attuale (serve al NoFall)
        public double yaw; // yaw attuale (serve all'AimSnap)
        public double pitch; // pitch attuale (serve all'AimSnap)
        public boolean groundBelow = true; // blocco solido sotto i piedi (default sicuro)
        public boolean sprinting = false;
        public boolean blocking = false; // scudo alzato
        public boolean usingItem = false; // arco/cibo in uso (solo Fabric per ora)
        public boolean hungry = false; // fame <= 6
        public boolean blind = false; // cecita (solo Paper per ora)
        public boolean liquidFeet = false; // piedi nel liquido
        public boolean liquidBelow = false; // liquido sotto i piedi
        public boolean swimming = false;
        public boolean inCobweb = false; // ragnatela/neve (solo Paper per ora)
        public boolean soulSand = false; // piedi su soul sand (solo Paper per ora)
        public boolean soulSpeed = false; // stivali soul speed (solo Paper per ora)
        public int speedAmp = -1; // pozione Speed: amplifier, -1 = nessuna (default sicuro)
        public int jumpAmp = -1; // Jump Boost: amplifier, -1 = nessuno
        public boolean ceilingAbove = false; // blocco solido sopra la testa (salti troncati)
        public boolean onIce = false; // ghiaccio sotto (scivola: 8-10 b/s legit)
        public double moveSpeedAttr = 0.1; // attribute movement_speed (base vanilla 0.1)
        public boolean softLanding = false; // atterraggio morbido (slime/honey/fieno/letti)
        public boolean slowFall = false; // slow falling / levitation (hover vanilla)
        public boolean onGround;
        public boolean flying;      // allowFlight / gamemode creativa
        public boolean inWater;
        public boolean onLadder;
        public boolean gliding;     // elytra
        public boolean riding;      // barca/cavallo/ecc
        public int ping;
        public long dtMillis;
    }

    public static class FightContext {
        public double distance; // piedi-a-piedi
        public double eyeDistance; // occhio-attaccante -> piedi-bersaglio (0 se non disponibile)
        public long dtSinceLastAttackMillis;
        public double attackerYaw;
        public double attackerPitch;
        public double targetYawDiff; // differenza angolo (per KillAura)
        public double yawSnapDiff = -1; // snap yaw tra attacchi consecutivi (-1 = primo colpo)
        public boolean blocking = false; // scudo alzato mentre colpisce
        public double ax; // posizione attaccante al colpo (serve all'AimLock)
        public double ay;
        public double az;
        public float attackCooldown = -1; // 0-1 vanilla (1 = carico pieno), -1 = non disponibile
        public boolean throughWall = false; // raytrace occhio->target bloccato (solo Paper+PL)
        public int targetId = -1; // entityId bersaglio (-1 = ignoto)
        public boolean targetIsPlayer = false; // true = PvP (ritmici valgono solo qui)
        public int ping;
    }

    public int checkMove(PlayerData data, MoveContext ctx) { return 0; }
    public int checkFight(PlayerData data, FightContext ctx) { return 0; }

    /** Check sperimentale: gira solo con experimental-checks: true. */
    public boolean experimental() { return false; }

    /** Differenza angolare minima tra due yaw. Gradi 0-180. */
    public static double yawDiff(double a, double b) {
        double d = Math.abs(a - b) % 360;
        if (d > 180) d = 360 - d;
        return d;
    }
}
