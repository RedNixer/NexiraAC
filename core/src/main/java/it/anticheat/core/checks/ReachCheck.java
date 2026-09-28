package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;

/**
 * Reach v2: usa la distanza minima tra piedi-a-piedi e occhio-a-piedi.
 * Perche: la misura piedi-a-piedi si gonfia quando il bersaglio e sbalzato
 * in aria dal knockback (anche +2/3 blocchi verticali legittimi).
 * In piu richiede 2 colpi di fila oltre il limite: il singolo sforo e lag.
 * Limiti: 3.0 di range + tolleranza ping (3.4 / 3.8 / 4.2).
 */
public class ReachCheck extends Check {
    @Override public String name() { return "Reach"; }
    @Override public CheckType type() { return CheckType.COMBAT; }
    @Override public String description() { return "Colpi da distanza impossibile"; }

    @Override
    public int checkFight(PlayerData data, FightContext ctx) {
        double d = ctx.eyeDistance > 0 ? Math.min(ctx.distance, ctx.eyeDistance) : ctx.distance;
        // vanilla max ~3.0: limiti stretti + bande (tarati su Hitboxes +0.5 di Meteor)
        double limit = ctx.ping > 250 ? 4.0 : (ctx.ping > 120 ? 3.6 : 3.2);
        double over = d - limit;
        // finestra ultimi 6 colpi: 3+ sfori = cheat. Cosi un bersaglio che si
        // muove (distanza che oscilla sopra/sotto) non azzera piu tutto,
        // mentre il singolo spike di lag resta innocuo.
        data.reachWindow = ((data.reachWindow << 1) | (over > 0 ? 1 : 0)) & 0x3F;
        if (Integer.bitCount(data.reachWindow) < 3) return 0;
        data.reachWindow = 0;
        if (over > 1.0) return 6;
        if (over > 0.3) return 4; // fascia Meteor (Hitboxes +0.5, aura 3.5-4.5): sempre visibile
        return 2;
    }
}
