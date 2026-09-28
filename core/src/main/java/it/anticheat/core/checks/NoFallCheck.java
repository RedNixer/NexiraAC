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
        // A) onGround-spoof: streak allungato col ping (posizioni raggruppate),
        // mai spento: prima sopra 300 era invisibile.
        if (ctx.onGround && !ctx.groundBelow && ctx.dy < -1.8 && !ctx.inWater && !ctx.onLadder) {
            data.groundSpoofStreak++;
            if (data.groundSpoofStreak >= LatencyComp.needStreak(ctx.ping, 6)) {
                data.groundSpoofStreak = 0;
                data.wasOnGround = ctx.onGround;
                return 5;
            }
        } else {
            data.groundSpoofStreak = Math.max(0, data.groundSpoofStreak - 1);
        }

        // inizio possibile caduta (lascia terra, non in acqua/scale)
        if (!ctx.onGround && data.wasOnGround && !ctx.inWater && !ctx.onLadder) {
            data.fallStartY = ctx.y;
        }

        // atterraggio: se caduto da >3.5 blocchi, aspetta il danno
        if (ctx.onGround && !data.wasOnGround && !ctx.inWater && !ctx.onLadder) {
            double fell = data.fallStartY - ctx.y;
            if (fell > 3.5) {
                data.pendingFallDist = fell;
                data.pendingFallTime = System.currentTimeMillis();
            }
            data.fallStartY = 0;
        }

        // B) atterrato da oltre 1.5s senza alcun danno = danno cancellato
        if (data.pendingFallDist > 0
                && System.currentTimeMillis() - data.pendingFallTime > 1500
                && data.pendingFallTime > data.lastFallDamageTime) {
            data.pendingFallDist = 0;
            data.wasOnGround = ctx.onGround;
            return 5;
        }

        // C) supporto: dichiara terra ma sotto non c'e niente mentre scende.
        //    Il controllo non dipende dalla velocita dei pacchetti: anche se il
        //    cheat spalma la caduta, in aria non c'e supporto per 8 movimenti.
        if (ctx.onGround && !ctx.groundBelow && ctx.dy < -0.3
                && !ctx.inWater && !ctx.onLadder && !ctx.flying && !ctx.gliding) {
            data.noGroundStreak++;
            if (data.noGroundStreak >= LatencyComp.needStreak(ctx.ping, 8)) {
                data.noGroundStreak = 0;
                data.wasOnGround = ctx.onGround;
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
        if (fallDistance > 4.0) return 4;
        return 0;
    }
}
