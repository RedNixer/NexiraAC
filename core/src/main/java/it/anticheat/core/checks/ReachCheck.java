package it.anticheat.core.checks;

import it.anticheat.core.Check;
import it.anticheat.core.CheckType;
import it.anticheat.core.PlayerData;
import it.anticheat.core.physics.LatencyComp;

/**
 * Reach v3 (Passo 1 A3): vanilla max 3.0 + mezzo margine ping.
 * Misura occhio->hitbox dai pacchetti (bridge) o minima piedi/occhio a eventi.
 * Finestra ultimi 8 colpi: 5+ sfori = cheat (stretto da 6/3: i cheat veri
 * sforano sempre, il lag oscilla sopra/sotto e non riempie mai 5/8).
 * Limiti: 3.05 base + meta margine ping (tetto 4.0, era 4.5).
 */
public class ReachCheck extends Check {
    @Override public String name() { return "Reach"; }
    @Override public CheckType type() { return CheckType.COMBAT; }
    @Override public String description() { return "Colpi da distanza impossibile"; }

    @Override
    public int checkFight(PlayerData data, FightContext ctx) {
        // A1: colpo attraverso un muro (raytrace bloccato): 2 di fila = cheat.
        // Il singolo e lag/angolo hitbox, mai flag immediato.
        if (ctx.throughWall) {
            data.wallHitStreak++;
            if (data.wallHitStreak >= 2) {
                data.wallHitStreak = 0;
                return 5;
            }
        } else {
            data.wallHitStreak = 0;
        }
        double d = ctx.eyeDistance > 0 ? Math.min(ctx.distance, ctx.eyeDistance) : ctx.distance;
        // vanilla 3.0 + 0.05 rumore + mezzo margine ping (tetto 4.0)
        double limit = Math.min(4.0, 3.05 + LatencyComp.margin(ctx.ping) / 2.0);
        double over = d - limit;
        // finestra ultimi 8 colpi: 5+ sfori = cheat (l'oscillazione da lag
        // 50/50 non arriva mai a 5, il cheat costante sì)
        data.reachWindow = ((data.reachWindow << 1) | (over > 0 ? 1 : 0)) & 0xFF;
        if (Integer.bitCount(data.reachWindow) < 5) return 0;
        data.reachWindow = 0;
        if (over > 1.0) return 6;
        if (over > 0.3) return 4; // fascia Meteor (Hitboxes +0.5, aura 3.5-4.5): sempre visibile
        return 2;
    }
}
