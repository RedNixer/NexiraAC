package it.anticheat.core.physics;

/**
 * Tipi di blocco che contano per la fisica. Mapping 1:1 nostro,
 * gli adapter traducono Bukkit/Mojang in questo enum.
 * Mai NMS nel core: solo questo vocabolario.
 */
public enum BlockKind {
    AIR,
    SOLID,       // cubo pieno (pietra, terra, assi...)
    STAIR,       // scale (supporto parziale, non isSolid su Bukkit)
    SLAB,        // lastre (supporto parziale)
    FENCE,       // recinti/muri: 1.5 di collisione
    CARPET,      // tappeti/neve leggera: supporto sottile
    SCAFFOLD,    // impalcature (scalabili, collisione speciale)
    LADDER,      // scale a pioli / viti (arrampicata)
    VINE,        // viti generiche arrampicabili
    ICE,         // ghiaccio/packed/blue/frosted (attrito 0.98)
    SLIME,       // rimbalzo
    HONEY,       // appiccicoso (no salto, scivolo pareti)
    SOUL_SAND,   // rallenta (senza soul speed)
    COBWEB,      // ragnatela (caduta lenta)
    POWDER_SNOW, // neve polverosa (come ragnatela)
    HAY,         // fieno (riduce danno caduta)
    BED,         // letti (assorbono caduta)
    WATER,       // acqua
    LAVA,        // lava
    BUBBLE,      // colonne di bolle (da implementare Fase 3)
    CACTUS,      // danno ma solido? no: non solido, segna per exempt
    OTHER;       // fallback sicuro = trattato come SOLID per le collisioni

    /** Attrito vanilla (slipperiness) per tipo. */
    public double slipperiness() {
        return switch (this) {
            case ICE -> 0.98;
            case SLIME -> 0.8;
            default -> 0.6;
        };
    }

    /** Ci si può stare sopra (supporto per onGround calcolato). */
    public boolean supports() {
        return switch (this) {
            case AIR, WATER, LAVA, BUBBLE, COBWEB, POWDER_SNOW, LADDER, VINE,
                 CACTUS, SCAFFOLD -> false;
            default -> true;
        };
    }

    /** Assorbe la caduta (niente danno = niente NoFall). */
    public boolean softLanding() {
        return switch (this) {
            case SLIME, HONEY, HAY, BED, COBWEB, POWDER_SNOW -> true;
            default -> false;
        };
    }
}
