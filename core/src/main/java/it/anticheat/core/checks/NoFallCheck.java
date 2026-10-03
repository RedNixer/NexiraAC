package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;
import it.anticheat.core.physics.LatencyComp;

/**
 * NoFall v2: becca i due trucchi reali.
 * A) onGround-spoof: il client dice "sono a terra" mentre precipita (dy molto
 *    negativa per tanti movimenti di fila = impossibile legittimo).
 * B) danno cancellato: atterra da >3.5 blocchi ma nessun danno da caduta
 *    arriva entro 1.5s (con doppio controllo d'ordine per non flaggare i legit).
 */
public class NoFallCheck extends Check {
    @Override public String name() { return "NoFall"; }
    @Override public CheckType type() { return CheckType.MOVEMENT; }
    @Override public String description() { return "Nessun danno da caduta sospetto"; }

    @Override
    public int checkMove(PlayerData data, MoveContext ctx) {
        if (ctx.flying || ctx.gliding || ctx.riding) {
            data.wasOnGround = ctx.onGround;
            return 0;
        }
        // ground claimed while falling fast; second arm catches freefall samples
        if (ctx.onGround && !ctx.inWater && !ctx.onLadder) {
            boolean classic = !ctx.groundBelow && ctx.dy < -1.8;
            boolean freefall = ctx.dy < -3.0;
            if (classic || freefall) {
                data.groundSpoofStreak++;
                if (data.groundSpoofStreak >= LatencyComp.needStreak(ctx.ping, 6)) {
                    data.groundSpoofStreak = 0;
                    data.wasOnGround = ctx.onGround;
                    data.lastNoFallBranch = "A";
                    return 5;
                }
            } else {
                data.groundSpoofStreak = Math.max(0, data.groundSpoofStreak - 1);
            }
        } else {
            data.groundSpoofStreak = Math.max(0, data.groundSpoofStreak - 1);
        }

        // inizio possibile caduta (lascia terra, non in acqua/scale)
        if (!ctx.onGround && data.wasOnGround && !ctx.inWater && !ctx.onLadder) {
            data.fallStartY = ctx.y;
        }
        // traccia il picco in aria (Fase A2): il cheat NoFall azzera la
        // fallDistance del server mandando onGround=true prima dell'impatto,
        // ma la Y reale scende comunque. Il picco non mente mai.
        if (!ctx.onGround && !ctx.inWater && !ctx.onLadder && !ctx.flying && !ctx.gliding) {
            if (ctx.y > data.fallStartY) data.fallStartY = ctx.y;
        }

        // atterraggio: se caduto da >3.5 blocchi, aspetta il danno.
        // Usa il picco Y reale, non la fallDistance server (spoofabile).
        // Atterraggio morbido: il mondo assorbe (stile Grim), mai pending.
        if (ctx.onGround && !data.wasOnGround && !ctx.inWater && !ctx.onLadder) {
            if (ctx.softLanding) {
                data.pendingFallDist = 0;
                data.fallStartY = 0;
            } else {
                double fell = data.fallStartY - ctx.y;
                if (fell > 3.5) {
                    data.pendingFallDist = fell;
                    data.pendingFallTime = System.currentTimeMillis();
                }
                data.fallStartY = 0;
            }
        }

        // B) atterrato da oltre 1.5s senza alcun danno = danno cancellato.
        // Neve/ragnatela recente (2s): attutiscono vanilla, skip (stile Grim).
        if (data.pendingFallDist > 0
                && System.currentTimeMillis() - data.pendingFallTime > 1500
                && data.pendingFallTime > data.lastFallDamageTime
                && System.currentTimeMillis() - data.lastCobwebTime > 2000) {
            data.pendingFallDist = 0;
            data.wasOnGround = ctx.onGround;
            data.lastNoFallBranch = "B";
            return 5;
        }

        // C) supporto: dichiara terra ma sotto non c'e niente mentre scende.
        //    Soglia -1.0 (non -0.3): scendere da slab/blocco (step-down -0.5/-0.6)
        //    e legit. Lo spoof vero cade a -2+ con ground dichiarato.
        //    Il cheat lento lo becca comunque il branch-B (danno mancante).
        if (ctx.onGround && !ctx.groundBelow && ctx.dy < -1.0
                && !ctx.inWater && !ctx.onLadder && !ctx.flying && !ctx.gliding) {
            data.noGroundStreak++;
            if (data.noGroundStreak >= LatencyComp.needStreak(ctx.ping, 8)) {
                data.noGroundStreak = 0;
                data.wasOnGround = ctx.onGround;
                data.lastNoFallBranch = "C";
                return 5;
            }
        } else {
            data.noGroundStreak = Math.max(0, data.noGroundStreak - 1);
        }

        data.wasOnGround = ctx.onGround;
        return 0;
    }

    /** Chiamato dagli adapter su danno da caduta (anche ridotto da slime/fieno). */
    public int checkFallDamage(PlayerData data, double fallDistance, double damageTaken) {
        if (damageTaken > 0) {
            // danno arrivato (anche ridotto): caduta legittima, azzera il sospetto
            data.pendingFallDist = 0;
            data.lastFallDamageTime = System.currentTimeMillis();
            return 0;
        }
        // caduta importante senza alcun danno (assorbito dal cheat)
        data.lastFallDamageTime = 0; // non far scattare la guardia d'ordine
        if (fallDistance > 4.0) {
            data.lastNoFallBranch = "D";
            return 4;
        }
        return 0;
    }
}
