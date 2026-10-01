package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;

/** Fly: sale senza supporto / resta in aria troppo a lungo senza elytra/veicolo. */
public class FlyCheck extends Check {
    @Override public String name() { return "Fly"; }
    @Override public CheckType type() { return CheckType.MOVEMENT; }
    @Override public String description() { return "Volo o permanenza in aria sospetta"; }

    @Override
    public int checkMove(PlayerData data, MoveContext ctx) {
        if (ctx.flying || ctx.gliding || ctx.riding || ctx.inWater || ctx.onLadder) {
            data.airTicks = 0;
            data.airMs = 0;
            data.flyUpStreak = 0;
            return 0;
        }
        if (!ctx.onGround) {
            data.airTicks++;
            data.airMs += Math.max(1, ctx.dtMillis);
            // Salita verticale senza salto. Soglia scalata sul dt reale:
            // Paper vede ogni pacchetto (dt 50ms, salto max ~1.25), Fabric
            // campiona ogni ~90ms quindi lo stesso volo spalma dy su piu tick.
            // Volo creativo = +0.5/tick sostenuto; salto = un picco e basta.
            double ticks = Math.max(1.0, ctx.dtMillis / 50.0);
            double upLimit = 1.4 * ticks;
            if (ctx.dy > upLimit && ctx.distXZ < 2.0 * ticks) {
                data.flyUpStreak++;
                // salto legit = 1 picco; volo = salita sostenuta 3 campioni
                if (data.flyUpStreak >= 3) {
                    data.flyUpStreak = 0;
                    return 5;
                }
            } else {
                data.flyUpStreak = 0;
            }
            // aria troppo lunga senza scendere: la ragnatela e lo slime
            // fanno planare quasi fermi -> richiedi anche movimento orizzontale.
            // Soglia in ms reali (non campioni): 6s di volo + bonus ping.
            // Prima: 120 campioni = 6s su Paper ma 11s su Fabric (90ms/campione).
            if (data.airMs > 6000 + Math.min(6000, ctx.ping * 5L)) {
                if (ctx.dy > 0.1) return 4;
                if (ctx.dy >= -0.05 && ctx.distXZ > 0.4) return 4;
            }
            // hover stazionario: 15s in aria quasi fermo (non ragnatela/neve).
            // Cadere da altezze normali dura secondi e scende veloce: escluso da dy.
            if (data.airMs > 15000 && !ctx.inCobweb
                    && ctx.distXZ < 0.5 && ctx.dy > -0.1 && ctx.dy < 0.1) {
                data.airMs = 0; // evita spam: riflagga tra altri 15s se persiste
                return 4;
            }
        } else {
            data.airTicks = 0;
            data.airMs = 0;
            data.flyUpStreak = 0;
        }
        return 0;
    }
}
