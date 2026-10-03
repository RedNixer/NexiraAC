# Punishments — how Nexira decides what happens

When a check flags a player, the anticheat adds violation levels (VL) and then
looks at `punishments.json` to decide what to do. Same file, same format on
Paper (`plugins/AntiCheat/punishments.json`) and Fabric
(`config/anticheat/punishments.json`). The file is created with sane defaults
the first time the server starts — edit it with any text editor, then
`/ac reload` (no restart needed).

## The file

```json
{
  "defaults": { "warnVl": 20, "kickVl": 50, "banVl": 100 },
  "checks": {
    "KillAura": [
      { "vl": 30, "action": "warn", "reason": "KillAura sospetta" },
      { "vl": 60, "action": "kick", "reason": "KillAura rilevata" },
      { "vl": 100, "action": "tempban", "duration": "7d", "reason": "KillAura" }
    ]
  }
}
```

How it reads: when KillAura VL reaches 30 the player gets warned, at 60 kicked,
at 100 banned for 7 days. Rules are matched top-down — the highest VL reached
wins. Any check you don't list falls back to the global warn/kick/ban
thresholds (the same ones you can change in `/ac settings`).

## Actions

- `notify` — staff sees the flag, player notices nothing. Use it for
  anything you want to review by hand first.
- `warn` — the player gets a chat warning. Cheap, no harm done.
- `kick` — kicked with your reason. Rejoin and the VL is still there, so
  repeating the cheat escalates on its own.
- `tempban` — banned until the `duration` expires, then the server unbans
  automatically. You don't need to run anything.
- `ban` — permanent ban.
- `freeze` — the player can't move until you `/ac unfreeze` them. Good for
  keeping a suspect still while you check the logs.

## Durations

Write them like `30m` (minutes), `12h` (hours), `7d` (days), `12mo` (months,
30 days each) or `perm` (forever). A plain number means minutes. If you write
something invalid the server log tells you and treats the ban as permanent —
check the console if a ban looks wrong.

## Manual commands (staff)

These bypass VL completely — your call, your responsibility:

- `/ac ban <player> <durata|perm> [motivo]` — e.g. `/ac ban Steve 7d killaura dai log`
- `/ac kick <player> [motivo]`
- `/ac unban <player>`
- `/ac freeze <player>` / `/ac unfreeze <player>`

Tab-complete suggests player names and durations. Everything works from the
server console too (without the slash).

## Tips from running it

- Start softer than you think. `warn` at low VL catches misconfigurations
  before they cost you players — a legit player with a laggy connection looks
  a lot like a bad cheater in the logs.
- `test-mode: true` in config.yml logs every punishment without applying it.
  Run a day in test-mode after changing thresholds, read the console, then
  enforce.
