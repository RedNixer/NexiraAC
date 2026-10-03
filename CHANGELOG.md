# Changelog

## 0.2.5

Giornata passata a inseguire falsi positivi col debug acceso. Il grosso:

- Sprint-jump sotto i soffitti bassi flaggava Prediction: è una meccanica
  vanilla (quasi 2x la velocità normale), ora il simulatore la conosce.
- Lo sprint alza `movement_speed` del 30% da solo, e i tetti lo contavano
  due volte. Normalizzato.
- Ghiaccio: scivolare a 8-10 b/s è normale, il simulatore ora lo sa.
- NoFall: slime/honey/fieno/letti/ragnatele non flaggano più, né scendere
  dalle scale o dagli slab. Aggiunto il branch nel debug (`nfBr=`) così il
  prossimo falso positivo si legge da solo.
- Combat: il fight via pacchetti poteva restare cieco (eventi skippati +
  hook muto). Ora c'è il fallback automatico agli eventi e l'hook urla in
  console se il mapping ProtocolLib non torna.
- KillAura vs mob fermi: i check di ritmo valgono solo in PvP, come fanno
  gli anticheat grossi. Tolto anche il doppio conteggio dei click
  (ogni colpo contava 2, sballava CPS e regolarità).
- Reach: i muri contano solo oltre i 3 metri, niente più raytrace borderline
  su recinti e slab. Step con streak anti-lag.
- Tab-complete: soglia 5/sec kickava chi teneva premuto TAB. Ora 20.
- XRay statistico rimosso del tutto (23 check rimasti). Troppi falsi
  positivi per quello che dava, e lo rifaremo meglio più avanti.
- Prime fondamenta del TickEngine: vocabolario blocchi condiviso
  (BlockKind/WorldView), cache blocchi, collision solver AABB in parallelo
  (logga `[AC-SOL]`, non dà VL). Il resto arriva nelle prossime versioni.

## 0.2.0

Beta iniziale: 24 check, GUI admin, ProtocolLib opzionale, mod Fabric
server + client companion, punishments.json, protection guards.
