<p align="center">
  <img src="assets/logo.png" alt="Nexira" width="320">
</p>

<h1 align="center">NEXIRA</h1>

<p align="center">
  <b>Beta</b> — under active development. It won't catch everything yet, and
  thresholds may need tuning on your server. Built and tested on 1.21.11.
</p>

<p align="center">
  Server-side anticheat for Minecraft 1.21.11 — Paper/Purpur and Fabric, one shared core.
  <br>
  <img src="https://img.shields.io/badge/minecraft-1.21.11-green" alt="mc 1.21.11">
  <img src="https://img.shields.io/badge/paper%20%2B%20fabric-blue" alt="paper + fabric">
  <img src="https://img.shields.io/badge/java-21-orange" alt="java 21">
</p>

Nexira watches movement, combat and world interaction from the server side and scores
every suspicion with violation levels (VL). Warn, kick, ban — or just teleport the
cheater back to solid ground with setback. Everything is tunable live from an in-game
admin panel, no restarts. A web dashboard shows players, sessions and alt accounts.

There is also an optional client companion mod: players who install it get a verified
badge, and staff get an F8 shortcut to the panel. The server never trusts the client —
the mod is only a signal, all decisions stay server-side.

- [Checks](#checks-23)
- [Quick start](#quick-start)
- [Commands](#commands)
- [Web dashboard](DASHBOARD.md)
- [Staff mode](#staff-mode-paper)
- [Server protection](#server-protection-paper)
- [Config](#config)
- [Build from source](#build-from-source)
- [Roadmap](#roadmap)
- [Nexira vs Grim vs Vulcan](#nexira-vs-grim-vs-vulcan)

## Checks (23)

**Movement.** Speed (spikes, metronome, hop rhythm) · Fly (flight, airtime,
stationary hover, slime-bounce aware) · NoFall (ground spoof, cancelled damage,
missing support, soft landings never flag) · Step (streaked, lag-burst safe) ·
Sprint (hunger, blindness, bow/food in hand) · NoSlow (items, webs, soul sand) ·
Timer · Jesus · Spider\* · Prediction (vanilla simulation with real block
awareness: ice slip, low-ceiling sprint boost, sprint attribute, jump boost).

**Combat.** Reach (eye-to-hitbox, 3.05 + half ping margin, 8-hit window,
walls only past 3m) · KillAura (rate, yaw snap, 1.9+ cooldown, plus PvP-only
anti-smooth: hit rhythm, perfect-cooldown streaks, target switching) ·
AutoClicker (your CPS limit, regularity, 1 click = 1 swing) · Multitask\* ·
AimSnap (LOOK-raw only) · AimLock (frozen aim, bow snap) · RotationStream
(raw LOOK packets) · PacketOrder (attack order, ground-spoof, creative-aware) ·
Interact (mid-use hits, self-hits, multi-entity, range).

**World.** Scaffold (rate, gap, sneak, yaw snap, rotation/face, range) ·
FastBreak (damage duration, intervals, no-swing, mine timing, same-tick
multi-break, hotbar swap, vanilla DPS per tool) · AutoTotem (rate, swap timing,
post-pop refill).

\* experimental, off by default (`experimental-checks: true`).

No ping cutoffs: every check scales tolerance with latency instead of switching
off. Knockback is subtracted from movement instead of blanking checks.

With ProtocolLib (Paper only, optional): precise Flying rate, eye-to-hitbox
range, attack order, raw rotation stream, interact validation. Without it,
everything else works on events alone. On 1.21.11 you need a ProtocolLib
dev-build (5.5.0+).

## Quick start

1. Put `anticheat-paper-0.3.0.jar` in `plugins/` (Paper) **or**
   `fabric-mod-...+mc.1.21.11.jar` in `mods/` (Fabric server). Never both,
   never swapped.
2. Start once, stop. From console (no slash): `ac add YourName`.
3. Start again. `/ac gui` opens the panel. Web dashboard: see
   [DASHBOARD.md](DASHBOARD.md).
4. Client mod (optional): `client-mod-...+mc.1.21.11.jar` (without `-dev`)
   in the game `mods/`. F8 opens the staff panel.

## Commands

`/ac gui|settings|vl|reset|stats|debug|dashboard|reports|cps|testmode|experimental|download|vanish|inv|ban|kick|unban|freeze|unfreeze|reload|add`
— most work from console without the slash (`ac vl Steve`).
Players report with `/report <player> <reason>`.
Punishments (per-check rules, durations, manual bans): [PUNISHMENTS.md](PUNISHMENTS.md).

Permissions: `anticheat.admin` (default op), `anticheat.exempt` (bypass all
checks), `anticheat.report` (default true).

## Staff mode (Paper)

`/ac vanish` hides you from non-admins while you keep playing survival.
Right-click any player while vanished to open their inventory live.
`/ac inv <player>` opens the same view from anywhere.

## Server protection (Paper)

Login flood (per-IP rate, join/quit storm, auto IP-ban), packet exploits
(oversized NBT/payloads, click/tab floods, needs ProtocolLib), spam (chat
mute, command kick, oversized books). All in `protection.json`.
Volumetric DDoS needs a proxy (TCPShield or similar) — no plugin stops that.

## Config

One `config.yml`, same keys on Paper (`plugins/AntiCheat/`) and Fabric
(`config/anticheat/`): admins, thresholds, per-check toggles, CPS limit,
setback, test-mode, experimental/packet/dashboard toggles, client policy.
Also editable live from `/ac settings`.

## Build from source

```bat
.\gradlew.bat build
```

Jars land in each module's `build/libs/`. First run needs internet (Minecraft +
Fabric mappings, ~1 GB). Java 21 required.

## Roadmap

Done in 0.2.5–0.3.0: false-positive hunt (low ceilings, ice, sprint
attribute, soft landings, combat wiring), XRay removed, TickEngine
foundations (BlockKind/WorldView, AABB solver), web dashboard (players,
sessions, alts, token auth).

Next, in order: central punishment manager · SQLite storage behind `Storage` ·
`/ac alts` in game · punishments GUI · TickEngine simulation · NPC decoy +
combine checks · elytra/vehicle limits, phase on the solver · flag replay,
per-check debug, Geyser awareness.

Ideas, not promises. False-positive reports welcome — open an issue with
your `[AC-DBG]` lines and what you were doing.

## Nexira vs Grim vs Vulcan

| | Nexira | Grim | Vulcan |
|---|---|---|---|
| Price | Free, open source | Free, open source | Paid |
| Platforms | Paper/Purpur **and** Fabric, shared core | Bukkit and Fabric | Spigot/Paper forks |
| Checks | 23 | 130+ with full simulation | 100+, strong combat |
| Performance | Light, packets opt-in | Heavier, simulates every tick | Tuned for big networks |
| Setup | One config + live GUI + test-mode | Powerful but config-heavy | Near plug-and-play |
| Extras | Client mod, web dashboard | — | — |
| Best for | Small/medium + Fabric servers | Large PvP networks | Networks wanting premium |

Honest note: Grim covers more exotic cheats, Vulcan has years of big-network
tuning. Nexira trades coverage for simplicity. If you outgrow it, Grim is the
natural next step up.

## License

Copyright (c) 2026 RedNixer. Licensed under AGPL-3.0 (see LICENSE).
Techniques are inspired by Grim and Meteor; all code here is original.
