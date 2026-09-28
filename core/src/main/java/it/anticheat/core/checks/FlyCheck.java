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
            return 0;
        }
        if (!ctx.onGround) {
            data.airTicks++;
            // salita verticale senza salto (salto max ~1.25 blocchi, tolleranza)
            if (ctx.dy > 1.4 && ctx.distXZ < 2.0) return 5;
            // aria troppo lunga senza scendere: la ragnatela e lo slime
            // fanno planare quasi fermi -> richiedi anche movimento orizzontale.
            // Soglia allungata col ping (pacchetti raggruppati), mai spenta:
            // un fly a 400ms prima era invisibile.
            if (data.airTicks > 120 + Math.min(120, ctx.ping / 2)) {
                if (ctx.dy > 0.1) return 4;
                if (ctx.dy >= -0.05 && ctx.distXZ > 0.4) return 4;
            }
            // hover stazionario: 15s in aria quasi fermo (non ragnatela/neve).
            // Cadere da altezze normali dura secondi e scende veloce: escluso da dy.
            if (data.airTicks > 300 && !ctx.inCobweb
                    && ctx.distXZ < 0.5 && ctx.dy > -0.1 && ctx.dy < 0.1) {
                data.airTicks = 0; // evita spam: riflagga tra altri 15s se persiste
                return 4;
            }
        } else {
            data.airTicks = 0;
        }
        return 0;
    }
}
